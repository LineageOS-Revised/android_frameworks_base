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

package com.android.systemui.statusbar.quickactions.transfer.ui.viewmodel

import android.app.Notification
import android.content.Context
import com.android.systemui.ActivityIntentHelper
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.statusbar.NotificationLockscreenUserManager
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.statusbar.quickactions.delivery.domain.DeliveryParser
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings.TRANSFERS
import com.android.systemui.statusbar.quickactions.popups.ui.model.ChipIcon
import com.android.systemui.statusbar.quickactions.popups.ui.model.ColorsModel
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupContentModel
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.NotificationIslandChipViewModel
import com.android.systemui.statusbar.quickactions.transfer.shared.model.TransferChipModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher

/** Downloads, uploads and installs: any notification with a determinate progress bar. */
class TransferPopupChipViewModel
@AssistedInject
constructor(
    @Application context: Context,
    @Background backgroundDispatcher: CoroutineDispatcher,
    notifCollection: CommonNotifCollection,
    activeNotificationsInteractor: ActiveNotificationsInteractor,
    activityStarter: ActivityStarter,
    activityIntentHelper: ActivityIntentHelper,
    lockscreenUserManager: NotificationLockscreenUserManager,
    keyguardStateController: KeyguardStateController,
) :
    NotificationIslandChipViewModel<TransferPopupChipViewModel.Transfer>(
        context,
        backgroundDispatcher,
        notifCollection,
        activeNotificationsInteractor,
        activityStarter,
        activityIntentHelper,
        lockscreenUserManager,
        keyguardStateController,
        TRANSFERS,
        PopupChipId.Transfer,
    ) {

    data class Transfer(val title: String?, val percent: Int)

    override fun parse(entry: NotificationEntry): Transfer? {
        val notification = entry.sbn.notification
        val extras = notification.extras
        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        if (max <= 0 || extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)) {
            return null
        }
        val progress = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        if (progress < 0 || progress >= max) return null
        if (notification.category in EXCLUDED_CATEGORIES ||
            notification.isMediaNotification ||
            notification.isStyle(Notification.CallStyle::class.java) ||
            entry.sbn.packageName == SYSTEMUI_PACKAGE ||
            DeliveryParser.isDeliveryApp(entry.sbn.packageName)
        ) {
            return null
        }
        return Transfer(
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim(),
            percent = (progress.toLong() * 100 / max).toInt(),
        )
    }

    override fun toChip(
        shown: List<ActiveItem<Transfer>>,
        icon: Icon?,
        onOpen: (() -> Unit)?,
    ): PopupChipModel.Shown {
        val active = shown.first().active
        val transfer = shown.first().item.value
        val more = shown.size - 1
        val percentText = "${transfer.percent}%"
        return PopupChipModel.Shown(
            chipId = PopupChipId.Transfer,
            icons = listOfNotNull(icon?.let { ChipIcon(icon = it, onClick = onOpen) }),
            chipText = if (more > 0) "${shown.size} · $percentText" else percentText,
            colors = ColorsModel.DynamicIsland,
            contentDescription =
                listOfNotNull(active.appName, transfer.title, percentText).joinToString(" "),
            popupContent =
                PopupContentModel.Transfer(
                    TransferChipModel(
                        icon = icon,
                        appName = active.appName,
                        title = transfer.title,
                        percent = transfer.percent,
                        moreCount = more,
                        onOpen = onOpen,
                    )
                ),
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(): TransferPopupChipViewModel
    }

    private companion object {
        const val SYSTEMUI_PACKAGE = "com.android.systemui"
        val EXCLUDED_CATEGORIES =
            setOf(
                Notification.CATEGORY_TRANSPORT,
                Notification.CATEGORY_NAVIGATION,
                Notification.CATEGORY_ALARM,
                Notification.CATEGORY_STOPWATCH,
                Notification.CATEGORY_CALL,
            )
    }
}
