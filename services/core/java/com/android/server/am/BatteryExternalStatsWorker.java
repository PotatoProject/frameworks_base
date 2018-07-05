/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.android.server.am;

import android.annotation.Nullable;
import android.bluetooth.BluetoothActivityEnergyInfo;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.wifi.IWifiManager;
import android.net.wifi.WifiActivityEnergyInfo;
import android.os.BatteryStats;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SynchronousResultReceiver;
import android.os.SystemClock;
import android.telephony.ModemActivityInfo;
import android.telephony.TelephonyManager;
import android.util.IntArray;
import android.util.Slog;
import android.util.TimeUtils;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.os.BatteryStatsImpl;

import libcore.util.EmptyArray;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;

/**
 * A Worker that fetches data from external sources (WiFi controller, bluetooth chipset) on a
 * dedicated thread and updates BatteryStatsImpl with that information.
 *
 * As much work as possible is done without holding the BatteryStatsImpl lock, and only the
 * readily available data is pushed into BatteryStatsImpl with the lock held.
 */
class BatteryExternalStatsWorker implements BatteryStatsImpl.ExternalStatsSync {
    private static final String TAG = "BatteryExternalStatsWorker";
    private static final boolean DEBUG = false;

    /**
     * How long to wait on an individual subsystem to return its stats.
     */
    private static final long EXTERNAL_STATS_SYNC_TIMEOUT_MILLIS = 2000;

    // There is some accuracy error in wifi reports so allow some slop in the results.
    private static final long MAX_WIFI_STATS_SAMPLE_ERROR_MILLIS = 750;

    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor(
            (ThreadFactory) r -> {
                Thread t = new Thread(r, "batterystats-worker");
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            });

    private final Context mContext;
    private final BatteryStatsImpl mStats;

    @GuardedBy("this")
    private int mUpdateFlags = 0;

    @GuardedBy("this")
    private Future<?> mCurrentFuture = null;

    @GuardedBy("this")
    private String mCurrentReason = null;

    @GuardedBy("this")
    private final IntArray mUidsToRemove = new IntArray();

    private final Object mWorkerLock = new Object();

    @GuardedBy("mWorkerLock")
    private IWifiManager mWifiManager = null;

    @GuardedBy("mWorkerLock")
    private TelephonyManager mTelephony = null;

    // WiFi keeps an accumulated total of stats, unlike Bluetooth.
    // Keep the last WiFi stats so we can compute a delta.
    @GuardedBy("mWorkerLock")
    private WifiActivityEnergyInfo mLastInfo =
            new WifiActivityEnergyInfo(0, 0, 0, new long[]{0}, 0, 0, 0);

    BatteryExternalStatsWorker(Context context, BatteryStatsImpl stats) {
        mContext = context;
        mStats = stats;
    }

    @Override
    public synchronized Future<?> scheduleSync(String reason, int flags) {
        return scheduleSyncLocked(reason, flags);
    }

    @Override
    public synchronized Future<?> scheduleCpuSyncDueToRemovedUid(int uid) {
        mUidsToRemove.add(uid);
        return scheduleSyncLocked("remove-uid", UPDATE_CPU);
    }

    public synchronized Future<?> scheduleWrite() {
        if (mExecutorService.isShutdown()) {
            return CompletableFuture.failedFuture(new IllegalStateException("worker shutdown"));
        }

        scheduleSyncLocked("write", UPDATE_ALL);
        // Since we use a single threaded executor, we can assume the next scheduled task's
        // Future finishes after the sync.
        return mExecutorService.submit(mWriteTask);
    }

    /**
     * Schedules a task to run on the BatteryExternalStatsWorker thread. If scheduling more work
     * within the task, never wait on the resulting Future. This will result in a deadlock.
     */
    public synchronized void scheduleRunnable(Runnable runnable) {
        if (!mExecutorService.isShutdown()) {
            mExecutorService.submit(runnable);
        }
    }

    public void shutdown() {
        mExecutorService.shutdownNow();
    }

    private Future<?> scheduleSyncLocked(String reason, int flags) {
        if (mExecutorService.isShutdown()) {
            return CompletableFuture.failedFuture(new IllegalStateException("worker shutdown"));
        }

        if (mCurrentFuture == null) {
            mUpdateFlags = flags;
            mCurrentReason = reason;
            mCurrentFuture = mExecutorService.submit(mSyncTask);
        }
        mUpdateFlags |= flags;
        return mCurrentFuture;
    }

