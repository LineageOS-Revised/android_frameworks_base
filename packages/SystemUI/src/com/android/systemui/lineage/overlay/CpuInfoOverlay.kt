/*
 * Copyright (C) 2026 The LineageOS Project
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

package com.android.systemui.lineage.overlay

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

/** Overlay showing the CPU load, the average CPU frequency and the CPU temperature. */
@SysUISingleton
class CpuInfoOverlay
@Inject
constructor(
    context: Context,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
) : InfoOverlay(context, Handler(backgroundLooper), mainHandler) {

    /** Thermal zone types used to read the CPU temperature, in order of preference. */
    private val temperatureTypes =
        context.resources.getStringArray(R.array.config_cpuInfoTempNodeTypes)

    private var previousTimes: CpuTimes? = null
    private var temperatureNode: String? = null

    override fun refresh(): CharSequence {
        val times = readCpuTimes()
        val load = calculateLoad(times)
        previousTimes = times

        return context.getString(
            R.string.cpu_info_overlay_text,
            formatLoad(load),
            formatFrequency(averageFrequency()),
            formatTemperature(readTemperature()),
        )
    }

    private fun readCpuTimes(): CpuTimes? =
        try {
            File(PROC_STAT).useLines { lines ->
                lines.firstOrNull { it.startsWith("cpu ") }?.let { parseCpuLine(it) }
            }
        } catch (e: IOException) {
            null
        }

    private fun parseCpuLine(line: String): CpuTimes? {
        // cpu user nice system idle iowait irq softirq steal guest guest_nice ...
        val fields = line.trim().split(WHITESPACE)
        if (fields.size < 5) {
            return null
        }
        var total = 0L
        var idle = 0L
        for (i in 1 until fields.size) {
            val value = fields[i].toLongOrNull() ?: break
            total += value
            if (i == IDLE_FIELD || i == IOWAIT_FIELD) {
                idle += value
            }
        }
        return CpuTimes(total, idle)
    }

    private fun calculateLoad(times: CpuTimes?): Int? {
        val previous = previousTimes ?: return null
        if (times == null) {
            return null
        }
        val total = times.total - previous.total
        val idle = times.idle - previous.idle
        if (total <= 0L) {
            return 0
        }
        return ((total - idle) * 100 / total).toInt()
    }

    private fun averageFrequency(): Long? {
        var sum = 0L
        var count = 0
        for (core in 0 until Runtime.getRuntime().availableProcessors()) {
            val khz = readNumber(String.format(Locale.US, CPU_FREQ_PATH, core)) ?: continue
            sum += khz
            count++
        }
        return if (count > 0) sum / count else null
    }

    private fun readTemperature(): Int? {
        val node = temperatureNode ?: resolveTemperatureNode() ?: return null
        val millidegrees = readNumber(node)
        if (millidegrees == null) {
            // The zone disappeared, look it up again on the next update.
            temperatureNode = null
            return null
        }
        return if (millidegrees > 1000L) (millidegrees / 1000L).toInt() else millidegrees.toInt()
    }

    private fun resolveTemperatureNode(): String? {
        val zones = File(THERMAL_ZONES).listFiles() ?: return null
        val zoneTypes = mutableListOf<Pair<File, String>>()
        for (zone in zones) {
            val type = readString("${zone.path}/$TYPE_NODE")?.trim() ?: continue
            if (type.isNotEmpty()) {
                zoneTypes += zone to type
            }
        }
        val zone =
            temperatureTypes.firstNotNullOfOrNull { type ->
                zoneTypes.firstOrNull { it.second.equals(type, ignoreCase = true) }?.first
            } ?: zoneTypes.firstOrNull { it.second.startsWith(CPU_TYPE_PREFIX, true) }?.first
        return zone?.let { "${it.path}/$TEMP_NODE" }?.also { temperatureNode = it }
    }

    private fun readString(path: String): String? =
        try {
            File(path).readText()
        } catch (e: IOException) {
            null
        }

    private fun readNumber(path: String): Long? = readString(path)?.trim()?.toLongOrNull()

    private fun formatLoad(load: Int?): String = if (load != null) "$load%" else "--"

    private fun formatFrequency(khz: Long?): String =
        when {
            khz == null -> "--"
            khz >= 1_000_000L -> String.format(Locale.US, "%.2fGHz", khz / 1_000_000.0)
            else -> String.format(Locale.US, "%dMHz", khz / 1_000)
        }

    private fun formatTemperature(celsius: Int?): String =
        if (celsius != null) "$celsius°C" else "--"

    private class CpuTimes(val total: Long, val idle: Long)

    private companion object {
        const val PROC_STAT = "/proc/stat"
        const val THERMAL_ZONES = "/sys/class/thermal"
        const val TYPE_NODE = "type"
        const val TEMP_NODE = "temp"
        const val CPU_TYPE_PREFIX = "cpu"
        const val CPU_FREQ_PATH = "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq"
        const val IDLE_FIELD = 4
        const val IOWAIT_FIELD = 5
        val WHITESPACE = Regex("""\s+""")
    }
}
