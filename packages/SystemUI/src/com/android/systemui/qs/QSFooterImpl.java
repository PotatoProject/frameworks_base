/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.ACTION_QS_DATE;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto;
import com.android.keyguard.CarrierText;
import com.android.keyguard.KeyguardStatusView;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.settingslib.Utils;
import com.android.settingslib.drawable.UserIconDrawable;
import com.android.settingslib.graph.SignalDrawable;
import com.android.systemui.Dependency;
import com.android.systemui.FontSizeUtils;
import com.android.systemui.R;
import com.android.systemui.R.dimen;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.qs.TouchAnimator.Builder;
import com.android.systemui.qs.TouchAnimator.ListenerAdapter;
import com.android.systemui.statusbar.phone.ExpandableIndicator;
import com.android.systemui.statusbar.phone.MultiUserSwitch;
import com.android.systemui.statusbar.phone.SettingsButton;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.EmergencyListener;
import com.android.systemui.statusbar.policy.NetworkController.SignalCallback;
import com.android.systemui.statusbar.policy.NextAlarmController;
import com.android.systemui.statusbar.policy.NextAlarmController.NextAlarmChangeCallback;
import com.android.systemui.statusbar.policy.UserInfoController;
import com.android.systemui.statusbar.policy.UserInfoController.OnUserInfoChangedListener;
import com.android.systemui.tuner.TunerService;

import static android.content.Context.VIBRATOR_SERVICE;

