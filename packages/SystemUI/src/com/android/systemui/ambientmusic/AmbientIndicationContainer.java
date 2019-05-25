package com.android.systemui.ambientmusic;

import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.media.MediaMetadata;
import android.os.Handler;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.systemui.AutoReinflateContainer;
import com.android.systemui.R;
import com.android.systemui.doze.DozeLog;
import com.android.systemui.statusbar.phone.DozeParameters;
import com.android.systemui.statusbar.phone.StatusBar;

import com.android.systemui.ambientmusic.AmbientIndicationInflateListener;

import java.util.Locale;

public class AmbientIndicationContainer extends AutoReinflateContainer {
    private View mAmbientIndication;
    private CharSequence mIndication;
    private StatusBar mStatusBar;
    private AnimatedVectorDrawable mAnimatedIcon;
    private TextView mText;
    private Context mContext;
    private MediaMetadata mMediaMetaData;
    private String mMediaText;
    private boolean mForcedMediaDoze;
    private Handler mHandler;
    private boolean mInfoAvailable;
    private String mInfoToSet;
    private boolean mDozing;
    private String mLastInfo;

    private String mTrackInfoSeparator;

    public AmbientIndicationContainer(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mContext = context;
        final int iconSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.notification_menu_icon_padding);
        mAnimatedIcon = (AnimatedVectorDrawable) mContext.getDrawable(
                R.drawable.audioanim_animation).getConstantState().newDrawable();
        mAnimatedIcon.setBounds(0, 0, iconSize, iconSize);
        mTrackInfoSeparator = getResources().getString(R.string.ambientmusic_songinfo);
    }

    public void hideIndication() {
        setIndication(null, null);
        mAnimatedIcon.stop();
    }

    public void initializeView(StatusBar statusBar, Handler handler) {
        mStatusBar = statusBar;
        addInflateListener(new AmbientIndicationInflateListener(this));
        mHandler = handler;
    }

    public void updateAmbientIndicationView(View view) {
        mAmbientIndication = findViewById(R.id.ambient_indication);
        mText = (TextView)findViewById(R.id.ambient_indication_text);
        setIndication(mMediaMetaData, mMediaText);
    }

    public void setDozing(boolean dozing) {
        if (dozing == mDozing) return;

        mDozing = dozing;
        setTickerMarquee(dozing, false);
        if (dozing && mInfoAvailable) {
            mText.setText(mInfoToSet);
            mLastInfo = mInfoToSet;
            mAmbientIndication.setVisibility(View.VISIBLE);
            updatePosition();
        } else {
            setCleanLayout(-1);
            mAmbientIndication.setVisibility(View.INVISIBLE);
            mText.setText(null);
        }
    }

    private void setTickerMarquee(boolean enable, boolean extendPulseOnNewTrack) {
        if (enable) {
            setTickerMarquee(false, false);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mText.setEllipsize(TruncateAt.MARQUEE);
                    mText.setMarqueeRepeatLimit(2);
                    boolean rtl = TextUtils.getLayoutDirectionFromLocale(
                            Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL;
                    mText.setCompoundDrawables(rtl ? null : mAnimatedIcon, null, rtl ?
                            mAnimatedIcon : null, null);
                    mText.setSelected(true);
                    mAnimatedIcon.start();
                    if (extendPulseOnNewTrack && mStatusBar.isPulsing()) {
                        mStatusBar.getDozeScrimController().extendPulseForMusicTicker();
                    }
                }
            }, 1600);
        } else {
            mText.setEllipsize(null);
            mText.setSelected(false);
            mAnimatedIcon.stop();
        }
    }

    public void setOnPulseEvent(int reason, boolean pulsing) {
        setCleanLayout(reason);
        setTickerMarquee(pulsing,
                reason == DozeLog.PULSE_REASON_FORCED_MEDIA_NOTIFICATION);
    }

    public void setCleanLayout(int reason) {
        mForcedMediaDoze =
                reason == DozeLog.PULSE_REASON_FORCED_MEDIA_NOTIFICATION;
        updatePosition();
    }

    public void updatePosition() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) this.getLayoutParams();
        lp.gravity = mForcedMediaDoze ? Gravity.CENTER : Gravity.BOTTOM;
        this.setLayoutParams(lp);
    }

    public void setIndication(MediaMetadata mediaMetaData, String notificationText) {
        CharSequence charSequence = null;
        mInfoToSet = null;
        if (mediaMetaData != null) {
            CharSequence artist = mediaMetaData.getText(MediaMetadata.METADATA_KEY_ARTIST);
            CharSequence album = mediaMetaData.getText(MediaMetadata.METADATA_KEY_ALBUM);
            CharSequence title = mediaMetaData.getText(MediaMetadata.METADATA_KEY_TITLE);
            if (artist != null && album != null && title != null) {
                /* considering we are in Ambient mode here, it's not worth it to show
                    too many infos, so let's skip album name to keep a smaller text */
                charSequence = String.format(mTrackInfoSeparator, title.toString(), artist.toString());
            }
        }
        if (mDozing) {
            // if we are already showing an Ambient Notification with track info,
            // stop the current scrolling and start it delayed again for the next song
            setTickerMarquee(true, true);
        }

        if (!TextUtils.isEmpty(charSequence)) {
            mInfoToSet = charSequence.toString();
        } else if (!TextUtils.isEmpty(notificationText)) {
            mInfoToSet = notificationText;
        }

        mInfoAvailable = mInfoToSet != null;
        if (mInfoAvailable) {
            mMediaMetaData = mediaMetaData;
            mMediaText = notificationText;
            boolean isAnotherTrack = mInfoAvailable
                    && (TextUtils.isEmpty(mLastInfo) || (!TextUtils.isEmpty(mLastInfo) && !mLastInfo.equals(mInfoToSet)));
            if (!DozeParameters.getInstance(mContext).getAlwaysOn() && mStatusBar != null && isAnotherTrack) {
                mStatusBar.triggerAmbientForMedia();
            }
            if (mDozing) {
                mLastInfo = mInfoToSet;
            }
        }
        mText.setText(mInfoToSet);
        mAmbientIndication.setVisibility(mDozing && mInfoAvailable ? View.VISIBLE : View.INVISIBLE);
    }

    public View getIndication() {
        return mAmbientIndication;
    }
}
