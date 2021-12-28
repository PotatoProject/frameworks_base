package com.posp.android.systemui;

import android.content.Context;

import com.posp.android.systemui.dagger.DaggerGlobalRootComponentPosp;
import com.posp.android.systemui.dagger.GlobalRootComponentPosp;

import com.android.systemui.SystemUIFactory;
import com.android.systemui.dagger.GlobalRootComponent;

public class SystemUIPospFactory extends SystemUIFactory {
    @Override
    protected GlobalRootComponent buildGlobalRootComponent(Context context) {
        return DaggerGlobalRootComponentPosp.builder()
                .context(context)
                .build();
    }
}
