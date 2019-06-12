package com.android.systemui.statusbar.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.INetworkStatsService;
import android.net.INetworkStatsService.Stub;
import android.net.NetworkInfo;
import android.net.NetworkStats;
import android.net.NetworkStats.Entry;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.INetworkManagementService;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Log;
import android.util.Slog;
import com.android.internal.os.BackgroundThread;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.INetworkSpeedStateCallBack;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Timer;

public class NetworkSpeedControllerImpl extends BroadcastReceiver implements NetworkSpeedController, Tunable {
    
    private static boolean DEBUG = false;
    private static long ERTRY_POINT = 1024;
    private static int HANDRED = 100;
    
    private static String TAG = "NetworkSpeedController";
    private static int TEN = 10;
    private static int THOUSAND = 1000;
    private static String UNIT_GB = "GB";
    private static String UNIT_KB = "KB";
    private static String UNIT_MB = "MB";
    private static int UPDATE_INTERVAL = 3;

    private int MSG_MAYBE_STOP_NETWORTSPEED = 1001;
    private int MSG_UPDATE_NETWORTSPEED = 1000;
    private int MSG_UPDATE_SHOW = 1002;
    private int MSG_UPDATE_SPEED_ON_BG = 2001;
    
    private MyBackgroundHandler mBackgroundHandler = new MyBackgroundHandler(BackgroundThread.getHandler().getLooper());
    private boolean mBlockNetworkSpeed = true;
    private ConnectivityManager mConnectivityManager = null;
    private Context mContext;
    private MyHandler mHandler = new MyHandler(Looper.getMainLooper());
    private boolean mHotSpotEnable = false;
    private StatusBarIconController mIconController;
    private boolean mIsFirstLoad = true;
    private final ArrayList<INetworkSpeedStateCallBack> mNetworkSpeedStateCallBack = new ArrayList<>();
    private boolean mNetworkTraceState = false;
    private boolean mShow = true;
    
    private String mSpeed;
    
    private MySpeedMachine mSpeedMachine = new MySpeedMachine();
    private INetworkStatsService mStatsService;
    private Timer mTimer;

