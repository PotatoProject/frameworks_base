/*
 * Copyright (C) 2021 Benzo Rom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package com.potatoproject.systemui.dagger

import android.content.Context
import android.content.res.Resources
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.theme.ThemeOverlayController
import com.potatoproject.systemui.theme.BootColorsController

import dagger.Binds
import dagger.Module
import dagger.Provides

@Module
abstract class SystemUIPotatoModule {

    @Binds
    @SysUISingleton
    abstract fun bindThemeOverlayController(sysui: BootColorsController): ThemeOverlayController

    @Module
    companion object {
        @Provides
        fun provideResources(context: Context): Resources = context.resources
    }
}
