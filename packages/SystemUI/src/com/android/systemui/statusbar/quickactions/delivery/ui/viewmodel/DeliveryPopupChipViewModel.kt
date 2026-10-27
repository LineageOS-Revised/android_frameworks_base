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

package com.android.systemui.statusbar.quickactions.delivery.ui.viewmodel

import android.app.Notification
import android.content.Context
import com.android.systemui.ActivityIntentHelper
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.statusbar.NotificationLockscreenUserManager
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.statusbar.quickactions.delivery.domain.DeliveryParser
import com.android.systemui.statusbar.quickactions.delivery.domain.ParsedDelivery
import com.android.systemui.statusbar.quickactions.delivery.shared.model.DeliveryChipModel
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings.DELIVERY
import com.android.systemui.statusbar.quickactions.popups.ui.model.ChipIcon
import com.android.systemui.statusbar.quickactions.popups.ui.model.ColorsModel
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupContentModel
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.NotificationIslandChipViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher

class DeliveryPopupChipViewModel
@AssistedInject
constructor(
    @Application private val context: Context,
    @Background backgroundDispatcher: CoroutineDispatcher,
    notifCollection: CommonNotifCollection,
    activeNotificationsInteractor: ActiveNotificationsInteractor,
    activityStarter: ActivityStarter,
    activityIntentHelper: ActivityIntentHelper,
    lockscreenUserManager: NotificationLockscreenUserManager,
    keyguardStateController: KeyguardStateController,
) :
    NotificationIslandChipViewModel<ParsedDelivery>(
        context,
        backgroundDispatcher,
        notifCollection,
        activeNotificationsInteractor,
        activityStarter,
        activityIntentHelper,
        lockscreenUserManager,
        keyguardStateController,
        DELIVERY,
        PopupChipId.Delivery,
    ) {

    override fun parse(entry: NotificationEntry): ParsedDelivery? {
        val packageName = entry.sbn.packageName
        if (!DeliveryParser.isDeliveryApp(packageName)) return null
        val notification = entry.sbn.notification
        if (!notification.isOngoingEvent && !notification.isPromotedOngoing) return null
        if (notification.isMediaNotification ||
            notification.isStyle(Notification.CallStyle::class.java)
        ) {
            return null
        }
        val extras = notification.extras
        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progress =
            if (max > 0 && !extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)) {
                (extras.getInt(Notification.EXTRA_PROGRESS, 0).toFloat() / max).coerceIn(0f, 1f)
            } else null
        return DeliveryParser.parse(
            packageName = packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            shortCriticalText = notification.shortCriticalText,
            progress = progress,
        )
    }

    override fun toChip(
        shown: List<ActiveItem<ParsedDelivery>>,
        icon: Icon?,
        onOpen: (() -> Unit)?,
    ): PopupChipModel.Shown {
        val active = shown.first().active
        val order = shown.first().item.value
        val model =
            DeliveryChipModel(
                icon = icon,
                appName = active.appName,
                title = order.title,
                eta = order.eta,
                stage = order.stage,
                progress = order.progress,
                subtitle = order.subtitle,
                onOpen = onOpen,
            )
        val stageText = order.stage?.let { context.getString(STAGE_LABELS[it]) }
        return PopupChipModel.Shown(
            chipId = PopupChipId.Delivery,
            icons = listOfNotNull(icon?.let { ChipIcon(icon = it, onClick = onOpen) }),
            chipText = order.eta ?: stageText ?: active.appName,
            colors = ColorsModel.DynamicIsland,
            contentDescription =
                listOfNotNull(active.appName, order.title, order.eta).joinToString(" "),
            popupContent = PopupContentModel.Delivery(model),
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(): DeliveryPopupChipViewModel
    }

    companion object {
        val STAGE_LABELS =
            intArrayOf(
                R.string.dynamic_island_delivery_placed,
                R.string.dynamic_island_delivery_preparing,
                R.string.dynamic_island_delivery_on_the_way,
                R.string.dynamic_island_delivery_arriving,
            )
    }
}