    private final Runnable mSyncTask = new Runnable() {
        @Override
        public void run() {
            // Capture a snapshot of the state we are meant to process.
            final int updateFlags;
            final String reason;
            final int[] uidsToRemove;
            synchronized (BatteryExternalStatsWorker.this) {
                updateFlags = mUpdateFlags;
                reason = mCurrentReason;
                uidsToRemove = mUidsToRemove.size() > 0 ? mUidsToRemove.toArray() : EmptyArray.INT;
                mUpdateFlags = 0;
                mCurrentReason = null;
                mUidsToRemove.clear();
                mCurrentFuture = null;
            }

            synchronized (mWorkerLock) {
                if (DEBUG) {
                    Slog.d(TAG, "begin updateExternalStatsSync reason=" + reason);
                }
                try {
                    updateExternalStatsLocked(reason, updateFlags);
                } finally {
                    if (DEBUG) {
                        Slog.d(TAG, "end updateExternalStatsSync");
                    }
                }
            }

            // Clean up any UIDs if necessary.
            synchronized (mStats) {
                for (int uid : uidsToRemove) {
                    mStats.removeIsolatedUidLocked(uid);
                }
            }
        }
    };

    private final Runnable mWriteTask = new Runnable() {
        @Override
        public void run() {
            synchronized (mStats) {
                mStats.writeAsyncLocked();
            }
        }
    };

    private void updateExternalStatsLocked(final String reason, int updateFlags) {
        // We will request data from external processes asynchronously, and wait on a timeout.
        SynchronousResultReceiver wifiReceiver = null;
        SynchronousResultReceiver bluetoothReceiver = null;
        SynchronousResultReceiver modemReceiver = null;

        if ((updateFlags & BatteryStatsImpl.ExternalStatsSync.UPDATE_WIFI) != 0) {
            // We were asked to fetch WiFi data.
            if (mWifiManager == null) {
                mWifiManager = IWifiManager.Stub.asInterface(ServiceManager.getService(
                        Context.WIFI_SERVICE));
            }

            if (mWifiManager != null) {
                try {
                    wifiReceiver = new SynchronousResultReceiver("wifi");
                    mWifiManager.requestActivityInfo(wifiReceiver);
                } catch (RemoteException e) {
                    // Oh well.
                }
            }
        }

        if ((updateFlags & BatteryStatsImpl.ExternalStatsSync.UPDATE_BT) != 0) {
            // We were asked to fetch Bluetooth data.
            final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) {
                bluetoothReceiver = new SynchronousResultReceiver("bluetooth");
                adapter.requestControllerActivityEnergyInfo(bluetoothReceiver);
            }
        }

        if ((updateFlags & BatteryStatsImpl.ExternalStatsSync.UPDATE_RADIO) != 0) {
            // We were asked to fetch Telephony data.
            if (mTelephony == null) {
                mTelephony = TelephonyManager.from(mContext);
            }

            if (mTelephony != null) {
                modemReceiver = new SynchronousResultReceiver("telephony");
                mTelephony.requestModemActivityInfo(modemReceiver);
            }
        }

        final WifiActivityEnergyInfo wifiInfo = awaitControllerInfo(wifiReceiver);
        final BluetoothActivityEnergyInfo bluetoothInfo = awaitControllerInfo(bluetoothReceiver);
        final ModemActivityInfo modemInfo = awaitControllerInfo(modemReceiver);

        synchronized (mStats) {
            mStats.addHistoryEventLocked(
                    SystemClock.elapsedRealtime(),
                    SystemClock.uptimeMillis(),
                    BatteryStats.HistoryItem.EVENT_COLLECT_EXTERNAL_STATS,
                    reason, 0);

            if ((updateFlags & UPDATE_CPU) != 0) {
                mStats.updateCpuTimeLocked(true /* updateCpuFreqData */);
                mStats.updateKernelWakelocksLocked();
                mStats.updateKernelMemoryBandwidthLocked();
            }

            if ((updateFlags & UPDATE_RPM) != 0) {
                mStats.updateRpmStatsLocked();
            }

            if (bluetoothInfo != null) {
                if (bluetoothInfo.isValid()) {
                    mStats.updateBluetoothStateLocked(bluetoothInfo);
                } else {
                    Slog.e(TAG, "bluetooth info is invalid: " + bluetoothInfo);
                }
            }
        }

        // WiFi and Modem state are updated without the mStats lock held, because they
        // do some network stats retrieval before internally grabbing the mStats lock.

        if (wifiInfo != null && wifiInfo.isValid()) {
            mStats.updateWifiState(extractDeltaLocked(wifiInfo));
        } else {
            // wifiInfo can be null if link layer statistics feature(optional) is not supported.
            // In this case, updateWifiState is called to update the tx and rx packets statistics.
            mStats.updateWifiState(null);
        }

