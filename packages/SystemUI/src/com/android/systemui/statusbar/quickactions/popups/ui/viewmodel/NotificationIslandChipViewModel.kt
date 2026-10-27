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

package com.android.systemui.statusbar.quickactions.popups.ui.viewmodel

import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.getValue
import com.android.systemui.ActivityIntentHelper
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
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
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings.observeDynamicIslandFeatureEnabled
import com.android.systemui.statusbar.quickactions.popups.shared.toActivityLaunchAction
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupChipModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

/**
 * Island chip fed by notifications. Each notification is parsed once when posted or updated and
 * dropped when removed.
 */
abstract class NotificationIslandChipViewModel<T : Any>(
    private val context: Context,
    private val backgroundDispatcher: CoroutineDispatcher,
    private val notifCollection: CommonNotifCollection,
    private val activeNotificationsInteractor: ActiveNotificationsInteractor,
    private val activityStarter: ActivityStarter,
    private val activityIntentHelper: ActivityIntentHelper,
    private val lockscreenUserManager: NotificationLockscreenUserManager,
    private val keyguardStateController: KeyguardStateController,
    private val settingKey: String,
    private val chipId: PopupChipId,
) : DynamicIslandChipViewModel, ExclusiveActivatable() {

    data class Item<T>(
        val value: T,
        val postTimeMs: Long,
        val contentIntent: PendingIntent?,
    )

    class ActiveItem<T>(val item: Item<T>, val active: ActiveNotificationModel)

    private val hidden = PopupChipModel.Hidden(chipId)
    private val hydrator = Hydrator("${javaClass.simpleName}.hydrator")

    private var cachedIconKey: Pair<String, Any?>? = null
    private var cachedIcon: Icon? = null
    private var cachedOpenKey: Pair<String, PendingIntent?>? = null
    private var cachedOpen: (() -> Unit)? = null

    protected abstract fun parse(entry: NotificationEntry): T?

    /** [shown] is sorted newest first and never empty. */
    protected abstract fun toChip(
        shown: List<ActiveItem<T>>,
        icon: Icon?,
        onOpen: (() -> Unit)?,
    ): PopupChipModel.Shown

    private val parsed: Flow<Map<String, Item<T>>> =
        callbackFlow {
                val items = HashMap<String, Item<T>>()

                fun update(entry: NotificationEntry): Boolean {
                    val value =
                        if (entry.sbn.notification?.isGroupSummary == false) {
                            try {
                                parse(entry)
                            } catch (e: RuntimeException) {
                                Log.w(TAG, "Failed to parse ${entry.key}", e)
                                null
                            }
                        } else null
                    val previous = items[entry.key]
                    if (value == null) {
                        return items.remove(entry.key) != null
                    }
                    if (previous?.value == value) return false
                    items[entry.key] =
                        Item(value, entry.sbn.postTime, entry.sbn.notification.contentIntent)
                    return true
                }

                val listener =
                    object : NotifCollectionListener {
                        override fun onEntryAdded(entry: NotificationEntry) {
                            if (update(entry)) trySend(items.toMap())
                        }

                        override fun onEntryUpdated(entry: NotificationEntry) {
                            if (update(entry)) trySend(items.toMap())
                        }

                        override fun onEntryRemoved(entry: NotificationEntry, reason: Int) {
                            if (items.remove(entry.key) != null) trySend(items.toMap())
                        }
                    }

                notifCollection.addCollectionListener(listener)
                notifCollection.allNotifs.forEach { update(it) }
                trySend(items.toMap())
                awaitClose { notifCollection.removeCollectionListener(listener) }
            }
            .conflate()

    override val chip: PopupChipModel by
        hydrator.hydratedStateOf(
            traceName = "chip",
            initialValue = hidden,
            source =
                observeDynamicIslandFeatureEnabled(context, settingKey)
                    .distinctUntilChanged()
                    .flatMapLatest { enabled -> if (enabled) chipFlow() else flowOf(hidden) }
                    .distinctUntilChanged(),
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    private fun chipFlow(): Flow<PopupChipModel> =
        parsed.flatMapLatest { items ->
            if (items.isEmpty()) {
                flowOf(hidden)
            } else {
                activeNotificationsInteractor.allRepresentativeNotifications.mapLatest { active ->
                    val shown =
                        items
                            .mapNotNull { (key, item) ->
                                active[key]
                                    ?.takeUnless { it.isRowDismissed }
                                    ?.let { ActiveItem(item, it) }
                            }
                            .sortedByDescending { it.item.postTimeMs }
                    if (shown.isEmpty()) {
                        hidden
                    } else {
                        val top = shown.first()
                        toChip(shown, icon(top.active), openAction(top))
                    }
                }
            }
        }

    private suspend fun icon(active: ActiveNotificationModel): Icon? {
        val key = active.packageName to active.statusBarIcon
        if (key == cachedIconKey) return cachedIcon
        val drawable =
            withContext(backgroundDispatcher) {
                try {
                    context.packageManager.getApplicationIcon(active.packageName)
                } catch (e: PackageManager.NameNotFoundException) {
                    active.statusBarIcon?.loadDrawable(context)
                }
            }
        cachedIcon = drawable?.let { Icon.Loaded(it, ContentDescription.Loaded(active.appName)) }
        cachedIconKey = key
        return cachedIcon
    }

    private fun openAction(shown: ActiveItem<T>): (() -> Unit)? {
        val key = shown.active.key to shown.item.contentIntent
        if (key == cachedOpenKey) return cachedOpen
        cachedOpen =
            shown.item.contentIntent.toActivityLaunchAction(
                activityStarter = activityStarter,
                activityIntentHelper = activityIntentHelper,
                lockscreenUserManager = lockscreenUserManager,
                keyguardStateController = keyguardStateController,
            )
        cachedOpenKey = key
        return cachedOpen
    }

    private companion object {
        const val TAG = "NotificationIslandChip"
    }
}
