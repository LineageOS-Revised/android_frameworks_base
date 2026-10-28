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

package com.android.systemui.statusbar.quickactions.popups.ui.compose

import android.view.DisplayCutout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupContentModel
import com.android.systemui.statusbar.quickactions.screenrecord.shared.model.ScreenRecordPopupModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Single centered status bar capsule styled like a compact dynamic island. */
@Composable
fun StatusBarDynamicIslandChip(
    viewModel: PopupChipModel.Shown,
    pageCount: Int,
    cutoutSpec: DynamicIslandCutoutSpec,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    onChipBoundsChanged: (Rect) -> Unit = {},
    onReservedWidthChanged: (Int) -> Unit = {},
) {
    val isMediaChip = viewModel.popupContent is PopupContentModel.Media
    val chipShape = RoundedCornerShape(50)
    val colors = viewModel.colors
    val chipBackgroundColor =
        colors.chipBackground(
            isPopupShown = viewModel.isPopupShown,
            colorScheme = MaterialTheme.colorScheme,
        )
    val chipContentColor =
        colors.chipContent(
            isPopupShown = viewModel.isPopupShown,
            colorScheme = MaterialTheme.colorScheme,
        )
    val chipOutline =
        colors.chipOutline(
            isPopupShown = viewModel.isPopupShown,
            colorScheme = MaterialTheme.colorScheme,
        )
    val view = LocalView.current
    val split = remember { CutoutSplit() }
    val boundsModifier =
        Modifier.onGloballyPositioned { coordinates ->
            onChipBoundsChanged(coordinates.boundsInScreen(view))
            onReservedWidthChanged(coordinates.size.width + 2 * abs(split.shiftPx))
        }
    // Moves the chip sideways so the gap left for the camera sits right over the cutout. Room
    // is kept on both sides so the island view stays centered and nothing gets clipped.
    val cutoutAlignModifier =
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val shift = split.shiftPx
            layout(placeable.width + 2 * abs(shift), placeable.height) {
                placeable.place(abs(shift) + shift, 0)
            }
        }
    if (viewModel.popupContent.isUtilityStatusContent() && viewModel.icons.isNotEmpty()) {
        UtilityStatusIslandChip(
            viewModel = viewModel,
            onTap = onTap,
            cutoutSpec = cutoutSpec,
            split = split,
            chipBackgroundColor = chipBackgroundColor,
            chipContentColor = chipContentColor,
            chipOutline = chipOutline,
            modifier = modifier.then(cutoutAlignModifier).then(boundsModifier),
        )
        return
    }

    val cutoutGap = if (cutoutSpec.hasCutout) cutoutSpec.embeddedGapWidth else 0.dp
    val compactWidth = compactIslandWidthFor(viewModel.popupContent)?.plus(cutoutGap)
    val hasInlineTimer = viewModel.popupContent is PopupContentModel.Stopwatch
    val trailingDecorationWidth =
        when (val popupContent = viewModel.popupContent) {
            is PopupContentModel.Media ->
                if (popupContent.model.isPlaying) {
                    14.dp
                } else if (pageCount > 1) {
                    11.dp
                } else {
                    0.dp
                }
            is PopupContentModel.ScreenRecord -> 11.dp
            is PopupContentModel.Transfer -> 22.dp
            else -> {
                if (pageCount > 1) 11.dp else 0.dp
            }
        }
    val leadingDecorationWidth =
        when {
            viewModel.icons.isEmpty() -> 0.dp
            else -> 18.dp + (8.dp * (viewModel.icons.size - 1))
        }
    val maxTextWidth =
        (CompactIslandMaxWidth - 24.dp - leadingDecorationWidth - trailingDecorationWidth)
            .coerceAtLeast(56.dp)

    val leading: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            viewModel.icons.forEachIndexed { index, chipIcon ->
                val isArtworkLike =
                    index == 0 &&
                        (viewModel.popupContent is PopupContentModel.Media ||
                            viewModel.popupContent is PopupContentModel.LiveScore ||
                            viewModel.popupContent is PopupContentModel.Delivery ||
                            viewModel.popupContent is PopupContentModel.Transfer)
                Icon(
                    icon = chipIcon.icon,
                    modifier = Modifier
                        .size(if (isArtworkLike) 18.dp else 16.dp)
                        .then(
                            if (isArtworkLike) {
                                Modifier.clip(CircleShape)
                            } else {
                                Modifier
                            }
                        ),
                    tint = if (isArtworkLike) Color.Unspecified else chipContentColor,
                )
            }
        }
    }

    val trailing: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            viewModel.chipText
                ?.takeIf {
                    !isMediaChip &&
                        viewModel.popupContent !is PopupContentModel.ScreenRecord &&
                        !hasInlineTimer &&
                        it.isNotBlank()
                }
                ?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        color = chipContentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = maxTextWidth),
                    )
                }

            when (val popupContent = viewModel.popupContent) {
                is PopupContentModel.Media ->
                    if (popupContent.model.isPlaying) {
                        AudioReactiveBars(
                            isPlaying = true,
                            color = chipContentColor,
                        )
                    } else if (pageCount > 1) {
                        SwipeHint(color = chipContentColor.copy(alpha = 0.72f))
                    }
                is PopupContentModel.ScreenRecord ->
                    when (val model = popupContent.model) {
                        is ScreenRecordPopupModel.Starting ->
                            StatusContent(
                                text = viewModel.chipText.orEmpty(),
                                color = chipContentColor,
                                showSwipeHint = pageCount > 1,
                            )
                        is ScreenRecordPopupModel.Recording ->
                            StatusContent(
                                text = viewModel.chipText.orEmpty(),
                                color = chipContentColor,
                                showSwipeHint = pageCount > 1,
                            )
                    }
                is PopupContentModel.Stopwatch ->
                    StatusContent(
                        text =
                            popupContent.model.elapsedTimeText
                                ?: rememberElapsedDurationText(
                                    popupContent.model.baseElapsedRealtimeMs
                                ),
                        color = chipContentColor,
                        showSwipeHint = pageCount > 1,
                    )
                is PopupContentModel.Transfer ->
                    CircularProgressIndicator(
                        progress = { popupContent.model.percent / 100f },
                        modifier = Modifier.size(14.dp),
                        color = chipContentColor,
                        strokeWidth = 2.dp,
                        trackColor = chipContentColor.copy(alpha = 0.24f),
                    )
                else -> {
                    if (pageCount > 1) {
                        SwipeHint(color = chipContentColor.copy(alpha = 0.72f))
                    }
                }
            }
        }
    }

    val hasCutout = cutoutSpec.hasCutout
    Layout(
        contents = listOf(leading, trailing),
        modifier =
            modifier
                .then(cutoutAlignModifier)
                .then(boundsModifier)
                .openSquishAnimation(viewModel.isPopupShown)
                .defaultMinSize(minHeight = 32.dp)
                .widthIn(
                    min = compactWidth ?: 0.dp,
                    max = compactWidth ?: (CompactIslandMaxWidth + cutoutGap),
                )
                .clip(chipShape)
                .background(chipBackgroundColor)
                .border(width = 1.dp, color = chipOutline, shape = chipShape)
                .clickable(onClick = onTap)
                .padding(horizontal = 12.dp, vertical = 7.dp),
    ) { (leadingMeasurables, trailingMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val left = leadingMeasurables.map { it.measure(loose) }
        val leftWidth = left.maxOfOrNull { it.width } ?: 0
        val hasLeft = viewModel.icons.isNotEmpty()
        val spacing =
            when {
                hasCutout -> cutoutGap.roundToPx()
                hasLeft -> 8.dp.roundToPx()
                else -> 0
            }
        val usedLeft = if (hasLeft) leftWidth else 0
        val rightConstraints =
            loose.copy(maxWidth = (constraints.maxWidth - usedLeft - spacing).coerceAtLeast(0))
        val right = trailingMeasurables.map { it.measure(rightConstraints) }
        val rightWidth = right.maxOfOrNull { it.width } ?: 0
        val width =
            (usedLeft + spacing + rightWidth).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height =
            maxOf(left.maxOfOrNull { it.height } ?: 0, right.maxOfOrNull { it.height } ?: 0)
                .coerceIn(constraints.minHeight, constraints.maxHeight)
        split.shiftPx = if (hasCutout) (rightWidth - usedLeft) / 2 else 0
        layout(width, height) {
            if (hasLeft) left.forEach { it.place(0, (height - it.height) / 2) }
            right.forEach { it.place(width - rightWidth, (height - it.height) / 2) }
        }
    }
}

