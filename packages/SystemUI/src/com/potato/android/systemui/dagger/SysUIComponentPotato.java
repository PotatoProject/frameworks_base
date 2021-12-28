package com.potato.android.systemui.dagger;

import com.android.systemui.dagger.DefaultComponentBinder;
import com.android.systemui.dagger.DependencyProvider;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.SystemUIBinder;
import com.android.systemui.dagger.SysUIComponent;
import com.android.systemui.dagger.SystemUIModule;

import dagger.Subcomponent;

@SysUISingleton
@Subcomponent(modules = {
        DefaultComponentBinder.class,
        DependencyProvider.class,
        SystemUIBinder.class,
        SystemUIModule.class,
        SystemUIPotatoModule.class})
public interface SysUIComponentPotato extends SysUIComponent {
    @SysUISingleton
    @Subcomponent.Builder
    interface Builder extends SysUIComponent.Builder {
        SysUIComponentPotato build();
    }
}
