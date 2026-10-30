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

package com.android.systemui.statusbar.quickactions.liveupdate.shared.model

import com.android.systemui.common.shared.model.Icon

/** What an app's Live Update (promoted ongoing notification) shows in the island. */
data class LiveUpdate(
    val title: String?,
    val text: String?,
    val shortText: String?,
    val timer: LiveUpdateTimer?,
    val progress: LiveUpdateProgress?,
)

data class LiveUpdateTimer(val baseElapsedRealtimeMs: Long, val isCountDown: Boolean)

data class LiveUpdateProgress(val percent: Int, val isIndeterminate: Boolean)

data class LiveUpdatePopupModel(
    val icon: Icon?,
    val appName: String,
    val update: LiveUpdate,
    val moreCount: Int,
    val onOpen: (() -> Unit)?,
)
