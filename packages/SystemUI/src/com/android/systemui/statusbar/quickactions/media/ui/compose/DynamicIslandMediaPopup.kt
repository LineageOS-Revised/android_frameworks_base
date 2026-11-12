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

package com.android.systemui.statusbar.quickactions.media.ui.compose

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon as UiIcon
import com.android.systemui.common.ui.compose.Icon as UiIconView
import com.android.systemui.media.controls.shared.model.MediaAction
import com.android.systemui.res.R
import com.android.systemui.statusbar.quickactions.media.shared.model.MediaControlChipModel
import kotlinx.coroutines.delay

private val PopupShape = RoundedCornerShape(34.dp)
private val ArtworkSize = 72.dp
private val ArtworkSpacing = 12.dp

/** Expanded media controls for the centered dynamic island. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DynamicIslandMediaPopup(
    model: MediaControlChipModel,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = PopupShape,
        shadowElevation = 12.dp,
        modifier = modifier.widthIn(min = 320.dp, max = 400.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
        ) {
            Row(
                modifier =
                    Modifier.clickable(enabled = model.openApp != null) {
                        model.openApp?.invoke()
                    },
                horizontalArrangement = Arrangement.spacedBy(ArtworkSpacing),
            ) {
                MediaIcon(
                    icon =
                        model.artworkIcon
                            ?: model.appIcon
                            ?: UiIcon.Resource(com.android.internal.R.drawable.ic_audio_media, null),
                    size = ArtworkSize,
                    corner = 12.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = model.songName?.toString().orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = model.artistName?.toString().orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalContentColor.current.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        model.appIcon?.let { MediaIcon(icon = it, size = 24.dp, corner = 6.dp) }
                    }
                    if (model.durationMs > 0L) {
                        MediaProgressSection(
                            positionMs = model.positionMs,
                            durationMs = model.durationMs,
                            isPlaying = model.isPlaying,
                            canBeScrubbed = model.canBeScrubbed,
                            onSeekTo = model.seekTo,
                        )
                    }
                }
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth().padding(start = ArtworkSize + ArtworkSpacing),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MediaActionButton(action = model.previousAction)
                MediaActionButton(action = model.playOrPause)
                MediaActionButton(action = model.nextAction)
                IconButton(
                    onClick = { model.openOutputSwitcher?.invoke() },
                    shapes = IconButtonDefaults.shapes(),
                    enabled = model.openOutputSwitcher != null,
                ) {
                    UiIconView(
                        icon =
                            UiIcon.Resource(
                                R.drawable.ic_media_home_devices,
                                ContentDescription.Resource(R.string.controls_media_output_chip),
                            ),
                        modifier = Modifier.size(24.dp),
                        tint = LocalContentColor.current,
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaIcon(icon: UiIcon, size: Dp, corner: Dp) {
    UiIconView(
        icon = icon,
        modifier = Modifier.size(size).clip(RoundedCornerShape(corner)),
        tint = Color.Unspecified,
    )
}

@Composable
private fun MediaProgressSection(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    canBeScrubbed: Boolean,
    onSeekTo: ((Long) -> Unit)?,
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var displayedPositionMs by remember { mutableStateOf(positionMs.coerceIn(0L, durationMs)) }
    val currentSeekAction by rememberUpdatedState(onSeekTo)

    LaunchedEffect(positionMs, durationMs, isPlaying, isScrubbing) {
        if (isScrubbing) {
            return@LaunchedEffect
        }

        displayedPositionMs = positionMs.coerceIn(0L, durationMs)

        if (!isPlaying || durationMs <= 0L) {
            return@LaunchedEffect
        }

        val startWallClockMs = System.currentTimeMillis()
        val startPositionMs = displayedPositionMs
        while (!isScrubbing) {
            delay(16L)
            val elapsedMs = System.currentTimeMillis() - startWallClockMs
            displayedPositionMs = (startPositionMs + elapsedMs).coerceAtMost(durationMs)
            if (displayedPositionMs >= durationMs) {
                break
            }
        }
    }

    val contentColor = LocalContentColor.current
    Column {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Slider(
                value = displayedPositionMs.coerceIn(0L, durationMs).toFloat(),
                onValueChange = {
                    isScrubbing = true
                    displayedPositionMs = it.toLong()
                },
                onValueChangeFinished = {
                    currentSeekAction?.invoke(displayedPositionMs)
                    isScrubbing = false
                },
                enabled = canBeScrubbed,
                valueRange = 0f..durationMs.toFloat(),
                thumb = {},
                track = { state ->
                    LinearProgressIndicator(
                        progress = { state.coercedValueAsFraction },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = contentColor,
                        trackColor = contentColor.copy(alpha = 0.2f),
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                },
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatElapsedTime(displayedPositionMs),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.6f),
            )
            Text(
                text = formatElapsedTime(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.6f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaActionButton(action: MediaAction?) {
    val icon = action?.icon
    if (action == null || icon == null) {
        Spacer(modifier = Modifier.size(IconButtonDefaults.smallContainerSize()))
        return
    }
    IconButton(
        onClick = { action.action?.run() },
        shapes = IconButtonDefaults.shapes(),
        enabled = action.action != null,
    ) {
        UiIconView(
            icon =
                UiIcon.Loaded(
                    icon,
                    action.contentDescription?.toString()?.let { ContentDescription.Loaded(it) },
                ),
            modifier = Modifier.size(24.dp),
            tint = LocalContentColor.current,
        )
    }
}

private fun formatElapsedTime(milliseconds: Long): String {
    return DateUtils.formatElapsedTime(milliseconds / DateUtils.SECOND_IN_MILLIS)
}
