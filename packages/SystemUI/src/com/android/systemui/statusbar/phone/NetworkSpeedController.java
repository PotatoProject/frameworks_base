package com.android.systemui.statusbar.phone;

import com.android.systemui.statusbar.policy.CallbackController;
import java.util.BitSet;

public interface NetworkSpeedController extends CallbackController<INetworkSpeedStateCallBack> {
    void updateConnectivity(BitSet bitSet, BitSet bitSet2);
}
