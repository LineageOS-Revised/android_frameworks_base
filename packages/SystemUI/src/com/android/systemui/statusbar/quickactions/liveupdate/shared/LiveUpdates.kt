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

package com.android.systemui.statusbar.quickactions.liveupdate.shared

import android.content.Context
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.Style
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings.LIVE_UPDATES
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings.readDynamicIslandFeatureEnabled

/** Whether the island's Live Update chip shows this notification, so other chips should skip it. */
fun NotificationEntry.isShownAsLiveUpdate(context: Context): Boolean {
    val style = promotedNotificationContentModels?.privateVersion?.style ?: return false
    if (style == Style.Call || style == Style.CollapsedCall) return false
    return context.contentResolver.readDynamicIslandFeatureEnabled(LIVE_UPDATES)
}
