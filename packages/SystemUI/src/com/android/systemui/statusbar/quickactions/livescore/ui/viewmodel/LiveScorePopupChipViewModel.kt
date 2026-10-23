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

package com.android.systemui.statusbar.quickactions.livescore.ui.viewmodel

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import com.android.systemui.ActivityIntentHelper
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.statusbar.NotificationLockscreenUserManager
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import com.android.systemui.statusbar.notification.shared.ActiveNotificationModel
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.statusbar.quickactions.livescore.domain.LiveScoreInput
import com.android.systemui.statusbar.quickactions.livescore.domain.LiveScoreParser
import com.android.systemui.statusbar.quickactions.livescore.domain.ParsedLiveScore
import com.android.systemui.statusbar.quickactions.livescore.shared.model.LiveScoreChipModel
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings.LIVE_SCORES
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings.observeDynamicIslandFeatureEnabled
import com.android.systemui.statusbar.quickactions.popups.shared.toActivityLaunchAction
import com.android.systemui.statusbar.quickactions.popups.ui.model.ChipIcon
import com.android.systemui.statusbar.quickactions.popups.ui.model.ColorsModel
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupContentModel
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.DynamicIslandChipViewModel
import com.android.systemui.util.time.SystemClock
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

/** One-off score alerts (goal, full time) stay in the island for this long. */
private const val SCORE_ALERT_DURATION_MS = 10 * 60 * 1000L

/**
 * ViewModel backing live scores inside the dynamic island.
 *
 * Scores are read from sports app notifications by [LiveScoreParser]. Each notification is parsed
 * once when it is posted or updated, so other notification traffic costs almost nothing here.
 */