    private class MyBackgroundHandler extends Handler {
        public MyBackgroundHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            if (msg.what == MSG_UPDATE_SPEED_ON_BG) {
                mBackgroundHandler.removeMessages(MSG_UPDATE_SPEED_ON_BG);
                if (mSpeedMachine.isTurnOn()) {
                    mSpeedMachine.updateSpeedonBG();
                    scheduleNextUpdate();
                }
            }
        }
    }

    private class MyHandler extends Handler {
        public MyHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            int action = msg.what;
            if (action == MSG_UPDATE_NETWORTSPEED) {
                if (msg.obj instanceof String) {
                    mSpeed = (String) msg.obj;
                    refreshSpeed();
                }
            } else if (action == MSG_MAYBE_STOP_NETWORTSPEED) {
                updateState();
            } else if (action == MSG_UPDATE_SHOW) {
                onShowStateChange();
            }
        }
    }

    private class MySpeedMachine {
        long incrementRxBytes = 0;
        long incrementTxBytes = 0;
        boolean isTurnOn = false;
        boolean mIsFirstLoadTether = true;
        long oldRxBytes = 0;
        long oldTetherRxBytes = 0;
        long oldTetherTxBytes = 0;
        long oldTxBytes = 0;

        public MySpeedMachine() {
            reset();
        }

        
        private void updateSpeedonBG() {
            long totalRxBytes;
            long j;
            long incrementTetherTxBytes;
            if (isNetworkSpeedTracing()) {
                long totalTxBytes = TrafficStats.getTotalTxBytes();
                long totalRxBytes2 = TrafficStats.getTotalRxBytes();
                incrementTxBytes = totalTxBytes - oldTxBytes;
                incrementRxBytes = totalRxBytes2 - oldRxBytes;
                oldTxBytes = totalTxBytes;
                oldRxBytes = totalRxBytes2;
                long incrementTetherRxBytes = 0;
                if (!mHotSpotEnable) {
                    oldTetherTxBytes = 0;
                    oldTetherRxBytes = 0;
                    mIsFirstLoadTether = true;
                    totalRxBytes = totalRxBytes2;
                } else {
                    long[] bytes = getTetherStats();
                    if (bytes == null || bytes.length != 2) {
                        incrementTetherTxBytes = 0;
                    } else {
                        long tetherRxBytes = bytes[0];
                        long tetherTxBytes = bytes[1];
                        incrementTetherTxBytes = tetherTxBytes - oldTetherTxBytes;
                        incrementTetherRxBytes = tetherRxBytes - oldTetherRxBytes;
                        oldTetherTxBytes = tetherTxBytes;
                        oldTetherRxBytes = tetherRxBytes;
                    }
                    if (DEBUG) Log.d(TAG, "NetWorkSpeed TetherTx: " + formateSpeed(incrementTetherTxBytes / ((long) UPDATE_INTERVAL)) +
                            " tTetherRx: " + formateSpeed(incrementTetherRxBytes / ((long) UPDATE_INTERVAL)) +
                            " systemTx: " + formateSpeed(incrementTxBytes / ((long) UPDATE_INTERVAL)) +
                            " systemRx: " + formateSpeed(incrementRxBytes / ((long) UPDATE_INTERVAL)));

                    totalRxBytes = totalRxBytes2;

                    if (mIsFirstLoadTether) {
                        mIsFirstLoadTether = false;
                    } else {
                        incrementTxBytes = incrementTxBytes + incrementTetherTxBytes + incrementTetherRxBytes;
                    }
                }
                if (mIsFirstLoad) {
                    if (DEBUG) Log.d(TAG, "NetWorkSpeed is first load.");
                    j = 0;
                    incrementTxBytes = 0;
                    incrementRxBytes = 0;
                    mIsFirstLoad = false;
                } else {
                    j = 0;
                }
                if (incrementTxBytes < j) {
                    incrementTxBytes = j;
                }
                if (incrementRxBytes < j) {
                    incrementRxBytes = j;
                }
                long incrementBytes = incrementRxBytes > incrementTxBytes ? incrementRxBytes : incrementTxBytes;
                long incrementPs = incrementBytes / ((long) UPDATE_INTERVAL);
                String speedstr = formateSpeed(incrementPs);
                if (DEBUG) Log.d(TAG, "NetWorkSpeed refresh totalTxBytes=" + totalTxBytes +
                        ", totalRxBytes=" + totalRxBytes +
                        ", incrementPs=" + incrementPs +
                        ", mSpeed=" + speedstr +
                        ", incrementBytes:" + incrementBytes);
                
                Message message = mHandler.obtainMessage();
                message.what = MSG_UPDATE_NETWORTSPEED;
                message.obj = speedstr;
                mHandler.sendMessage(message);
                return;
            }
            Message message2 = mHandler.obtainMessage();
            message2.what = MSG_MAYBE_STOP_NETWORTSPEED;
            mHandler.sendMessage(message2);
            Log.d(TAG, "send MSG_CLOSE_NETWORTSPEED");
        }

        public void reset() {
            oldTxBytes = 0;
            incrementTxBytes = 0;
            oldRxBytes = 0;
            incrementRxBytes = 0;
        }

        public void setTurnOn() {
            isTurnOn = true;
        }

        public void setTurnOff() {
            isTurnOn = false;
        }

        public boolean isTurnOn() {
            return isTurnOn;
        }
    }

    public NetworkSpeedControllerImpl(Context context) {
        mContext = context;
        mTimer = new Timer();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.TIME_SET");
        filter.addAction("android.intent.action.TIMEZONE_CHANGED");
        filter.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");
        mContext.registerReceiver(this, filter);
        mStatsService = Stub.asInterface(ServiceManager.getService("netstats"));
        mConnectivityManager = (ConnectivityManager) mContext.getSystemService("connectivity");
        mIconController = (StatusBarIconController) Dependency.get(StatusBarIconController.class);
        mIconController.setOPCustView("networkspeed", R.layout.status_bar_network_speed, mShow);
        mIconController.setIconVisibility("networkspeed", mShow);
        ((TunerService) Dependency.get(TunerService.class)).addTunable(this, "icon_blacklist");
    }

    public void updateConnectivity(BitSet connectedTransports, BitSet validatedTransports) {
        if (DEBUG) {
            String str = TAG;
            StringBuilder sb = new StringBuilder();
            sb.append("updateConnectivity connectedTransports:");
            sb.append(connectedTransports);
            sb.append(" validatedTransports:");
            sb.append(validatedTransports);
            Log.d(str, sb.toString());
        }
        updateState();
    }

    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == "android.intent.action.TIME_SET") {
            updateState();
        } else if (action == "android.intent.action.TIMEZONE_CHANGED") {
            updateState();
        } else if (action == "android.net.wifi.WIFI_AP_STATE_CHANGED") {
            mHotSpotEnable = intent.getIntExtra("wifi_state", 14) == 13;
            if (DEBUG) Log.i(TAG, "HotSpot enable: " + mHotSpotEnable);
            updateState();
        }
    }

    private String divToFractionDigits(long unmber, long divisor, int maxNum) {
        if (divisor == 0) {
            Log.i(TAG, "divisor shouldn't be 0");
            return "Error";
        }
        StringBuffer result = new StringBuffer();
        long dividend = unmber;
        long fraction = dividend % divisor;
        result.append(dividend / divisor);
        if (maxNum > 0) {
            result.append(".");
            for (int i = 0; i < maxNum; i++) {
                long dividend2 = fraction * 10;
                fraction = dividend2 % divisor;
                result.append(dividend2 / divisor);
            }
        }
        return result.toString();
    }

    
    private String formateSpeed(long speed) {
        long divisor;
        String unit;
        StringBuffer result = new StringBuffer();
        String str = UNIT_KB;
        long j = ERTRY_POINT;
        int maxFractionDigits = 0;
        if (speed < ERTRY_POINT) {
            divisor = ERTRY_POINT;
            maxFractionDigits = 2;
            unit = UNIT_KB;
        } else if (speed >= ERTRY_POINT && speed < ERTRY_POINT * ((long) THOUSAND)) {
            divisor = ERTRY_POINT;
            unit = UNIT_KB;
            if (speed >= ERTRY_POINT && speed < ERTRY_POINT * ((long) TEN)) {
                maxFractionDigits = 2;
            } else if (speed >= ERTRY_POINT * ((long) TEN) && speed < ERTRY_POINT * ((long) HANDRED)) {
                maxFractionDigits = 1;
            } else if (speed >= ERTRY_POINT * ((long) HANDRED) && speed < ERTRY_POINT * ((long) THOUSAND)) {
                maxFractionDigits = 0;
            }
        } else if (speed < ERTRY_POINT * ((long) THOUSAND) || speed >= ERTRY_POINT * ERTRY_POINT * ((long) THOUSAND)) {
            divisor = ERTRY_POINT * ERTRY_POINT * ERTRY_POINT;
            unit = UNIT_GB;
            maxFractionDigits = (speed < (ERTRY_POINT * ERTRY_POINT) * ((long) THOUSAND) || speed >= ((long) TEN) * divisor) ? (speed < ((ERTRY_POINT * ERTRY_POINT) * ERTRY_POINT) * ((long) TEN) || speed >= ((long) HANDRED) * divisor) ? 0 : 1 : 2;
        } else {
            divisor = ERTRY_POINT * ERTRY_POINT;
            unit = UNIT_MB;
            if (speed >= ERTRY_POINT * ((long) THOUSAND) && speed < ((long) TEN) * divisor) {
                maxFractionDigits = 2;
            } else if (speed >= ERTRY_POINT * ERTRY_POINT * ((long) TEN) && speed < ((long) HANDRED) * divisor) {
                maxFractionDigits = 1;
            } else if (speed >= ERTRY_POINT * ERTRY_POINT * ((long) HANDRED) && speed < ((long) THOUSAND) * divisor) {
                maxFractionDigits = 0;
            }
        }
        result.append(divToFractionDigits(speed, divisor, maxFractionDigits));
        result.append(":");
        result.append(unit);
        result.append("/S");
        return result.toString();
    }

    public void updateState() {
        boolean traceState = isNetworkSpeedTracing();
        if (DEBUG) Log.d(TAG, "updateState traceState:" + traceState);

        if (mNetworkTraceState != traceState) {
            mNetworkTraceState = traceState;
            if (mNetworkTraceState) {
                onStartTraceSpeed();
            } else {
                onStopTraceSpeed();
            }
            Message message = mHandler.obtainMessage();
            message.what = MSG_UPDATE_SHOW;
            mHandler.sendMessage(message);
        }
    }

    
    private void onShowStateChange() {
        boolean show = mNetworkTraceState;
        if (mShow != show) {
            mShow = show;
            if (DEBUG) Log.d(TAG, "onShowStateChange s:" + show);
            
            if (mIconController != null) {
                mIconController.setIconVisibility("networkspeed", show);
            }

            Iterator it = mNetworkSpeedStateCallBack.iterator();
            while (it.hasNext()) {
                ((INetworkSpeedStateCallBack) it.next()).onSpeedShow(show);
            }
        }
    }

    private void onStartTraceSpeed() {
        if (DEBUG) Log.d(TAG, "onStartTraceSpeed");
        updateSpeed();
    }

    private void onStopTraceSpeed() {
        if (DEBUG) Log.d(TAG, "onStopTraceSpeed");
        mIsFirstLoad = true;
        stopSpeed();
        mSpeed = "";
    }

    private void updateSpeed() {
        mIsFirstLoad = true;
        if (DEBUG) Log.d(TAG, "updateSpeed");
        mSpeed = "";
        Message message = mHandler.obtainMessage();
        message.what = MSG_UPDATE_NETWORTSPEED;
        message.obj = mSpeed;
        mHandler.sendMessage(message);
        if (mSpeedMachine != null) {
            mSpeedMachine.reset();
            mSpeedMachine.setTurnOn();
        }
        mBackgroundHandler.removeMessages(MSG_UPDATE_SPEED_ON_BG);
        Message msg = new Message();
        msg.what = MSG_UPDATE_SPEED_ON_BG;
        mBackgroundHandler.sendMessage(msg);
    }

    
    private void scheduleNextUpdate() {
        long nextTime = SystemClock.uptimeMillis() + ((long) (UPDATE_INTERVAL * 1000));
        Message msg = new Message();
        msg.what = MSG_UPDATE_SPEED_ON_BG;
        mBackgroundHandler.sendMessageAtTime(msg, nextTime);
    }

    private void stopSpeed() {
        if (mSpeedMachine != null) {
            mSpeedMachine.reset();
            mSpeedMachine.setTurnOff();
        }
        mBackgroundHandler.removeMessages(MSG_UPDATE_SPEED_ON_BG);
    }

    
    private void refreshSpeed() {
        Iterator it = mNetworkSpeedStateCallBack.iterator();
        while (it.hasNext()) {
            INetworkSpeedStateCallBack callback = (INetworkSpeedStateCallBack) it.next();
            if (callback != null) {
                callback.onSpeedChange(mSpeed);
            }
        }
    }

    private boolean isNetworkConnected() {
        boolean z = false;
        if (mContext == null) {
            return false;
        }
        NetworkInfo networkInfo = null;
        if (mConnectivityManager != null) {
            networkInfo = mConnectivityManager.getActiveNetworkInfo();
        }
        if (networkInfo != null && networkInfo.isAvailable()) {
            z = true;
        }
        boolean isNetworkConnected = z;
        if (DEBUG) Log.v(TAG, "isNetworkConnected = " + isNetworkConnected);
        
        return isNetworkConnected;
    }

    
    private boolean isNetworkSpeedTracing() {
        return isNetworkConnected() && !mBlockNetworkSpeed;
    }

    public void onTuningChanged(String key, String newValue) {
        if ("icon_blacklist".equals(key)) {
            boolean blocknetworkSpeed = StatusBarIconController.getIconBlacklist(newValue).contains("networkspeed");
            if (blocknetworkSpeed != mBlockNetworkSpeed) {
                Log.i(TAG, "onTuningChanged blocknetworkSpeed: " + blocknetworkSpeed);
                mBlockNetworkSpeed = blocknetworkSpeed;
                updateState();
            }
        }
    }

    public void addCallback(INetworkSpeedStateCallBack callback) {
        synchronized (this) {
            mNetworkSpeedStateCallBack.add(callback);
            try {
                callback.onSpeedChange(mSpeed);
                callback.onSpeedShow(mShow);
            } catch (Exception e) {
                Slog.w(TAG, "Failed to call to IKeyguardStateCallback", e);
            }
        }
    }

    public void removeCallback(INetworkSpeedStateCallBack callback) {
        synchronized (this) {
            mNetworkSpeedStateCallBack.remove(callback);
        }
    }

    
    private long[] getTetherStats() {
        long[] bytes = new long[2];
        try {
            NetworkStats stats = INetworkManagementService.Stub.asInterface(ServiceManager.getService("network_management")).getNetworkStatsTethering(-1);
            int size = stats != null ? stats.size() : 0;
            long sumTxBytes = 0;
            long sumRxBytes = 0;
            Entry entry = null;
            for (int i = 0; i < size; i++) {
                entry = stats.getValues(i, entry);
                sumRxBytes += entry.rxBytes;
                sumTxBytes += entry.txBytes;
            }
            bytes[0] = sumRxBytes;
            bytes[1] = sumTxBytes;
        } catch (RemoteException e) {
        }
        return bytes;
    }
}
