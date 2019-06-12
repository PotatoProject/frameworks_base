package com.android.systemui.statusbar.phone;

public interface INetworkSpeedStateCallBack {
    void onSpeedChange(String speed);

    void onSpeedShow(boolean show);
}