/** Sideways shift that puts the chip's camera gap over the cutout. */
private class CutoutSplit {
    var shiftPx by mutableIntStateOf(0)
}

@Composable
private fun UtilityStatusIslandChip(
    viewModel: PopupChipModel.Shown,
    onTap: () -> Unit,
    cutoutSpec: DynamicIslandCutoutSpec,
    split: CutoutSplit,
    chipBackgroundColor: Color,
    chipContentColor: Color,
    chipOutline: Color,
    modifier: Modifier = Modifier,
) {
    val rightSegmentWidth =
        when (viewModel.popupContent) {
            is PopupContentModel.Flashlight -> 52.dp
            is PopupContentModel.Alarm -> 72.dp
            else -> 80.dp
        }
    val connectedIslandWidth =
        (CompactUtilityConnectedIslandChromeWidth +
                cutoutSpec.embeddedGapWidth +
                rightSegmentWidth)
            .coerceIn(
                CompactUtilityConnectedIslandMinWidth,
                CompactUtilityConnectedIslandMaxWidth,
            )
    val utilityText =
        when (val popupContent = viewModel.popupContent) {
            is PopupContentModel.ScreenRecord ->
                when (val model = popupContent.model) {
                    is ScreenRecordPopupModel.Starting -> "${model.secondsUntilStarted}s"
                    is ScreenRecordPopupModel.Recording ->
                        rememberElapsedDurationText(model.startElapsedRealtimeMs)
                }
            is PopupContentModel.Stopwatch ->
                popupContent.model.elapsedTimeText
                    ?: rememberElapsedDurationText(popupContent.model.baseElapsedRealtimeMs)
            is PopupContentModel.Alarm -> viewModel.chipText.orEmpty()
            is PopupContentModel.Flashlight -> viewModel.chipText.orEmpty()
            else -> ""
        }

    Row(
        modifier =
            modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    // Icon side is 10dp + 16dp icon; everything after the gap is the other side.
                    val iconSide = 26.dp.roundToPx()
                    val gap = cutoutSpec.embeddedGapWidth.roundToPx()
                    split.shiftPx =
                        if (cutoutSpec.hasCutout) {
                            (placeable.width - gap - 2 * iconSide) / 2
                        } else {
                            0
                        }
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .openSquishAnimation(viewModel.isPopupShown)
                .defaultMinSize(minHeight = 32.dp)
                .width(connectedIslandWidth)
                .clip(RoundedCornerShape(50))
                .background(chipBackgroundColor)
                .border(width = 1.dp, color = chipOutline, shape = RoundedCornerShape(50))
                .clickable(onClick = onTap),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            icon = viewModel.icons.first().icon,
            modifier = Modifier.size(16.dp),
            tint = chipContentColor,
        )
        Spacer(modifier = Modifier.width(cutoutSpec.embeddedGapWidth))
        Box(
            modifier =
                Modifier.width(rightSegmentWidth)
                    .padding(start = 6.dp, top = 7.dp, bottom = 7.dp, end = 6.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = utilityText,
                style = MaterialTheme.typography.labelLarge,
                color = chipContentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
    }
}

@Composable
private fun StatusContent(
    text: String,
    color: Color,
    showSwipeHint: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
        )
        if (showSwipeHint) {
            SwipeHint(color = color.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun SwipeHint(color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(2) {
            Box(
                modifier = Modifier.size(width = 3.dp, height = 3.dp)
                    .background(color = color, shape = CircleShape)
            )
        }
    }
}

private val CompactIslandMaxWidth = 192.dp
private val CompactMediaIslandWidth = 108.dp
private val CompactTimerIslandWidth = 116.dp
private val CompactRecordingIslandWidth = 88.dp
private val CompactAlarmIslandWidth = 92.dp
private val CompactUtilityIslandWidth = 74.dp
private val CompactUtilityConnectedIslandChromeWidth = 42.dp
private val CompactUtilityConnectedIslandMinWidth = 132.dp
private val CompactUtilityConnectedIslandMaxWidth = 188.dp
private val DynamicIslandEmbeddedGapFallbackWidth = 38.dp
private val DynamicIslandEmbeddedGapMinWidth = 34.dp
private val DynamicIslandEmbeddedGapMaxWidth = 88.dp
private val DynamicIslandEmbeddedGapSidePadding = 10.dp

data class DynamicIslandCutoutSpec(
    val embeddedGapWidth: Dp,
    val horizontalOffset: Dp,
    val hasCutout: Boolean,
)

@Composable
fun rememberDynamicIslandCutoutSpec(): DynamicIslandCutoutSpec {
    val density = LocalDensity.current
    val view = LocalView.current
    val displayCutout = view.rootWindowInsets?.displayCutout ?: view.display?.cutout
    val topCutout = displayCutout?.topBoundingRectOrNull()
    val rootWidthPx =
        when {
            view.rootView.width > 0 -> view.rootView.width
            view.width > 0 -> view.width
            else -> view.resources.configuration.windowConfiguration.maxBounds.width()
        }

    return with(density) {
        if (topCutout == null || rootWidthPx <= 0) {
            DynamicIslandCutoutSpec(
                embeddedGapWidth = DynamicIslandEmbeddedGapFallbackWidth,
                horizontalOffset = 0.dp,
                hasCutout = false,
            )
        } else {
            val embeddedGapWidthDp =
                (topCutout.width().toDp() + (DynamicIslandEmbeddedGapSidePadding * 2))
                    .coerceIn(
                        DynamicIslandEmbeddedGapMinWidth,
                        DynamicIslandEmbeddedGapMaxWidth,
                    )
            val horizontalOffsetDp = (topCutout.exactCenterX() - (rootWidthPx / 2f)).toDp()
            DynamicIslandCutoutSpec(
                embeddedGapWidth = embeddedGapWidthDp,
                horizontalOffset = horizontalOffsetDp,
                hasCutout = true,
            )
        }
    }
}

private fun DisplayCutout.topBoundingRectOrNull() =
    getBoundingRectTop().takeUnless { it.isEmpty }

private fun PopupContentModel.isUtilityStatusContent(): Boolean {
    return this is PopupContentModel.ScreenRecord ||
        this is PopupContentModel.Stopwatch ||
        this is PopupContentModel.Alarm ||
        this is PopupContentModel.Flashlight
}

private fun compactIslandWidthFor(content: PopupContentModel): Dp? {
    return when (content) {
        is PopupContentModel.Media -> CompactMediaIslandWidth
        is PopupContentModel.ScreenRecord ->
            when (content.model) {
                is ScreenRecordPopupModel.Starting -> CompactTimerIslandWidth
                is ScreenRecordPopupModel.Recording -> CompactRecordingIslandWidth
            }
        is PopupContentModel.Stopwatch -> CompactTimerIslandWidth
        is PopupContentModel.Alarm -> CompactAlarmIslandWidth
        is PopupContentModel.Flashlight -> CompactUtilityIslandWidth
        else -> null
    }
}

@Composable
private fun Modifier.openSquishAnimation(isOpen: Boolean): Modifier {
    val scaleX = remember { Animatable(1f, visibilityThreshold = 0.01f) }
    val scaleY = remember { Animatable(1f, visibilityThreshold = 0.01f) }
    val currentIsOpen by rememberUpdatedState(isOpen)
    LaunchedEffect(Unit) {
        snapshotFlow { currentIsOpen }
            .drop(1)
            .collectLatest { open ->
                if (!open) return@collectLatest
                scaleX.snapTo(1f)
                scaleY.snapTo(1f)
                coroutineScope {
                    launch {
                        scaleX.animateTo(
                            targetValue = 1f,
                            animationSpec =
                                keyframes {
                                    durationMillis = 360
                                    0.9f at 0
                                    1.05f at 160 using FastOutSlowInEasing
                                    0.98f at 280
                                    1f at 360
                                },
                        )
                    }
                    launch {
                        scaleY.animateTo(
                            targetValue = 1f,
                            animationSpec =
                                keyframes {
                                    durationMillis = 360
                                    1.12f at 0
                                    0.94f at 160 using FastOutSlowInEasing
                                    1.02f at 280
                                    1f at 360
                                },
                        )
                    }
                }
            }
    }
    return this.graphicsLayer {
        this.scaleX = scaleX.value
        this.scaleY = scaleY.value
    }
}

private fun LayoutCoordinates.boundsInScreen(view: android.view.View): Rect {
    val location = IntArray(2)
    view.getLocationOnScreen(location)
    return boundsInRoot().translate(Offset(location[0].toFloat(), location[1].toFloat()))
}
