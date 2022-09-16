/*
 * Copyright (C) 2022 Benzo Rom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package com.potatoproject.systemui.theme

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.UserManager
import android.util.Log
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dump.DumpManager
import com.android.systemui.flags.FeatureFlags
import com.android.systemui.flags.SystemPropertiesHelper
import com.android.systemui.keyguard.WakefulnessLifecycle
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener
import com.android.systemui.statusbar.policy.DeviceProvisionedController
import com.android.systemui.theme.ThemeOverlayApplier
import com.android.systemui.theme.ThemeOverlayController
import com.android.systemui.util.settings.SecureSettings
import java.lang.RuntimeException
import java.util.concurrent.Executor
import javax.inject.Inject

@SysUISingleton
class BootColorsController @Inject constructor(
    private val context: Context,
    broadcastDispatcher: BroadcastDispatcher,
    @Background bgHandler: Handler,
    @Main mainExecutor: Executor,
    @Background bgExecutor: Executor,
    themeOverlayApplier: ThemeOverlayApplier,
    secureSettings: SecureSettings,
    private val systemProperties: SystemPropertiesHelper,
    resources: Resources,
    wallpaperManager: WallpaperManager,
    userManager: UserManager,
    dumpManager: DumpManager,
    deviceProvisionedController: DeviceProvisionedController,
    private val userTracker: UserTracker,
    featureFlags: FeatureFlags,
    wakefulnessLifecycle: WakefulnessLifecycle,
    configurationController: ConfigurationController
) : ThemeOverlayController(
    context, broadcastDispatcher, bgHandler, mainExecutor, bgExecutor,
    themeOverlayApplier, secureSettings, wallpaperManager, userManager,
    deviceProvisionedController, userTracker, dumpManager, featureFlags,
    resources, wakefulnessLifecycle, configurationController
) {
    init {
        with(configurationController) {
            addCallback(object : ConfigurationListener {
                override fun onThemeChanged() {
                    setBootColorSystemProps()
                }
            })
        }
        val themeColors = bootColors
        themeColors.indices.forEach {
            val color = themeColors[it]
            Log.d(TAG, "Boot animation colors ${it.plus(1)}: $color")
        }
    }

    private fun setBootColorSystemProps() {
        if (userTracker.userId == 0) {
            try {
                val themeColors = bootColors
                themeColors.indices.forEach {
                    val color = themeColors[it]
                    systemProperties.set(
                        "persist.bootanim.color${it.plus(1)}",
                        color
                    )
                    Log.d(TAG, "Writing boot animation colors ${it.plus(1)}: $color")
                }
            } catch (ex: RuntimeException) {
                Log.w(TAG, "Cannot set sysprop. Look for 'init' and 'dmesg' logs for more info."
                )
            }
        }
    }

    private val bootColors: IntArray
        get() = intArrayOf(
            context.getColor(android.R.color.system_accent1_400),
            context.getColor(android.R.color.system_accent1_200),
            context.getColor(android.R.color.system_accent1_700),
            context.getColor(android.R.color.system_accent2_900)
        )
}
