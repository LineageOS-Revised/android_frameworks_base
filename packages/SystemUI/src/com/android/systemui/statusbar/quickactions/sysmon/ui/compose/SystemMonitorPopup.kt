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

package com.android.systemui.statusbar.quickactions.sysmon.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.systemui.res.R
import com.android.systemui.statusbar.quickactions.sysmon.shared.model.CoreStats
import com.android.systemui.statusbar.quickactions.sysmon.shared.model.SystemMonitorPopupModel
import com.android.systemui.statusbar.quickactions.sysmon.shared.model.SystemStats
import java.util.Locale

private val PopupShape = RoundedCornerShape(32.dp)
private const val HIGH_LOAD = 85

@Composable
fun SystemMonitorPopup(model: SystemMonitorPopupModel, modifier: Modifier = Modifier) {
    val stats = model.stats
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = PopupShape,
        shadowElevation = 12.dp,
        modifier = modifier.widthIn(min = 320.dp, max = 400.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.system_monitor_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(top = 8.dp),
                )
                IconButton(onClick = model.onClose, modifier = Modifier.padding(top = 6.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.system_monitor_close),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Cores(stats.cores)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MemoryRow("Mem", stats.memUsedMb, stats.memTotalMb)
                    if ((stats.swapTotalMb ?: 0) > 0) {
                        MemoryRow("Swp", stats.swapUsedMb, stats.swapTotalMb)
                    }
                }
                Sensors(stats)
                if (stats.topProcesses.isNotEmpty()) TopProcesses(stats)
            }
        }
    }
}

@Composable
private fun Cores(cores: List<CoreStats>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cores.withIndex().chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { (index, core) -> CoreCell(index, core) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RowScope.CoreCell(index: Int, core: CoreStats) {
    Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$index",
            style = monoStyle(),
            color = mutedColor(),
            modifier = Modifier.width(10.dp),
        )
        Meter(percent = core.load, modifier = Modifier.weight(1f))
        Text(
            text = "${core.load?.toString() ?: "--"}%".padStart(4),
            style = monoStyle(),
            textAlign = TextAlign.End,
        )
        Text(text = formatMhz(core.mhz), style = monoStyle(), color = mutedColor())
    }
}

@Composable
private fun MemoryRow(label: String, usedMb: Int?, totalMb: Int?) {
    val percent =
        if (usedMb != null && totalMb != null && totalMb > 0) usedMb * 100 / totalMb else null
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = monoStyle(),
            color = mutedColor(),
            modifier = Modifier.width(28.dp),
        )
        Meter(percent = percent, modifier = Modifier.weight(1f))
        Text(text = "${formatGb(usedMb)}/${formatGb(totalMb)}", style = monoStyle())
    }
}

@Composable
private fun Sensors(stats: SystemStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Sensor(
            label = "CPU",
            value = stats.cpuTemp?.let { "$it°C" } ?: "--",
        )
        Sensor(
            label = "GPU",
            value =
                listOfNotNull(stats.gpuLoad?.let { "$it%" }, stats.gpuMhz?.let { "${it}M" })
                    .joinToString(" ")
                    .ifEmpty { "--" },
        )
        Sensor(
            label = stringResource(R.string.system_monitor_battery),
            value =
                listOfNotNull(
                        stats.batteryTemp?.let { String.format(Locale.US, "%.1f°C", it) },
                        stats.batteryMa?.let { "${it}mA" },
                        stats.batteryWatts?.let { String.format(Locale.US, "%.1fW", it) },
                    )
                    .joinToString(" ")
                    .ifEmpty { "--" },
        )
    }
}

@Composable
private fun Sensor(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = mutedColor())
        Text(text = value, style = monoStyle(), maxLines = 1)
    }
}

@Composable
private fun TopProcesses(stats: SystemStats) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.system_monitor_top),
            style = MaterialTheme.typography.labelSmall,
            color = mutedColor(),
        )
        stats.topProcesses.forEach { process ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = process.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = String.format(Locale.US, "%.1f%%", process.cpu),
                    style = monoStyle(),
                )
            }
        }
    }
}

@Composable
private fun Meter(percent: Int?, modifier: Modifier = Modifier) {
    val fraction = (percent ?: 0).coerceIn(0, 100) / 100f
    val color =
        if ((percent ?: 0) >= HIGH_LOAD) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary
    Box(
        modifier =
            modifier
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight().background(color))
    }
}

@Composable
private fun monoStyle(): TextStyle =
    MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)

@Composable
private fun mutedColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

private fun formatMhz(mhz: Int?): String =
    when {
        mhz == null -> " -- "
        mhz >= 1000 -> String.format(Locale.US, "%.1fG", mhz / 1000f)
        else -> "${mhz}M"
    }.padStart(5)

private fun formatGb(mb: Int?): String =
    if (mb == null) "--" else String.format(Locale.US, "%.1fG", mb / 1024f)