public class QSFooterImpl extends FrameLayout implements QSFooter,
        NextAlarmChangeCallback, OnClickListener, OnLongClickListener, OnUserInfoChangedListener, EmergencyListener, SignalCallback {

    private static final float EXPAND_INDICATOR_THRESHOLD = .93f;
    private ActivityStarter mActivityStarter;
    private NextAlarmController mNextAlarmController;
    private UserInfoController mUserInfoController;
    private SettingsButton mSettingsButton;
    protected View mSettingsContainer;
    private PageIndicator mPageIndicator;
    private CarrierText mCarrierText;

    private TextView mAlarmStatus;
    private View mAlarmStatusCollapsed;
    private View mDate;

    private boolean mQsDisabled;
    private QSPanel mQsPanel;

    private boolean mExpanded;

    protected ExpandableIndicator mExpandIndicator;

    private boolean mListening;
    private boolean mAlarmShowing;
    private AlarmManager.AlarmClockInfo mNextAlarm;

    private boolean mShowEmergencyCallsOnly;
    private View mDivider;
    protected MultiUserSwitch mMultiUserSwitch;
    private ImageView mMultiUserAvatar;

    protected TouchAnimator mFooterAnimator;
    private float mExpansionAmount;

    protected View mEdit;
    private View mDateTimeGroup;
    private boolean mKeyguardShowing;
    private TouchAnimator mSettingsCogAnimator;

    private View mActionsContainer;
    private View mDragHandle;
    private View mMobileGroup;
    private ImageView mMobileSignal;
    private ImageView mMobileRoaming;
    private final int mColorForeground;
    private final CellSignalState mInfo = new CellSignalState();
    private OnClickListener mExpandClickListener;
    private TouchAnimator mAlarmAnimator;

	private boolean mOreoQsPanel;

    private Vibrator mVibrator;

    public QSFooterImpl(Context context, AttributeSet attrs) {
        super(context, attrs);
        mColorForeground = Utils.getColorAttr(context, android.R.attr.colorForeground);
        mVibrator = (Vibrator) getContext().getSystemService(VIBRATOR_SERVICE);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mDivider = findViewById(R.id.qs_footer_divider);
        mEdit = findViewById(android.R.id.edit);
        mEdit.setOnClickListener(view ->
                Dependency.get(ActivityStarter.class).postQSRunnableDismissingKeyguard(() ->
                        mQsPanel.showEdit(view)));

        mPageIndicator = findViewById(R.id.footer_page_indicator);

        mDateTimeGroup = findViewById(R.id.date_time_alarm_group);
        mDate = findViewById(R.id.date);

        mExpandIndicator = findViewById(R.id.expand_indicator);
        mSettingsButton = findViewById(R.id.settings_button);
        mSettingsContainer = findViewById(R.id.settings_button_container);
        mSettingsButton.setOnClickListener(this);
        mSettingsButton.setOnLongClickListener(this);

        mAlarmStatusCollapsed = findViewById(R.id.alarm_status_collapsed);
        mAlarmStatus = findViewById(R.id.alarm_status);
        mDateTimeGroup.setOnClickListener(this);
        mDateTimeGroup.setOnLongClickListener(this);

        mMobileGroup = findViewById(R.id.mobile_combo);
        mMobileSignal = findViewById(R.id.mobile_signal);
        mMobileRoaming = findViewById(R.id.mobile_roaming);
        mCarrierText = findViewById(R.id.qs_carrier_text);

        mMultiUserSwitch = findViewById(R.id.multi_user_switch);
        mMultiUserAvatar = mMultiUserSwitch.findViewById(R.id.multi_user_avatar);

        mDragHandle = findViewById(R.id.qs_drag_handle_view);
        mActionsContainer = findViewById(R.id.qs_footer_actions_container);

        // RenderThread is doing more harm than good when touching the header (to expand quick
        // settings), so disable it for this view
        ((RippleDrawable) mExpandIndicator.getBackground()).setForceSoftware(true);
        ((RippleDrawable) mSettingsButton.getBackground()).setForceSoftware(true);

        updateResources();

        mNextAlarmController = Dependency.get(NextAlarmController.class);
        mUserInfoController = Dependency.get(UserInfoController.class);
        mActivityStarter = Dependency.get(ActivityStarter.class);
        addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight,
                oldBottom) -> updateAnimator(right - left));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        updateEverything();
    }

    private void updateAnimator(int width) {
        int endMargin = (mInfo.visible ? mContext.getResources().getDimensionPixelSize(R.dimen.qs_footer_mobilegroup_margin_end) : 0);
        int sum = endMargin + (mInfo.visible ? mMobileGroup.getWidth() : 0);

        int numTiles = QuickQSPanel.getNumQuickTiles(mContext);
        int size = mContext.getResources().getDimensionPixelSize(R.dimen.qs_quick_tile_size)
                - mContext.getResources().getDimensionPixelSize(dimen.qs_quick_tile_padding);
        int remaining = (width - numTiles * size) / (numTiles - 1);
        int defSpace = mContext.getResources().getDimensionPixelOffset(R.dimen.default_gear_space);

        mSettingsCogAnimator = new Builder()
                .addFloat(mCarrierText, "translationX", -sum, 0)
                .addFloat(mSettingsContainer, "translationX", mOreoQsPanel ? -(remaining - defSpace) : 0, 0)
                .addFloat(mSettingsButton, "rotation", -120, 0)
                .build();

        if(mOreoQsPanel) {
			if (mAlarmShowing) {
            	int translate = isLayoutRtl() ? mDate.getWidth() : -mDate.getWidth();            
            	mAlarmAnimator = new Builder().addFloat(mDate, "alpha", 1, 0)
                    	.addFloat(mDateTimeGroup, "translationX", 0, translate)
                    	.addFloat(mAlarmStatus, "alpha", 0, 1)
                    	.setListener(new ListenerAdapter() {
                        	@Override
                        	public void onAnimationAtStart() {
                        	    mAlarmStatus.setVisibility(View.GONE);
                        	}

                        	@Override
                        	public void onAnimationStarted() {
                        	    mAlarmStatus.setVisibility(View.VISIBLE);
                        	}
                    	}).build();
        	} else {
            	mAlarmAnimator = null;
            	mAlarmStatus.setVisibility(View.GONE);
            	mDate.setAlpha(1);
           		mDateTimeGroup.setTranslationX(0);
			}
        }

        setExpansion(mExpansionAmount);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateResources();
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        updateResources();
    }

    private void updateResources() {
        FontSizeUtils.updateFontSize(mAlarmStatus, R.dimen.qs_date_collapsed_size);

        updateFooterAnimator();

        mOreoQsPanel = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.QS_PANEL_USE_OREO_STYLE, 0,
                UserHandle.USER_CURRENT) == 1;

        mExpandIndicator.setVisibility(mOreoQsPanel ? View.VISIBLE : View.GONE);
		mDateTimeGroup.setVisibility(mOreoQsPanel ? View.VISIBLE : View.GONE);

        // Update the width and weight of the actions container as the page indicator can sometimes
        // show and the layout needs to center it between the carrier text and actions container.
        LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) mActionsContainer.getLayoutParams();
        params.width = mContext.getResources().getInteger(R.integer.qs_footer_actions_width);
        params.weight = mContext.getResources().getInteger(R.integer.qs_footer_actions_weight);
        mActionsContainer.setLayoutParams(params);
    }

    private void updateFooterAnimator() {
        mFooterAnimator = createFooterAnimator();
    }

    @Nullable
    private TouchAnimator createFooterAnimator() {
        return new TouchAnimator.Builder()
                .addFloat(mDivider, "alpha", 0, 1)
                .addFloat(mEdit, "alpha", 0, 1)
                .addFloat(mPageIndicator, "alpha", 0, 1)
                .addFloat(mMobileSignal, "alpha", 0, 1)
                .addFloat(mMobileRoaming, "alpha", 0, 1)
                .addFloat(mDragHandle, "alpha", 0, 0, 0)
                .addFloat(mMultiUserSwitch, "alpha", 0, 1)
                .setStartDelay(0.15f)
                .build();
    }

    @Override
    public void setKeyguardShowing(boolean keyguardShowing) {
        mKeyguardShowing = keyguardShowing;
        setExpansion(mExpansionAmount);
    }

    @Override
    public void setExpandClickListener(OnClickListener onClickListener) {
        mExpandClickListener = onClickListener;
    }

    @Override
    public void setExpanded(boolean expanded) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        updateEverything();
    }

	@Override
    public void onNextAlarmChanged(AlarmManager.AlarmClockInfo nextAlarm) {
        mNextAlarm = nextAlarm;
        if (nextAlarm != null) {
            String alarmString = KeyguardStatusView.formatNextAlarm(getContext(), nextAlarm);
            mAlarmStatus.setText(alarmString);
            mAlarmStatus.setContentDescription(mContext.getString(
                    R.string.accessibility_quick_settings_alarm, alarmString));
            mAlarmStatusCollapsed.setContentDescription(mContext.getString(
                    R.string.accessibility_quick_settings_alarm, alarmString));
        }
        if (mAlarmShowing != (nextAlarm != null)) {
            mAlarmShowing = nextAlarm != null;
            updateAnimator(getWidth());
            updateEverything();
        }
	}

    @Override
    public void setExpansion(float headerExpansionFraction) {
        mExpansionAmount = headerExpansionFraction;
        if (mSettingsCogAnimator != null) mSettingsCogAnimator.setPosition(headerExpansionFraction);
        if (mAlarmAnimator != null) mAlarmAnimator.setPosition(
                mKeyguardShowing ? 0 : headerExpansionFraction);

        if (mFooterAnimator != null) {
            mFooterAnimator.setPosition(headerExpansionFraction);
        }

        updateAlarmVisibilities();

        mExpandIndicator.setExpanded(headerExpansionFraction > EXPAND_INDICATOR_THRESHOLD);
    }

    @Override
    @VisibleForTesting
    public void onDetachedFromWindow() {
        setListening(false);
        super.onDetachedFromWindow();
    }

	private void updateAlarmVisibilities() {
        mAlarmStatusCollapsed.setVisibility(mAlarmShowing ? View.VISIBLE : View.GONE);
	}

    @Override
    public void setListening(boolean listening) {
        if (listening == mListening) {
            return;
        }
        mListening = listening;
        updateListeners();
    }

    @Override
    public View getExpandView() {
        return findViewById(R.id.expand_indicator);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_EXPAND) {
            if (mExpandClickListener != null) {
                mExpandClickListener.onClick(null);
                return true;
            }
        }
        return super.performAccessibilityAction(action, arguments);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND);
    }

    @Override
    public void disable(int state1, int state2, boolean animate) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        updateEverything();
    }

    public void updateEverything() {
        post(() -> {
            updateVisibilities();
            setClickable(false);
        });
    }

    private void updateVisibilities() {
        updateAlarmVisibilities();
        final boolean isDemo = UserManager.isDeviceInDemoMode(mContext);
        mSettingsContainer.setVisibility(View.VISIBLE);
        mSettingsButton.setVisibility(View.VISIBLE);

        mMultiUserSwitch.setVisibility(showUserSwitcher(isDemo) ? View.VISIBLE : View.INVISIBLE);
        mEdit.setVisibility(isDemo || !mExpanded ? View.INVISIBLE : View.VISIBLE);

        if(mOreoQsPanel) {
            mMobileGroup.setVisibility(View.GONE);
            mMobileSignal.setVisibility(View.GONE);
            mMobileRoaming.setVisibility(View.GONE);
            mCarrierText.setVisibility(View.GONE);
        }
    }

    private boolean showUserSwitcher(boolean isDemo) {
        if (!mExpanded || isDemo || !UserManager.supportsMultipleUsers()) {
            return false;
        }
        UserManager userManager = UserManager.get(mContext);
        if (userManager.hasUserRestriction(UserManager.DISALLOW_USER_SWITCH)) {
            return false;
        }
        int switchableUserCount = 0;
        for (UserInfo user : userManager.getUsers(true)) {
            if (user.supportsSwitchToByUser()) {
                ++switchableUserCount;
                if (switchableUserCount > 1) {
                    return true;
                }
            }
        }
        return getResources().getBoolean(R.bool.qs_show_user_switcher_for_single_user);
    }

    private void updateListeners() {
        if (mListening) {
            mNextAlarmController.addCallback(this);
            mUserInfoController.addCallback(this);
            if (Dependency.get(NetworkController.class).hasVoiceCallingFeature()) {
                Dependency.get(NetworkController.class).addEmergencyListener(this);
                Dependency.get(NetworkController.class).addCallback(this);
            }
        } else {
            mNextAlarmController.removeCallback(this);
            mUserInfoController.removeCallback(this);
            Dependency.get(NetworkController.class).removeEmergencyListener(this);
            Dependency.get(NetworkController.class).removeCallback(this);
        }
    }

    @Override
    public void setQSPanel(final QSPanel qsPanel) {
        mQsPanel = qsPanel;
        if (mQsPanel != null) {
            mMultiUserSwitch.setQsPanel(qsPanel);
            mQsPanel.setFooterPageIndicator(mPageIndicator);
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mSettingsButton) {
            if (!Dependency.get(DeviceProvisionedController.class).isCurrentUserSetup()) {
                // If user isn't setup just unlock the device and dump them back at SUW.
                mActivityStarter.postQSRunnableDismissingKeyguard(() -> { });
                return;
            }
            MetricsLogger.action(mContext,
                    mExpanded ? MetricsProto.MetricsEvent.ACTION_QS_EXPANDED_SETTINGS_LAUNCH
                            : MetricsProto.MetricsEvent.ACTION_QS_COLLAPSED_SETTINGS_LAUNCH);
            startSettingsActivity();
        } else if (v == mDateTimeGroup) {
            if (mAlarmShowing) {
                Dependency.get(MetricsLogger.class).action(ACTION_QS_DATE,
                        true);
                PendingIntent showIntent = mNextAlarm.getShowIntent();
                mActivityStarter.startPendingIntentDismissingKeyguard(showIntent);
            } else {
                Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
                builder.appendPath("time");
                builder.appendPath(Long.toString(System.currentTimeMillis()));
                Intent todayIntent = new Intent(Intent.ACTION_VIEW, builder.build());
                mActivityStarter.postStartActivityDismissingKeyguard(todayIntent, 0);
            }
		}
    }

    @Override
    public boolean onLongClick(View v) {
        if (v == mSettingsButton) {
            startFriesDashboardActivity();
            mVibrator.vibrate(50);
        } else if (v == mDateTimeGroup) {
            if (mAlarmShowing) {
                Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
                builder.appendPath("time");
                builder.appendPath(Long.toString(System.currentTimeMillis()));
                Intent todayIntent = new Intent(Intent.ACTION_VIEW, builder.build());
                mActivityStarter.postStartActivityDismissingKeyguard(todayIntent, 0);
            } else {
                mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                        AlarmClock.ACTION_SHOW_ALARMS), 0);
            }
			mVibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
		}
        return false;
    }

    private ComponentName FRIES_DASHBOARD_SETTING_COMPONENT = new ComponentName(
            "com.android.settings", "com.android.settings.Settings$FriesDashboardActivity");

    private void startFriesDashboardActivity() {
        mActivityStarter.startActivity(new Intent().setComponent(FRIES_DASHBOARD_SETTING_COMPONENT),
                true /* dismissShade */);
    }

    private void startSettingsActivity() {
        mActivityStarter.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS),
                true /* dismissShade */);
    }

    @Override
    public void setEmergencyCallsOnly(boolean show) {
        boolean changed = show != mShowEmergencyCallsOnly;
        if (changed) {
            mShowEmergencyCallsOnly = show;
            if (mExpanded) {
                updateEverything();
            }
        }
    }

    @Override
    public void onUserInfoChanged(String name, Drawable picture, String userAccount) {
        if (picture != null &&
                UserManager.get(mContext).isGuestUser(KeyguardUpdateMonitor.getCurrentUser()) &&
                !(picture instanceof UserIconDrawable)) {
            picture = picture.getConstantState().newDrawable(mContext.getResources()).mutate();
            picture.setColorFilter(
                    Utils.getColorAttr(mContext, android.R.attr.colorForeground),
                    Mode.SRC_IN);
        }
        mMultiUserAvatar.setImageDrawable(picture);
    }

    private void handleUpdateState() {
        mMobileGroup.setVisibility(mInfo.visible ? View.VISIBLE : View.GONE);
        if (mInfo.visible) {
            mMobileRoaming.setVisibility(mInfo.roaming ? View.VISIBLE : View.GONE);
            mMobileRoaming.setImageTintList(ColorStateList.valueOf(mColorForeground));
            SignalDrawable d = new SignalDrawable(mContext);
            d.setDarkIntensity(QuickStatusBarHeader.getColorIntensity(mColorForeground));
            mMobileSignal.setImageDrawable(d);
            mMobileSignal.setImageLevel(mInfo.mobileSignalIconId);

            StringBuilder contentDescription = new StringBuilder();
            if (mInfo.contentDescription != null) {
                contentDescription.append(mInfo.contentDescription).append(", ");
            }
            if (mInfo.roaming) {
                contentDescription
                        .append(mContext.getString(R.string.data_connection_roaming))
                        .append(", ");
            }
            // TODO: show mobile data off/no internet text for 5 seconds before carrier text
            if (TextUtils.equals(mInfo.typeContentDescription,
                    mContext.getString(R.string.data_connection_no_internet))
                || TextUtils.equals(mInfo.typeContentDescription,
                    mContext.getString(R.string.cell_data_off_content_description))) {
                contentDescription.append(mInfo.typeContentDescription);
            }
            mMobileSignal.setContentDescription(contentDescription);
        }
    }

    @Override
    public void setMobileDataIndicators(NetworkController.IconState statusIcon,
            NetworkController.IconState qsIcon, int statusType,
            int qsType, boolean activityIn, boolean activityOut, int volteId,
            String typeContentDescription,
            String description, boolean isWide, int subId, boolean roaming) {
        mInfo.visible = statusIcon.visible;
        mInfo.mobileSignalIconId = statusIcon.icon;
        mInfo.contentDescription = statusIcon.contentDescription;
        mInfo.typeContentDescription = typeContentDescription;
        mInfo.roaming = roaming;
        handleUpdateState();
    }

    @Override
    public void setNoSims(boolean hasNoSims, boolean simDetected) {
        if (hasNoSims) {
            mInfo.visible = false;
        }
        handleUpdateState();
    }

    private final class CellSignalState {
        boolean visible;
        int mobileSignalIconId;
        public String contentDescription;
        String typeContentDescription;
        boolean roaming;
    }
}
