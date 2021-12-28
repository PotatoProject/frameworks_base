package com.potato.android.systemui;

import android.content.Context;

import com.potato.android.systemui.dagger.DaggerGlobalRootComponentPotato;
import com.potato.android.systemui.dagger.GlobalRootComponentPotato;

import com.android.systemui.SystemUIFactory;
import com.android.systemui.dagger.GlobalRootComponent;

public class SystemUIPotatoFactory extends SystemUIFactory {
    @Override
    protected GlobalRootComponent buildGlobalRootComponent(Context context) {
        return DaggerGlobalRootComponentPotato.builder()
                .context(context)
                .build();
    }
}
