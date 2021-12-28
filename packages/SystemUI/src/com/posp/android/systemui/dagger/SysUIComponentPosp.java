package com.posp.android.systemui.dagger;

import com.android.systemui.dagger.DefaultComponentBinder;
import com.android.systemui.dagger.DependencyProvider;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.SystemUIBinder;
import com.android.systemui.dagger.SysUIComponent;
import com.android.systemui.dagger.SystemUIModule;

import com.posp.android.systemui.keyguard.KeyguardSliceProviderPosp;
import com.posp.android.systemui.smartspace.KeyguardSmartspaceController;

import dagger.Subcomponent;

@SysUISingleton
@Subcomponent(modules = {
        DefaultComponentBinder.class,
        DependencyProvider.class,
        SystemUIBinder.class,
        SystemUIModule.class,
        SystemUIPospModule.class})
public interface SysUIComponentPosp extends SysUIComponent {
    @SysUISingleton
    @Subcomponent.Builder
    interface Builder extends SysUIComponent.Builder {
        SysUIComponentPosp build();
    }

    /**
     * Member injection into the supplied argument.
     */
    void inject(KeyguardSliceProviderPosp keyguardSliceProviderPosp);

    @SysUISingleton
    KeyguardSmartspaceController createKeyguardSmartspaceController();
}
