/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.statusbar.quickactions.sysmon.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings.SYSTEM_MONITOR
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings.observeDynamicIslandFeatureEnabled
import com.android.systemui.statusbar.quickactions.popups.ui.model.ColorsModel
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupContentModel
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.DynamicIslandChipViewModel
import com.android.systemui.statusbar.quickactions.sysmon.data.SystemMonitorRepository
import com.android.systemui.statusbar.quickactions.sysmon.data.SystemStatsReader
import com.android.systemui.statusbar.quickactions.sysmon.shared.model.SystemMonitorPopupModel
import com.android.systemui.statusbar.quickactions.sysmon.shared.model.SystemStats
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** htop-style system monitor in the dynamic island. Polls only while open and screen is on. */
class SystemMonitorPopupChipViewModel
@AssistedInject
constructor(
    @Application private val context: Context,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    private val repository: SystemMonitorRepository,
    powerInteractor: PowerInteractor,
) : DynamicIslandChipViewModel, ExclusiveActivatable() {
    private val hydrator = Hydrator("SystemMonitorPopupChipViewModel.hydrator")
    private val close: () -> Unit = { repository.setOpen(false) }

    override val chip: PopupChipModel by
        hydrator.hydratedStateOf(
            traceName = "chip",
            initialValue = HIDDEN,
            source =
                combine(
                        observeDynamicIslandFeatureEnabled(context, SYSTEM_MONITOR),
                        repository.isOpen,
                        powerInteractor.isInteractive,
                    ) { enabled, open, on ->
                        enabled && open && on
                    }
                    .distinctUntilChanged()
                    .flatMapLatest { active ->
                        if (active) stats().map(::toChip) else flowOf(HIDDEN)
                    },
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    private fun stats(): Flow<SystemStats> =
        flow {
                val reader = SystemStatsReader(context)
                while (true) {
                    val stats =
                        try {
                            reader.read()
                        } catch (e: RuntimeException) {
                            Log.w(TAG, "Failed to read stats", e)
                            null
                        }
                    if (stats != null) emit(stats)
                    delay(REFRESH_MS)
                }
            }
            .flowOn(backgroundDispatcher)

    private fun toChip(stats: SystemStats): PopupChipModel =
        PopupChipModel.Shown(
            chipId = PopupChipId.SystemMonitor,
            icons = emptyList(),
            chipText = "${stats.cpuLoad ?: "--"}% · ${stats.cpuTemp ?: "--"}°C",
            colors = ColorsModel.DynamicIsland,
            popupContent = PopupContentModel.SystemMonitor(SystemMonitorPopupModel(stats, close)),
        )

    @AssistedFactory
    interface Factory {
        fun create(): SystemMonitorPopupChipViewModel
    }

    private companion object {
        const val TAG = "SystemMonitorChip"
        const val REFRESH_MS = 2000L
        val HIDDEN = PopupChipModel.Hidden(PopupChipId.SystemMonitor)
    }
}
