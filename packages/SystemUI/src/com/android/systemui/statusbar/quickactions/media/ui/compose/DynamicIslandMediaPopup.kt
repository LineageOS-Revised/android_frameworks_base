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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonDefaults.IconButtonWidthOption
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon as UiIcon
import com.android.systemui.common.ui.compose.Icon as UiIconView
import com.android.systemui.media.controls.shared.model.MediaAction
import com.android.systemui.res.R
import com.android.systemui.statusbar.quickactions.media.shared.model.MediaControlChipModel
import kotlin.math.max
import kotlinx.coroutines.delay

private val PopupShape = RoundedCornerShape(34.dp)

/** Expanded media controls for the centered dynamic island. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DynamicIslandMediaPopup(
    model: MediaControlChipModel,
    modifier: Modifier = Modifier,
) {
    val scheme = model.colorScheme
    val artwork = model.artworkIcon?.takeIf { it != model.appIcon } as? UiIcon.Loaded
    val tinted = scheme != null || artwork != null
    val accent by
        animateColorAsState(scheme?.primary ?: MaterialTheme.colorScheme.primaryFixed)
    val onAccent by
        animateColorAsState(scheme?.onPrimary ?: MaterialTheme.colorScheme.onPrimaryFixed)
    val scrim by animateColorAsState(scheme?.background ?: Color.Black)
    Surface(
        color = if (scheme != null) scrim else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (tinted) Color.White else MaterialTheme.colorScheme.onSurface,
        shape = PopupShape,
        shadowElevation = 12.dp,
        modifier = modifier.widthIn(min = 320.dp, max = 400.dp),
    ) {
        Box {
            Crossfade(targetState = artwork, modifier = Modifier.matchParentSize()) { art ->
                if (art != null) {
                    Image(
                        painter = rememberDrawablePainter(art.drawable),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier.fillMaxSize().drawWithContent {
                                drawContent()
                                drawRect(
                                    Brush.radialGradient(
                                        0f to scrim.copy(alpha = 0.65f),
                                        1f to scrim.copy(alpha = 0.8f),
                                        center = size.center,
                                        radius = max(size.width, size.height) / 2,
                                    )
                                )
                            },
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier =
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = model.openApp != null) {
                                    model.openApp?.invoke()
                                },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        model.appIcon?.let {
                            UiIconView(
                                icon = it,
                                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)),
                                tint = Color.Unspecified,
                            )
                        }
                        Text(
                            text = model.appName.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = LocalContentColor.current.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { model.openOutputSwitcher?.invoke() },
                        shapes = IconButtonDefaults.shapes(),
                        enabled = model.openOutputSwitcher != null,
                        colors = tonalColors(),
                        modifier =
                            Modifier.size(
                                IconButtonDefaults.smallContainerSize(IconButtonWidthOption.Wide)
                            ),
                    ) {
                        UiIconView(
                            icon =
                                UiIcon.Resource(
                                    R.drawable.ic_media_home_devices,
                                    ContentDescription.Resource(
                                        R.string.controls_media_output_chip
                                    ),
                                ),
                            modifier = Modifier.size(20.dp),
                            tint = LocalContentColor.current,
                        )
                    }
                }
                Column {
                    Text(
                        text = model.songName?.toString().orEmpty(),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = model.artistName?.toString().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (model.durationMs > 0L) {
                    MediaProgressSection(
                        positionMs = model.positionMs,
                        durationMs = model.durationMs,
                        isPlaying = model.isPlaying,
                        canBeScrubbed = model.canBeScrubbed,
                        onSeekTo = model.seekTo,
                        accent = accent,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediaActionButton(action = model.previousAction)
                    PlayPauseButton(
                        action = model.playOrPause,
                        isPlaying = model.isPlaying,
                        accent = accent,
                        onAccent = onAccent,
                    )
                    MediaActionButton(action = model.nextAction)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaProgressSection(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    canBeScrubbed: Boolean,
    onSeekTo: ((Long) -> Unit)?,
    accent: Color,
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var displayedPositionMs by remember { mutableStateOf(positionMs.coerceIn(0L, durationMs)) }
    val currentSeekAction by rememberUpdatedState(onSeekTo)
    val amplitude by animateFloatAsState(if (isPlaying && !isScrubbing) 1f else 0f)

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
                thumb = {
                    Box(
                        Modifier.size(width = 4.dp, height = 16.dp)
                            .background(accent, RoundedCornerShape(2.dp))
                    )
                },
                track = { state ->
                    LinearWavyProgressIndicator(
                        progress = { state.coercedValueAsFraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = accent,
                        trackColor = contentColor.copy(alpha = 0.24f),
                        stopSize = 0.dp,
                        amplitude = { amplitude },
                    )
                },
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val timeStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
            Text(
                text = formatElapsedTime(displayedPositionMs),
                style = timeStyle,
                color = contentColor.copy(alpha = 0.7f),
            )
            Text(
                text = formatElapsedTime(durationMs),
                style = timeStyle,
                color = contentColor.copy(alpha = 0.7f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayPauseButton(
    action: MediaAction?,
    isPlaying: Boolean,
    accent: Color,
    onAccent: Color,
) {
    val size = IconButtonDefaults.mediumContainerSize(IconButtonWidthOption.Wide)
    val icon = action?.icon
    if (action == null || icon == null) {
        Spacer(modifier = Modifier.size(size))
        return
    }
    FilledIconToggleButton(
        checked = isPlaying,
        onCheckedChange = { action.action?.run() },
        shapes = IconButtonDefaults.toggleableShapes(),
        enabled = action.action != null,
        colors =
            IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = accent,
                contentColor = onAccent,
                checkedContainerColor = accent,
                checkedContentColor = onAccent,
            ),
        modifier = Modifier.size(size),
    ) {
        ActionIcon(action, icon)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaActionButton(action: MediaAction?) {
    val size = IconButtonDefaults.mediumContainerSize()
    val icon = action?.icon
    if (action == null || icon == null) {
        Spacer(modifier = Modifier.size(size))
        return
    }
    FilledTonalIconButton(
        onClick = { action.action?.run() },
        shapes = IconButtonDefaults.shapes(),
        enabled = action.action != null,
        colors = tonalColors(),
        modifier = Modifier.size(size),
    ) {
        ActionIcon(action, icon)
    }
}

@Composable
private fun ActionIcon(action: MediaAction, icon: android.graphics.drawable.Drawable) {
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

@Composable
private fun tonalColors() =
    LocalContentColor.current.let {
        IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = it.copy(alpha = 0.16f),
            contentColor = it,
        )
    }

private fun formatElapsedTime(milliseconds: Long): String {
    return DateUtils.formatElapsedTime(milliseconds / DateUtils.SECOND_IN_MILLIS)
}
