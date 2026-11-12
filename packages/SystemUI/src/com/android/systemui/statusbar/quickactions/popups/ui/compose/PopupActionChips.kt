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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.systemui.statusbar.quickactions.popups.shared.model.PopupActionModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PopupActionChips(
    actions: List<PopupActionModel>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) {
        return
    }

    val height = ButtonDefaults.ExtraSmallContainerHeight
    val shapes = ButtonDefaults.shapesFor(height)
    val label: @Composable (String) -> Unit = {
        Text(
            text = it,
            style = ButtonDefaults.textStyleFor(height),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.forEach { action ->
            val buttonModifier = Modifier.weight(1f, fill = false).height(height)
            if (action.emphasized) {
                Button(
                    onClick = action.onClick,
                    shapes = shapes,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    contentPadding = ButtonDefaults.ExtraSmallContentPadding,
                    modifier = buttonModifier,
                ) {
                    label(action.label)
                }
            } else {
                FilledTonalButton(
                    onClick = action.onClick,
                    shapes = shapes,
                    colors =
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.11f),
                            contentColor = Color.White,
                        ),
                    contentPadding = ButtonDefaults.ExtraSmallContentPadding,
                    modifier = buttonModifier,
                ) {
                    label(action.label)
                }
            }
        }
    }
}
