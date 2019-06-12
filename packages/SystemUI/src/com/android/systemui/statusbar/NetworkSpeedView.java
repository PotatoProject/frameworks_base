package com.android.systemui.statusbar;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.keyguard.ScreenLifecycle;
import com.android.systemui.keyguard.ScreenLifecycle.Observer;
import com.android.systemui.statusbar.phone.INetworkSpeedStateCallBack;
import com.android.systemui.statusbar.phone.NetworkSpeedController;

public class NetworkSpeedView extends LinearLayout implements INetworkSpeedStateCallBack {
    
    private static final boolean DEBUG = false;

    private Context mContext;
    private boolean mIsVisible;
    private NetworkSpeedController mNetworkSpeedController;
    private ScreenLifecycle mScreenLifecycle;
    private final Observer mScreenObserver;
    private String mTextDown;
    private String mTextUp;
    private TextView mTextViewDown;
    private TextView mTextViewUp;

    public NetworkSpeedView(Context context) {
        this(context, null);
    }

    public NetworkSpeedView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NetworkSpeedView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mIsVisible = false;

        mScreenObserver = new Observer() {
            public void onScreenTurnedOff() {
            }

            public void onScreenTurnedOn() {
                updateText();
            }
        };

        mNetworkSpeedController = (NetworkSpeedController) Dependency.get(NetworkSpeedController.class);
        mScreenLifecycle = (ScreenLifecycle) Dependency.get(ScreenLifecycle.class);
        mContext = context;
    }
    
    public void onFinishInflate() {
        super.onFinishInflate();
        mTextViewUp = findViewById(R.id.speed_word_up);
        mTextViewDown = findViewById(R.id.speed_word_down);
        if (DEBUG) Log.i("NetworkSpeedView", "onFinishInflate");
        refreshTextView();
    }

    public void onSpeedChange(String speed) {
        String[] tokens = speed.split(":");
        if (tokens.length == 2) {
            mTextUp = tokens[0];
            mTextDown = tokens[1];
            updateText();
        }
    }

    public void onSpeedShow(boolean show) {
        //for now keep empty i guess?
    }
    
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        registerReceiver();
    }

    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unregisterReceiver();
    }

    public void onConfigurationChanged(Configuration newConfig) {
        if (DEBUG) Log.i("NetworkSpeedView", " onConfigurationChanged: " + newConfig);
    }

    public void registerReceiver() {
        mNetworkSpeedController.addCallback(this);
        mScreenLifecycle.addObserver(mScreenObserver);
    }

    public void unregisterReceiver() {
        mNetworkSpeedController.removeCallback(this);
        mScreenLifecycle.removeObserver(mScreenObserver);
    }

    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        boolean isVisiable = visibility == 0;
        
        if (DEBUG) Log.i("NetworkSpeedView", " setVisibility: " + visibility);
        
        if (mIsVisible != isVisiable) {
            mIsVisible = isVisiable;
            updateText();
        }
    }

    private void updateText() {
        boolean isScreenTurnedOn = mScreenLifecycle.getScreenState() == 2;
        
        if (DEBUG) Log.i("NetworkSpeedView", " updateText: " + mTextUp + " " + mTextDown +
                " mIsVisible: " + mIsVisible +
                " mScreenOn: " + isScreenTurnedOn);
        
        if (mIsVisible && isScreenTurnedOn && mTextViewUp != null && mTextViewDown != null) {
            mTextViewUp.setText(mTextUp);
            mTextViewDown.setText(mTextDown);
        }
    }

    public void setTextColor(int color) {
        if (mTextViewUp != null && mTextViewDown != null) {
            mTextViewUp.setTextColor(color);
            mTextViewDown.setTextColor(color);
        }
    }

    private void refreshTextView() {
        if (mTextViewUp != null && mTextViewDown != null) {
            mTextViewUp.setLetterSpacing(-0.05f);
            mTextViewDown.setLetterSpacing(0.05f);
        }
    }
}
