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

package com.android.systemui.statusbar.quickactions.liveupdate.ui.viewmodel

import android.app.Notification
import android.content.Context
import android.os.SystemClock
import com.android.systemui.ActivityIntentHelper
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.statusbar.NotificationLockscreenUserManager
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.Style
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.When
import com.android.systemui.statusbar.notification.shared.Metric
import com.android.systemui.statusbar.notification.shared.NotificationChipFromCompactContent
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.statusbar.quickactions.liveupdate.shared.model.LiveUpdate
import com.android.systemui.statusbar.quickactions.liveupdate.shared.model.LiveUpdatePopupModel
import com.android.systemui.statusbar.quickactions.liveupdate.shared.model.LiveUpdateProgress
import com.android.systemui.statusbar.quickactions.liveupdate.shared.model.LiveUpdateTimer
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings.LIVE_UPDATES
import com.android.systemui.statusbar.quickactions.popups.ui.model.ChipIcon
import com.android.systemui.statusbar.quickactions.popups.ui.model.ColorsModel
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupContentModel
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.NotificationIslandChipViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Live Updates (promoted ongoing notifications) shown in the island instead of the stock status
 * bar chip. Calls keep the stock call chip.
 */
class LiveUpdatePopupChipViewModel
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
    NotificationIslandChipViewModel<LiveUpdate>(
        context,
        backgroundDispatcher,
        notifCollection,
        activeNotificationsInteractor,
        activityStarter,
        activityIntentHelper,
        lockscreenUserManager,
        keyguardStateController,
        LIVE_UPDATES,
        PopupChipId.LiveUpdate,
        skipLiveUpdates = false,
    ) {

    override fun parse(entry: NotificationEntry): LiveUpdate? {
        val content = entry.promotedNotificationContentModels?.privateVersion ?: return null
        if (content.style == Style.Call || content.style == Style.CollapsedCall) return null

        var shortText: String? = null
        var timer: LiveUpdateTimer? = null
        if (NotificationChipFromCompactContent.isEnabled) {
            when (
                val value =
                    (content.compactContent as? Notification.ResolvedBasicCompactContent)?.text
            ) {
                null -> {}
                is Notification.Metric.TimeDifference -> timer = value.toTimer()
                else -> shortText = value.toValueString(context).textVariants.firstOrNull()
            }
        } else {
            when (val metric = content.metrics?.firstOrNull()) {
                is Metric.Text -> shortText = metric.textVariants.firstOrNull()?.toString()
                is Metric.TimeDifference -> timer = metric.toTimer()
                null -> {}
            }
            shortText = content.shortCriticalText ?: shortText
            if (timer == null) {
                timer =
                    (content.time as? When.Chronometer)?.let {
                        LiveUpdateTimer(it.elapsedRealtimeMillis, it.isCountDown)
                    }
            }
        }

        return LiveUpdate(
            title = content.title?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            text = content.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            shortText = shortText?.trim()?.takeIf { it.isNotEmpty() },
            timer = timer,
            progress = content.progress(),
        )
    }

    override fun toChip(
        shown: List<NotificationIslandChipViewModel.ActiveItem<LiveUpdate>>,
        icon: Icon?,
        onOpen: (() -> Unit)?,
    ): PopupChipModel.Shown {
        val active = shown.first().active
        val update = shown.first().item.value
        val more = shown.size - 1
        // A running timer is drawn by the chip itself so it can tick.
        val text =
            update.shortText
                ?: update.progress?.takeUnless { it.isIndeterminate }?.let { "${it.percent}%" }
                ?: update.title.takeIf { update.timer == null }
        return PopupChipModel.Shown(
            chipId = PopupChipId.LiveUpdate,
            icons = listOfNotNull(icon?.let { ChipIcon(icon = it, onClick = onOpen) }),
            chipText = text?.let { if (more > 0) "${shown.size} · $it" else it },
            colors = ColorsModel.DynamicIsland,
            contentDescription =
                listOfNotNull(active.appName, update.title, update.shortText).joinToString(" "),
            popupContent =
                PopupContentModel.LiveUpdate(
                    LiveUpdatePopupModel(
                        icon = icon,
                        appName = active.appName,
                        update = update,
                        moreCount = more,
                        onOpen = onOpen,
                    )
                ),
        )
    }

    private fun PromotedNotificationContentModel.progress(): LiveUpdateProgress? {
        newProgress?.let {
            val max = it.progressMax
            if (it.isIndeterminate) return LiveUpdateProgress(0, isIndeterminate = true)
            if (max > 0) return LiveUpdateProgress(percent(it.progress, max), false)
        }
        oldProgress?.let {
            if (it.isIndeterminate) return LiveUpdateProgress(0, isIndeterminate = true)
            if (it.max > 0) return LiveUpdateProgress(percent(it.progress, it.max), false)
        }
        return null
    }

    private fun percent(progress: Int, max: Int): Int =
        (progress.toLong() * 100 / max).toInt().coerceIn(0, 100)

    private fun Notification.Metric.TimeDifference.toTimer(): LiveUpdateTimer? {
        if (pausedDuration != null) return null
        val base =
            zeroElapsedRealtime
                ?: zeroTime?.toEpochMilli()?.let { it.toElapsedRealtime() }
                ?: return null
        return LiveUpdateTimer(base, isCountDown = isTimer)
    }

    private fun Metric.TimeDifference.toTimer(): LiveUpdateTimer? =
        when (this) {
            is Metric.TimeDifference.ElapsedRealtime ->
                LiveUpdateTimer(zeroElapsedRealtime, isCountDown = isTimer)
            is Metric.TimeDifference.Instant ->
                LiveUpdateTimer(zeroTime.toEpochMilli().toElapsedRealtime(), isCountDown = isTimer)
            is Metric.TimeDifference.Paused -> null
        }

    private fun Long.toElapsedRealtime(): Long =
        this + (SystemClock.elapsedRealtime() - System.currentTimeMillis())

    @AssistedFactory
    interface Factory {
        fun create(): LiveUpdatePopupChipViewModel
    }
}
