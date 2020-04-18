package com.android.systemui.qs;

public interface QSContainer {
    public void disable(int state1, int state2, boolean animate);
    public void setHeightOverride(int heightOverride);
    public void updateExpansion();
    public void setExpansion(float expansion);
}
