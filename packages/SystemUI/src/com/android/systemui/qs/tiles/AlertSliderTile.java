/*
 * Copyright (C) 2016 The ParanoidAndroid Project
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
 * limitations under the License.
 */

package com.android.systemui.qs.tiles;

import android.app.NotificationManager;
import android.app.NotificationManager.Policy;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.ZenModeConfig;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import com.android.systemui.Dependency;
import com.android.systemui.Prefs;
import com.android.systemui.R;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.plugins.qs.DetailAdapter;
import com.android.systemui.qs.QSHost;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.statusbar.policy.ZenModeController;
import com.android.systemui.volume.SegmentedButtons;

/** Quick settings tile: Alert slider **/
public class AlertSliderTile extends QSTileImpl<BooleanState>  {

    private static final Intent ZEN_SETTINGS =
            new Intent(Settings.ACTION_ZEN_MODE_SETTINGS);

    private static final Intent ZEN_PRIORITY_SETTINGS =
            new Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS);

    private static final QSTile.Icon TOTAL_SILENCE =
            ResourceIcon.get(R.drawable.ic_qs_dnd_on_total_silence);

    private static final QSTile.Icon ALARMS_ONLY =
            ResourceIcon.get(R.drawable.ic_qs_dnd_on);

    private static final QSTile.Icon PRIORITY_ONLY =
            ResourceIcon.get(R.drawable.ic_qs_dnd_on_priority);

    private static final QSTile.Icon DISABLED =
            ResourceIcon.get(R.drawable.ic_qs_dnd_off);

    private final ZenModeController mController;
    private final AlertSliderDetailAdapter mDetailAdapter;
    private Policy mPolicy;

    private boolean mListening;
    private boolean mHasAlertSlider = false;
    private boolean mCollapseDetailOnZenChanged = true;

    public AlertSliderTile(QSHost host) {
        super(host);
        mController = Dependency.get(ZenModeController.class);
        mDetailAdapter = new AlertSliderDetailAdapter();
        mHasAlertSlider = mContext.getResources().getBoolean(com.android.internal.R.bool.config_hasAlertSlider)
                && !TextUtils.isEmpty(mContext.getResources().getString(com.android.internal.R.string.alert_slider_state_path))
                && !TextUtils.isEmpty(mContext.getResources().getString(com.android.internal.R.string.alert_slider_uevent_match_path));
        mPolicy = NotificationManager.from(mContext).getNotificationPolicy();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_dnd_label);
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return mDetailAdapter;
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        mCollapseDetailOnZenChanged = true;
        final int zen = getZenMode();
        if (zen != Settings.Global.ZEN_MODE_OFF) {
            showDetail(true);
        }
    }

    @Override
    protected void handleSecondaryClick() {
        // Secondary clicks are header clicks, just toggle.
        handleClick();
    }

    @Override
    public boolean isAvailable() {
        return mHasAlertSlider;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int zen = arg instanceof Integer ? (Integer) arg : getZenMode();
        switch (zen) {
            case Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS:
                state.icon = PRIORITY_ONLY;
                state.label = mContext.getString(R.string.quick_settings_dnd_priority_label);
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_dnd_priority_on);
                break;
            case Settings.Global.ZEN_MODE_NO_INTERRUPTIONS:
                state.icon = TOTAL_SILENCE;
                state.label = mContext.getString(R.string.quick_settings_dnd_none_label);
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_dnd_none_on);
                break;
            case Settings.Global.ZEN_MODE_ALARMS:
                state.icon = ALARMS_ONLY;
                state.label = mContext.getString(R.string.quick_settings_dnd_alarms_label);
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_dnd_alarms_on);
                break;
            default:
                state.icon = DISABLED;
                state.label = mContext.getString(R.string.quick_settings_alert_slider_none_label);
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_dnd_off);
                break;
        }
    }

    private int getZenMode() {
        return mController.getZen();
    }

    private void setZenMode(int mode) {
        mController.setZen(mode, null, TAG);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_DND;
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (mListening) {
            mController.addCallback(mZenCallback);
        } else {
            mController.removeCallback(mZenCallback);
        }
    }

    private final ZenModeController.Callback mZenCallback = new ZenModeController.Callback() {
        @Override
        public void onZenChanged(int zen) {
            if (mCollapseDetailOnZenChanged) {
                showDetail(false);
            }
            refreshState(zen);
            mCollapseDetailOnZenChanged = true;
        }
    };

    private final class AlertSliderDetailAdapter implements DetailAdapter {

        private SegmentedButtons mButtons;
        private TextView mMessageText, mZenPriorityIntroductionCustomize;
        private RelativeLayout mMessageBox;
        private RelativeLayout mRemindersSwitchContainer, mEventsSwitchContainer, mZenPriorityIntroduction;
        private Switch mReminders, mEvents;
        private ImageView mZenPriorityIntroductionConfirm;

        @Override
        public CharSequence getTitle() {
            return mContext.getString(R.string.quick_settings_dnd_label);
        }

        @Override
        public Boolean getToggleState() {
            return null;
        }

        @Override
        public Intent getSettingsIntent() {
            return ZEN_SETTINGS;
        }

        @Override
        public void setToggleState(boolean state) { }

        @Override
        public int getMetricsCategory() {
            return MetricsEvent.QS_DND_DETAILS;
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            final LinearLayout details = convertView != null ? (LinearLayout) convertView
                    : (LinearLayout) LayoutInflater.from(context).inflate(
                    R.layout.alert_slider_panel, parent, false);

            int state = getZenMode();

            mCollapseDetailOnZenChanged = true;

            mPolicy = NotificationManager.from(mContext).getNotificationPolicy();

            if (convertView == null) {
                mButtons = (SegmentedButtons) details.findViewById(R.id.alert_slider_buttons);
                mMessageText = (TextView) details.findViewById(R.id.alert_slider_introduction_message);
                mMessageBox = (RelativeLayout) details.findViewById(R.id.alert_slider_introduction);
                mZenPriorityIntroduction = (RelativeLayout) details.findViewById(R.id.zen_introduction);
                mZenPriorityIntroductionConfirm = (ImageView) details.findViewById(R.id.zen_introduction_confirm);
                mZenPriorityIntroductionConfirm.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Prefs.putBoolean(mContext, Prefs.Key.DND_CONFIRMED_PRIORITY_INTRODUCTION, true);
                    }
                });
                mZenPriorityIntroductionCustomize = (TextView) details.findViewById(R.id.zen_introduction_customize);
                mZenPriorityIntroductionCustomize.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Prefs.putBoolean(mContext, Prefs.Key.DND_CONFIRMED_PRIORITY_INTRODUCTION, true);
                        Dependency.get(ActivityStarter.class).postStartActivityDismissingKeyguard(
                                ZEN_PRIORITY_SETTINGS, 0);
                    }
                });
                mRemindersSwitchContainer = (RelativeLayout) details.findViewById(R.id.switch_container_reminders);
                mRemindersSwitchContainer.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mReminders != null) {
                            final boolean isChecked = mReminders.isChecked();
                            mReminders.setChecked(!isChecked);
                        }
                    }
                });
                mReminders = (Switch) details.findViewById(R.id.toggle_reminders);
                mReminders.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        MetricsLogger.action(mContext, MetricsEvent.ACTION_ZEN_ALLOW_REMINDERS, isChecked);
                        if (DEBUG) Log.d(TAG, "onPrefChange allowReminders=" + isChecked);
                        savePolicy(getNewPriorityCategories(isChecked, Policy.PRIORITY_CATEGORY_REMINDERS),
                                mPolicy.priorityCallSenders, mPolicy.priorityMessageSenders,
                                mPolicy.suppressedVisualEffects);
                    }
                });
                mEventsSwitchContainer = (RelativeLayout) details.findViewById(R.id.switch_container_events);
                mEventsSwitchContainer.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mEvents != null) {
                            final boolean isChecked = mEvents.isChecked();
                            mEvents.setChecked(!isChecked);
                        }
                    }
                });
                mEvents = (Switch) details.findViewById(R.id.toggle_events);
                mEvents.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        MetricsLogger.action(mContext, MetricsEvent.ACTION_ZEN_ALLOW_EVENTS, isChecked);
                        if (DEBUG) Log.d(TAG, "onPrefChange allowEvents=" + isChecked);
                        savePolicy(getNewPriorityCategories(isChecked, Policy.PRIORITY_CATEGORY_EVENTS),
                                mPolicy.priorityCallSenders, mPolicy.priorityMessageSenders,
                                mPolicy.suppressedVisualEffects);
                    }
                });
                mButtons.addButton(R.string.interruption_level_alarms_twoline,
                        R.string.quick_settings_dnd_alarms_label,
                        Settings.Global.ZEN_MODE_ALARMS);
                mButtons.addButton(R.string.interruption_level_none_twoline,
                        R.string.quick_settings_dnd_none_label,
                        Settings.Global.ZEN_MODE_NO_INTERRUPTIONS);
                mButtons.setCallback(mButtonsCallback);
            }

            mReminders.setChecked(isPriorityCategoryEnabled(Policy.PRIORITY_CATEGORY_REMINDERS));
            mEvents.setChecked(isPriorityCategoryEnabled(Policy.PRIORITY_CATEGORY_EVENTS));

            mButtons.setSelectedValue(state, false /* fromClick */);
            refresh(state);

            return details;
        }

        private void refresh(int state) {
            switch(state) {
                case Settings.Global.ZEN_MODE_ALARMS:
                    // silent
                    mButtons.setVisibility(View.VISIBLE);
                    mMessageBox.setVisibility(View.VISIBLE);
                    mMessageText.setText(mContext.getString(R.string.quick_settings_alert_slider_detail_alarms_only_description));
                    // priority
                    mRemindersSwitchContainer.setVisibility(View.GONE);
                    mEventsSwitchContainer.setVisibility(View.GONE);
                    mZenPriorityIntroduction.setVisibility(View.GONE);
                    break;
                case Settings.Global.ZEN_MODE_NO_INTERRUPTIONS:
                    // silent
                    mButtons.setVisibility(View.VISIBLE);
                    mMessageBox.setVisibility(View.VISIBLE);
                    mMessageText.setText(mContext.getString(R.string.zen_silence_introduction_voice));
                    // priority
                    mRemindersSwitchContainer.setVisibility(View.GONE);
                    mEventsSwitchContainer.setVisibility(View.GONE);
                    mZenPriorityIntroduction.setVisibility(View.GONE);
                    break;
                case Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS:
                    // silent
                    mButtons.setVisibility(View.GONE);
                    mMessageBox.setVisibility(View.GONE);
                    // priority
                    mRemindersSwitchContainer.setVisibility(View.VISIBLE);
                    mEventsSwitchContainer.setVisibility(View.VISIBLE);
                    final boolean confirmed =  false; // Prefs.getBoolean(mContext, Prefs.Key.DND_CONFIRMED_PRIORITY_INTRODUCTION, false);
                    mZenPriorityIntroductionConfirm.setVisibility(View.GONE);
                    mZenPriorityIntroduction.setVisibility(!confirmed ? View.VISIBLE : View.GONE);
                    break;
                default:
                    mButtons.setVisibility(View.GONE);
                    mMessageBox.setVisibility(View.VISIBLE);
                    mMessageText.setText(mContext.getString(R.string.zen_silence_introduction_voice));
                    mRemindersSwitchContainer.setVisibility(View.GONE);
                    mEventsSwitchContainer.setVisibility(View.GONE);
                    mZenPriorityIntroduction.setVisibility(View.GONE);
                    break;
            }
        }

        private final SegmentedButtons.Callback mButtonsCallback = new SegmentedButtons.Callback() {

            @Override
            public void onSelected(final Object value, boolean fromClick) {
                if (value != null && mButtons.isShown()) {
                    int state = (Integer) value;
                    if (fromClick) {
                        MetricsLogger.action(mContext, MetricsEvent.QS_DND, state);
                        mCollapseDetailOnZenChanged = false;
                        setSilentMode(state);
                        setZenMode(state);
                        refresh(state);
                    }
                }
            }

            @Override
            public void onInteraction() { }
        };

        public void setSilentMode(int silentState) {
            int silentMode = silentState == Settings.Global.ZEN_MODE_ALARMS ? 0 : 1;
            Settings.System.putIntForUser(mContext.getContentResolver(),
                    Settings.System.ALERT_SLIDER_SILENT_MODE, silentMode, UserHandle.USER_CURRENT);
        }
    }

    private boolean isPriorityCategoryEnabled(int categoryType) {
        return (mPolicy.priorityCategories & categoryType) != 0;
    }

    private int getNewPriorityCategories(boolean allow, int categoryType) {
        int priorityCategories = mPolicy.priorityCategories;
        if (allow) {
            priorityCategories |= categoryType;
        } else {
            priorityCategories &= ~categoryType;
        }
        return priorityCategories;
    }

    private void savePolicy(int priorityCategories, int priorityCallSenders,
            int priorityMessageSenders, int suppressedVisualEffects) {
        mPolicy = new Policy(priorityCategories, priorityCallSenders, priorityMessageSenders,
                suppressedVisualEffects);
        NotificationManager.from(mContext).setNotificationPolicy(mPolicy);
    }
}