        if (modemInfo != null) {
            if (modemInfo.isValid()) {
                mStats.updateMobileRadioState(modemInfo);
            } else {
                Slog.e(TAG, "modem info is invalid: " + modemInfo);
            }
        }
    }

    /**
     * Helper method to extract the Parcelable controller info from a
     * SynchronousResultReceiver.
     */
    private static <T extends Parcelable> T awaitControllerInfo(
            @Nullable SynchronousResultReceiver receiver) {
        if (receiver == null) {
            return null;
        }

        try {
            final SynchronousResultReceiver.Result result =
                    receiver.awaitResult(EXTERNAL_STATS_SYNC_TIMEOUT_MILLIS);
            if (result.bundle != null) {
                // This is the final destination for the Bundle.
                result.bundle.setDefusable(true);

                final T data = result.bundle.getParcelable(
                        BatteryStats.RESULT_RECEIVER_CONTROLLER_KEY);
                if (data != null) {
                    return data;
                }
            }
            Slog.e(TAG, "no controller energy info supplied for " + receiver.getName());
        } catch (TimeoutException e) {
            Slog.w(TAG, "timeout reading " + receiver.getName() + " stats");
        }
        return null;
    }

    private WifiActivityEnergyInfo extractDeltaLocked(WifiActivityEnergyInfo latest) {
        final long timePeriodMs = latest.mTimestamp - mLastInfo.mTimestamp;
        final long lastIdleMs = mLastInfo.mControllerIdleTimeMs;
        final long lastTxMs = mLastInfo.mControllerTxTimeMs;
        final long lastRxMs = mLastInfo.mControllerRxTimeMs;
        final long lastEnergy = mLastInfo.mControllerEnergyUsed;

        // We will modify the last info object to be the delta, and store the new
        // WifiActivityEnergyInfo object as our last one.
        final WifiActivityEnergyInfo delta = mLastInfo;
        delta.mTimestamp = latest.getTimeStamp();
        delta.mStackState = latest.getStackState();

        final long txTimeMs = latest.mControllerTxTimeMs - lastTxMs;
        final long rxTimeMs = latest.mControllerRxTimeMs - lastRxMs;
        final long idleTimeMs = latest.mControllerIdleTimeMs - lastIdleMs;

        if (txTimeMs < 0 || rxTimeMs < 0) {
            // The stats were reset by the WiFi system (which is why our delta is negative).
            // Returns the unaltered stats.
            delta.mControllerEnergyUsed = latest.mControllerEnergyUsed;
            delta.mControllerRxTimeMs = latest.mControllerRxTimeMs;
            delta.mControllerTxTimeMs = latest.mControllerTxTimeMs;
            delta.mControllerIdleTimeMs = latest.mControllerIdleTimeMs;
            Slog.v(TAG, "WiFi energy data was reset, new WiFi energy data is " + delta);
        } else {
            final long totalActiveTimeMs = txTimeMs + rxTimeMs;
            long maxExpectedIdleTimeMs;
            if (totalActiveTimeMs > timePeriodMs) {
                // Cap the max idle time at zero since the active time consumed the whole time
                maxExpectedIdleTimeMs = 0;
                if (totalActiveTimeMs > timePeriodMs + MAX_WIFI_STATS_SAMPLE_ERROR_MILLIS) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Total Active time ");
                    TimeUtils.formatDuration(totalActiveTimeMs, sb);
                    sb.append(" is longer than sample period ");
                    TimeUtils.formatDuration(timePeriodMs, sb);
                    sb.append(".\n");
                    sb.append("Previous WiFi snapshot: ").append("idle=");
                    TimeUtils.formatDuration(lastIdleMs, sb);
                    sb.append(" rx=");
                    TimeUtils.formatDuration(lastRxMs, sb);
                    sb.append(" tx=");
                    TimeUtils.formatDuration(lastTxMs, sb);
                    sb.append(" e=").append(lastEnergy);
                    sb.append("\n");
                    sb.append("Current WiFi snapshot: ").append("idle=");
                    TimeUtils.formatDuration(latest.mControllerIdleTimeMs, sb);
                    sb.append(" rx=");
                    TimeUtils.formatDuration(latest.mControllerRxTimeMs, sb);
                    sb.append(" tx=");
                    TimeUtils.formatDuration(latest.mControllerTxTimeMs, sb);
                    sb.append(" e=").append(latest.mControllerEnergyUsed);
                    Slog.wtf(TAG, sb.toString());
                }
            } else {
                maxExpectedIdleTimeMs = timePeriodMs - totalActiveTimeMs;
            }
            // These times seem to be the most reliable.
            delta.mControllerTxTimeMs = txTimeMs;
            delta.mControllerRxTimeMs = rxTimeMs;
            // WiFi calculates the idle time as a difference from the on time and the various
            // Rx + Tx times. There seems to be some missing time there because this sometimes
            // becomes negative. Just cap it at 0 and ensure that it is less than the expected idle
            // time from the difference in timestamps.
            // b/21613534
            delta.mControllerIdleTimeMs = Math.min(maxExpectedIdleTimeMs, Math.max(0, idleTimeMs));
            delta.mControllerEnergyUsed = Math.max(0, latest.mControllerEnergyUsed - lastEnergy);
        }

        mLastInfo = latest;
        return delta;
    }
}
