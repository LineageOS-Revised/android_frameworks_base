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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusBarDynamicIslandChip(
    viewModel: PopupChipModel.Shown,
    cutoutSpec: DynamicIslandCutoutSpec,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    onChipBoundsChanged: (Rect) -> Unit = {},
    onReservedWidthChanged: (Int) -> Unit = {},
) {
    val isMediaChip = viewModel.popupContent is PopupContentModel.Media
    val chipShape = CircleShape
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
    val boundsModifier =
        Modifier.onGloballyPositioned { coordinates ->
            onChipBoundsChanged(coordinates.boundsInScreen(view))
            onReservedWidthChanged(coordinates.size.width)
        }
    if (viewModel.popupContent.isUtilityStatusContent() && viewModel.icons.isNotEmpty()) {
        UtilityStatusIslandChip(
            viewModel = viewModel,
            onTap = onTap,
            cutoutSpec = cutoutSpec,
            chipBackgroundColor = chipBackgroundColor,
            chipContentColor = chipContentColor,
            chipOutline = chipOutline,
            modifier = modifier.then(boundsModifier),
        )
        return
    }

    val hasCutout = cutoutSpec.hasCutout
    val cutoutGap = if (hasCutout) cutoutSpec.embeddedGapWidth else 0.dp
    val hasInlineTimer = viewModel.popupContent is PopupContentModel.Stopwatch
    val trailingDecorationWidth =
        when (val popupContent = viewModel.popupContent) {
            is PopupContentModel.Media -> if (popupContent.model.isPlaying) 14.dp else 0.dp
            is PopupContentModel.Transfer -> 22.dp
            else -> 0.dp
        }
    val leadingDecorationWidth =
        when {
            viewModel.icons.isEmpty() -> 0.dp
            else -> 18.dp + (8.dp * (viewModel.icons.size - 1))
        }
    val maxTextWidth =
        (CompactIslandMaxWidth - 20.dp - leadingDecorationWidth - trailingDecorationWidth)
            .coerceAtLeast(56.dp)

    val chipText =
        viewModel.chipText?.takeIf {
            !isMediaChip &&
                viewModel.popupContent !is PopupContentModel.ScreenRecord &&
                !hasInlineTimer &&
                it.isNotBlank()
        }
    val textStyle = MaterialTheme.typography.labelLarge
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val (leftText, rightText) =
        remember(chipText, hasCutout, textStyle, leadingDecorationWidth, trailingDecorationWidth) {
            if (chipText == null || !hasCutout) {
                null to chipText
            } else {
                with(density) {
                    splitAroundCutout(
                        text = chipText,
                        leadingPx = leadingDecorationWidth.roundToPx(),
                        trailingPx = trailingDecorationWidth.roundToPx(),
                        spacingPx = 8.dp.roundToPx(),
                        measure = { textMeasurer.measure(it, textStyle).size.width },
                    )
                }
            }
        }

    val chipLabel: @Composable (String) -> Unit = { text ->
        Text(
            text = text,
            style = textStyle,
            color = chipContentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = maxTextWidth),
        )
    }

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
                            viewModel.popupContent is PopupContentModel.Transfer ||
                            viewModel.popupContent is PopupContentModel.LiveUpdate)
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
            leftText?.let { chipLabel(it) }
        }
    }

    val trailing: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            rightText?.let { chipLabel(it) }

            when (val popupContent = viewModel.popupContent) {
                is PopupContentModel.Media ->
                    if (popupContent.model.isPlaying) {
                        AudioReactiveBars(
                            isPlaying = true,
                            color = popupContent.model.colorScheme?.primary ?: chipContentColor,
                        )
                    }
                is PopupContentModel.ScreenRecord ->
                    StatusContent(text = viewModel.chipText.orEmpty(), color = chipContentColor)
                is PopupContentModel.Stopwatch ->
                    StatusContent(
                        text =
                            popupContent.model.elapsedTimeText
                                ?: rememberElapsedDurationText(
                                    popupContent.model.baseElapsedRealtimeMs
                                ),
                        color = chipContentColor,
                    )
                is PopupContentModel.Transfer ->
                    CircularProgressIndicator(
                        progress = { popupContent.model.percent / 100f },
                        modifier = Modifier.size(14.dp),
                        color = chipContentColor,
                        strokeWidth = 2.dp,
                        trackColor = chipContentColor.copy(alpha = 0.24f),
                    )
                is PopupContentModel.LiveUpdate -> {
                    val timer = popupContent.model.update.timer
                    if (timer != null && viewModel.chipText == null) {
                        StatusContent(
                            text =
                                rememberChronometerText(
                                    timer.baseElapsedRealtimeMs,
                                    timer.isCountDown,
                                ),
                            color = chipContentColor,
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    Layout(
        contents = listOf(leading, trailing),
        modifier =
            modifier
                .then(boundsModifier)
                .openSquishAnimation(viewModel.isPopupShown)
                .defaultMinSize(minHeight = 32.dp)
                .widthIn(max = CompactIslandMaxWidth + cutoutGap)
                .clip(chipShape)
                .background(chipBackgroundColor)
                .border(width = 1.dp, color = chipOutline, shape = chipShape)
                .clickable(onClick = onTap)
                .padding(horizontal = 10.dp, vertical = 7.dp),
    ) { (leadingMeasurables, trailingMeasurables), constraints ->
        val gap = if (hasCutout) cutoutGap.roundToPx() else 0
        val spacing = if (hasCutout) 0 else 8.dp.roundToPx()
        val sideMax =
            if (hasCutout) ((constraints.maxWidth - gap) / 2).coerceAtLeast(0)
            else constraints.maxWidth
        val loose = Constraints(maxWidth = sideMax, maxHeight = constraints.maxHeight)
        val left = leadingMeasurables.map { it.measure(loose) }
        val leftWidth = left.maxOfOrNull { it.width } ?: 0
        val usedSpacing = if (leftWidth > 0) spacing else 0
        val right =
            trailingMeasurables.map {
                it.measure(
                    loose.copy(
                        maxWidth =
                            if (hasCutout) sideMax
                            else (sideMax - leftWidth - usedSpacing).coerceAtLeast(0)
                    )
                )
            }
        val rightWidth = right.maxOfOrNull { it.width } ?: 0
        val contentWidth =
            if (hasCutout) 2 * maxOf(leftWidth, rightWidth) + gap
            else leftWidth + usedSpacing + rightWidth
        val width = contentWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height =
            maxOf(left.maxOfOrNull { it.height } ?: 0, right.maxOfOrNull { it.height } ?: 0)
                .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            val rightX =
                if (hasCutout) width - rightWidth else leftWidth + usedSpacing
            left.forEach { it.place(0, (height - it.height) / 2) }
            right.forEach { it.place(rightX, (height - it.height) / 2) }
        }
    }
}

/**
 * Splits [text] into a part shown left of the camera and a part shown right of it, so both sides
 * of the island end up about equally wide. Prefers splitting at " · ", else at a space.
 */
private fun splitAroundCutout(
    text: String,
    leadingPx: Int,
    trailingPx: Int,
    spacingPx: Int,
    measure: (String) -> Int,
): Pair<String?, String?> {
    val separator = if (text.contains(DotSeparator)) DotSeparator else " "
    val candidates = mutableListOf<Pair<String?, String?>>(null to text, text to null)
    var index = text.indexOf(separator)
    while (index >= 0) {
        val before = text.substring(0, index).trim()
        val after = text.substring(index + separator.length).trim()
        if (before.isNotEmpty() && after.isNotEmpty()) candidates += before to after
        index = text.indexOf(separator, index + separator.length)
    }

    fun sideWidth(part: String?, decorationPx: Int): Int {
        val textPx = part?.let(measure) ?: 0
        return when {
            textPx == 0 -> decorationPx
            decorationPx == 0 -> textPx
            else -> textPx + spacingPx + decorationPx
        }
    }

    return candidates.minBy { (before, after) ->
        abs(sideWidth(before, leadingPx) - sideWidth(after, trailingPx))
    }
}

private const val DotSeparator = " · "

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UtilityStatusIslandChip(
    viewModel: PopupChipModel.Shown,
    onTap: () -> Unit,
    cutoutSpec: DynamicIslandCutoutSpec,
    chipBackgroundColor: Color,
    chipContentColor: Color,
    chipOutline: Color,
    modifier: Modifier = Modifier,
) {
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
                .openSquishAnimation(viewModel.isPopupShown)
                .defaultMinSize(minHeight = 32.dp)
                .widthIn(max = CompactIslandMaxWidth + cutoutSpec.embeddedGapWidth)
                // Weighted halves of an intrinsic-width row are equally wide and as narrow as
                // the content allows, so the gap in the middle sits over the camera.
                .width(IntrinsicSize.Max)
                .clip(CircleShape)
                .background(chipBackgroundColor)
                .border(width = 1.dp, color = chipOutline, shape = CircleShape)
                .clickable(onClick = onTap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(1f).padding(start = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val content = viewModel.popupContent
            if (content is PopupContentModel.ScreenRecord &&
                content.model is ScreenRecordPopupModel.Starting
            ) {
                LoadingIndicator(modifier = Modifier.size(16.dp), color = chipContentColor)
            } else {
                Icon(
                    icon = viewModel.icons.first().icon,
                    modifier = Modifier.size(16.dp),
                    tint = chipContentColor,
                )
            }
        }
        Spacer(
            modifier =
                Modifier.width(if (cutoutSpec.hasCutout) cutoutSpec.embeddedGapWidth else 8.dp)
        )
        Box(
            modifier = Modifier.weight(1f).padding(top = 7.dp, bottom = 7.dp, end = 10.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = utilityText,
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = chipContentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatusContent(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        color = color,
        maxLines = 1,
    )
}

private val CompactIslandMaxWidth = 160.dp
private val DynamicIslandEmbeddedGapFallbackWidth = 38.dp
private val DynamicIslandEmbeddedGapMinWidth = 34.dp
private val DynamicIslandEmbeddedGapMaxWidth = 88.dp
private val DynamicIslandEmbeddedGapSidePadding = 4.dp

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Modifier.openSquishAnimation(isOpen: Boolean): Modifier {
    val scaleX = remember { Animatable(1f, visibilityThreshold = 0.01f) }
    val scaleY = remember { Animatable(1f, visibilityThreshold = 0.01f) }
    val currentIsOpen by rememberUpdatedState(isOpen)
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        snapshotFlow { currentIsOpen }
            .drop(1)
            .collectLatest { open ->
                if (!open) return@collectLatest
                scaleX.snapTo(0.9f)
                scaleY.snapTo(1.12f)
                coroutineScope {
                    launch { scaleX.animateTo(1f, spec) }
                    launch { scaleY.animateTo(1f, spec) }
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