class LiveScorePopupChipViewModel
@AssistedInject
constructor(
    @Application private val context: Context,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    private val notifCollection: CommonNotifCollection,
    private val activeNotificationsInteractor: ActiveNotificationsInteractor,
    private val systemClock: SystemClock,
    private val activityStarter: ActivityStarter,
    private val activityIntentHelper: ActivityIntentHelper,
    private val lockscreenUserManager: NotificationLockscreenUserManager,
    private val keyguardStateController: KeyguardStateController,
) : DynamicIslandChipViewModel, ExclusiveActivatable() {
    private val hydrator = Hydrator("LiveScorePopupChipViewModel.hydrator")

    // Kept so an unchanged score produces an equal model and the island doesn't recompose.
    private var cachedIcon: CachedIcon? = null
    private var cachedOpenAction: CachedOpenAction? = null

    /** Parsed scores keyed by notification key. Listener calls arrive on the main thread. */
    private val parsedScores: Flow<Map<String, ScoredNotification>> =
        callbackFlow {
                val scores = HashMap<String, ScoredNotification>()

                fun update(entry: NotificationEntry): Boolean {
                    val scored = entry.toScoredNotification()
                    val previous =
                        if (scored == null) scores.remove(entry.key)
                        else scores.put(entry.key, scored)
                    return previous != scored
                }

                fun publish() {
                    trySend(scores.toMap())
                }

                val listener =
                    object : NotifCollectionListener {
                        override fun onEntryAdded(entry: NotificationEntry) {
                            if (update(entry)) publish()
                        }

                        override fun onEntryUpdated(entry: NotificationEntry) {
                            if (update(entry)) publish()
                        }

                        override fun onEntryRemoved(entry: NotificationEntry, reason: Int) {
                            if (scores.remove(entry.key) != null) publish()
                        }
                    }

                notifCollection.addCollectionListener(listener)
                notifCollection.allNotifs.forEach { update(it) }
                publish()
                awaitClose { notifCollection.removeCollectionListener(listener) }
            }
            .conflate()

    override val chip: PopupChipModel by
        hydrator.hydratedStateOf(
            traceName = "chip",
            initialValue = HIDDEN,
            source =
                observeDynamicIslandFeatureEnabled(context, LIVE_SCORES)
                    .distinctUntilChanged()
                    .flatMapLatest { enabled ->
                        if (enabled) liveScoreChip() else flowOf(HIDDEN)
                    }
                    .distinctUntilChanged(),
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    private fun liveScoreChip(): Flow<PopupChipModel> =
        combine(
                parsedScores,
                activeNotificationsInteractor.allRepresentativeNotifications,
                ::Pair,
            )
            .transformLatest { (scores, activeNotifications) ->
                if (scores.isEmpty()) {
                    emit(HIDDEN)
                    return@transformLatest
                }
                // Score alerts expire after a while, so check again when the next one does.
                while (true) {
                    val now = systemClock.currentTimeMillis()
                    emit(toPopupChipModel(pickScore(scores, activeNotifications, now)))
                    val nextExpiry =
                        scores.values
                            .filter { !it.score.isLive }
                            .map { it.postTimeMs + SCORE_ALERT_DURATION_MS }
                            .filter { it > now }
                            .minOrNull() ?: break
                    delay(nextExpiry - now)
                }
            }

    /** Picks the score to show: live ones first, then the most recently updated. */
    private fun pickScore(
        scores: Map<String, ScoredNotification>,
        activeNotifications: Map<String, ActiveNotificationModel>,
        now: Long,
    ): Pick? {
        return scores.entries
            .mapNotNull { (key, scored) ->
                // Only notifications that are actually shown (right user, not dismissed).
                val active = activeNotifications[key]?.takeUnless { it.isRowDismissed }
                    ?: return@mapNotNull null
                val isFresh =
                    scored.score.isLive || now - scored.postTimeMs < SCORE_ALERT_DURATION_MS
                if (isFresh) Pick(scored, active) else null
            }
            .maxWithOrNull(
                compareBy<Pick> { it.scored.score.isLive }
                    .thenBy { it.active.promotedContent != null }
                    .thenBy { it.scored.postTimeMs }
            )
    }

    private suspend fun toPopupChipModel(pick: Pick?): PopupChipModel {
        pick ?: return HIDDEN
        val active = pick.active
        val score = pick.scored.score
        val onOpen = openAction(active.key, pick.scored.contentIntent)
        val model =
            LiveScoreChipModel(
                key = active.key,
                icon = icon(active),
                appName = active.appName,
                title = score.title,
                score = score.score,
                subtitle = score.subtitle,
                onOpen = onOpen,
                isLive = score.isLive,
            )

        return PopupChipModel.Shown(
            chipId = PopupChipId.LiveScore,
            icons = listOfNotNull(model.icon?.let { ChipIcon(icon = it, onClick = onOpen) }),
            chipText = collapsedText(score, active.appName),
            colors = ColorsModel.DynamicIsland,
            contentDescription =
                listOfNotNull(score.title, score.score, score.subtitle).joinToString(" "),
            popupContent = PopupContentModel.LiveScore(model),
        )
    }

    /** "Arsenal 2–1 Chelsea" when it fits, otherwise "2–1 • Arsenal vs Chelsea". */
    private fun collapsedText(score: ParsedLiveScore, appName: String): String {
        if (score.compactText.any(Char::isLetter)) {
            return score.compactText
        }
        val title = score.title?.takeUnless { it.isBlank() } ?: appName
        return listOf(score.compactText, title).filter { it.isNotBlank() }.joinToString(" • ")
    }

    /** The app's launcher icon (readable on light and dark), falling back to the small icon. */
    private suspend fun icon(active: ActiveNotificationModel): Icon? {
        val smallIcon = active.statusBarIcon
        cachedIcon
            ?.takeIf { it.packageName == active.packageName && it.smallIcon === smallIcon }
            ?.let { return it.icon }

        val contentDescription = ContentDescription.Loaded(active.appName)
        val drawable =
            withContext(backgroundDispatcher) {
                try {
                    context.packageManager.getApplicationIcon(active.packageName)
                } catch (e: PackageManager.NameNotFoundException) {
                    smallIcon?.loadDrawable(context)
                }
            }
        val icon = drawable?.let { Icon.Loaded(it, contentDescription) }
        cachedIcon = CachedIcon(active.packageName, smallIcon, icon)
        return icon
    }

    private fun openAction(key: String, contentIntent: PendingIntent?): (() -> Unit)? {
        cachedOpenAction
            ?.takeIf { it.key == key && it.contentIntent == contentIntent }
            ?.let { return it.action }
        val action =
            contentIntent.toActivityLaunchAction(
                activityStarter = activityStarter,
                activityIntentHelper = activityIntentHelper,
                lockscreenUserManager = lockscreenUserManager,
                keyguardStateController = keyguardStateController,
            )
        cachedOpenAction = CachedOpenAction(key, contentIntent, action)
        return action
    }

    private data class ScoredNotification(
        val score: ParsedLiveScore,
        val postTimeMs: Long,
        val contentIntent: PendingIntent?,
    )

    private class Pick(val scored: ScoredNotification, val active: ActiveNotificationModel)

    private class CachedIcon(
        val packageName: String,
        val smallIcon: android.graphics.drawable.Icon?,
        val icon: Icon?,
    )

    private class CachedOpenAction(
        val key: String,
        val contentIntent: PendingIntent?,
        val action: (() -> Unit)?,
    )

    private fun NotificationEntry.toScoredNotification(): ScoredNotification? {
        val notification = sbn.notification ?: return null
        if (notification.isGroupSummary) {
            return null
        }
        val extras = notification.extras
        val hasDeterminateProgress =
            extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 &&
                !extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val input =
            LiveScoreInput(
                packageName = sbn.packageName,
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
                bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                shortCriticalText = notification.shortCriticalText,
                category = notification.category,
                isOngoing = notification.isOngoingEvent,
                isPromoted = notification.isPromotedOngoing,
                isExcludedStyle =
                    hasDeterminateProgress ||
                        notification.isMediaNotification ||
                        notification.isStyle(Notification.CallStyle::class.java),
            )
        val score = LiveScoreParser.parse(input) ?: return null
        return ScoredNotification(
            score = score,
            postTimeMs = sbn.postTime,
            contentIntent = notification.contentIntent,
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(): LiveScorePopupChipViewModel
    }

    private companion object {
        val HIDDEN = PopupChipModel.Hidden(PopupChipId.LiveScore)
    }
}
