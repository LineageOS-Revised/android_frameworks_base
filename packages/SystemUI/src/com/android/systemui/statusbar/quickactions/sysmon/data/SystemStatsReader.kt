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

package com.android.systemui.statusbar.quickactions.sysmon.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.ParcelFileDescriptor
import android.os.ServiceManager
import com.android.systemui.res.R
import com.android.systemui.statusbar.quickactions.sysmon.shared.model.CoreStats
import com.android.systemui.statusbar.quickactions.sysmon.shared.model.ProcessStats
import com.android.systemui.statusbar.quickactions.sysmon.shared.model.SystemStats
import java.io.File
import java.io.FileInputStream
import kotlin.math.abs

/** Reads system stats from /proc, sysfs and the cpuinfo service. Not thread safe. */
class SystemStatsReader(private val context: Context) {

    private val coreCount = Runtime.getRuntime().availableProcessors()
    private val tempTypes = context.resources.getStringArray(R.array.config_cpuInfoTempNodeTypes)
    private val batteryManager = context.getSystemService(BatteryManager::class.java)

    private var previousTimes: Map<String, CpuTimes> = emptyMap()
    private var tempNode: String? = null
    private val unreadable = HashSet<String>()

    fun read(): SystemStats {
        val times = readCpuTimes()
        val loads =
            times.mapValues { (name, now) -> previousTimes[name]?.let { now.loadSince(it) } }
        previousTimes = times

        val mem = readMeminfo()
        val memTotal = mem["MemTotal"]
        val swapTotal = mem["SwapTotal"]
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryMa = readBatteryMa()
        val voltageMv = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        return SystemStats(
            cpuLoad = loads["cpu"],
            cpuTemp = readCpuTemp(),
            cores =
                (0 until coreCount).map { core ->
                    CoreStats(
                        load = loads["cpu$core"],
                        mhz = readLong("$CPU_DIR/cpu$core/cpufreq/scaling_cur_freq")
                            ?.let { (it / 1000).toInt() },
                    )
                },
            memUsedMb = memTotal?.let { total -> mem["MemAvailable"]?.let { (total - it).toMb() } },
            memTotalMb = memTotal?.toMb(),
            swapUsedMb = swapTotal?.let { total -> mem["SwapFree"]?.let { (total - it).toMb() } },
            swapTotalMb = swapTotal?.toMb(),
            gpuLoad = readOnce(GPU_BUSY_PATH)?.substringBefore('%')?.trim()?.toIntOrNull(),
            gpuMhz =
                readOnce(GPU_CLOCK_PATH)?.trim()?.toLongOrNull()?.let { (it / 1_000_000).toInt() },
            batteryTemp =
                battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                    ?.takeIf { it != Int.MIN_VALUE }
                    ?.let { it / 10f },
            batteryMa = batteryMa,
            batteryWatts =
                if (batteryMa != null && voltageMv > 0) abs(batteryMa) * voltageMv / 1_000_000f
                else null,
            topProcesses = readTopProcesses(),
        )
    }

    private fun readCpuTimes(): Map<String, CpuTimes> {
        val text = readText(PROC_STAT) ?: return emptyMap()
        val times = HashMap<String, CpuTimes>()
        for (line in text.lineSequence()) {
            if (!line.startsWith("cpu")) break
            val fields = line.trim().split(WHITESPACE)
            if (fields.size < 6) continue
            var total = 0L
            for (i in 1 until fields.size) total += fields[i].toLongOrNull() ?: 0L
            val idle = (fields[4].toLongOrNull() ?: 0L) + (fields[5].toLongOrNull() ?: 0L)
            times[fields[0]] = CpuTimes(total, idle)
        }
        return times
    }

    private fun readMeminfo(): Map<String, Long> {
        val text = readText(PROC_MEMINFO) ?: return emptyMap()
        val values = HashMap<String, Long>()
        for (line in text.lineSequence()) {
            val key = line.substringBefore(':', "")
            if (key in MEMINFO_KEYS) {
                line.substringAfter(':').trim().substringBefore(' ').toLongOrNull()?.let {
                    values[key] = it
                }
            }
        }
        return values
    }

    private fun readBatteryMa(): Int? {
        val raw = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (raw == null || raw == Int.MIN_VALUE) return null
        // Most kernels report microamps, some report milliamps.
        return if (abs(raw) > 20_000) raw / 1000 else raw
    }

    private fun readCpuTemp(): Int? {
        val node = tempNode ?: findTempNode() ?: return null
        val value = readLong(node)
        if (value == null) {
            tempNode = null
            return null
        }
        return if (value > 1000L) (value / 1000L).toInt() else value.toInt()
    }

    private fun findTempNode(): String? {
        val zones =
            File(THERMAL_ZONES).listFiles()?.mapNotNull { zone ->
                readText("${zone.path}/type")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { zone to it }
            } ?: return null
        val zone =
            tempTypes.firstNotNullOfOrNull { type ->
                zones.firstOrNull { it.second.equals(type, ignoreCase = true) }?.first
            } ?: zones.firstOrNull { it.second.startsWith("cpu", ignoreCase = true) }?.first
        return zone?.let { "${it.path}/temp" }?.also { tempNode = it }
    }

    /** Top processes from the CPU snapshot system_server already keeps. */
    private fun readTopProcesses(): List<ProcessStats> {
        val binder = ServiceManager.checkService(CPUINFO_SERVICE) ?: return emptyList()
        return try {
            val (read, write) = ParcelFileDescriptor.createPipe()
            read.use {
                write.use { binder.dumpAsync(it.fileDescriptor, null) }
                FileInputStream(read.fileDescriptor).bufferedReader().useLines { lines ->
                    lines
                        .take(MAX_CPUINFO_LINES)
                        .mapNotNull { line ->
                            PROCESS_LINE.find(line)?.let {
                                ProcessStats(it.groupValues[2], it.groupValues[1].toFloat())
                            }
                        }
                        .filter { it.cpu > 0f }
                        .take(TOP_PROCESSES)
                        .toList()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun readText(path: String): String? =
        try {
            File(path).readText()
        } catch (e: Exception) {
            null
        }

    private fun readLong(path: String): Long? = readText(path)?.trim()?.toLongOrNull()

    /** For nodes that may be blocked by SELinux: stop trying after the first failure. */
    private fun readOnce(path: String): String? {
        if (path in unreadable) return null
        return readText(path).also { if (it == null) unreadable += path }
    }

    private fun Long.toMb(): Int = (this / 1024).toInt()

    private class CpuTimes(val total: Long, val idle: Long) {
        fun loadSince(previous: CpuTimes): Int {
            val totalDelta = total - previous.total
            if (totalDelta <= 0L) return 0
            val busy = totalDelta - (idle - previous.idle)
            return (busy * 100 / totalDelta).toInt().coerceIn(0, 100)
        }
    }

    private companion object {
        const val PROC_STAT = "/proc/stat"
        const val PROC_MEMINFO = "/proc/meminfo"
        const val THERMAL_ZONES = "/sys/class/thermal"
        const val CPU_DIR = "/sys/devices/system/cpu"
        const val GPU_BUSY_PATH = "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"
        const val GPU_CLOCK_PATH = "/sys/class/kgsl/kgsl-3d0/gpuclk"
        const val CPUINFO_SERVICE = "cpuinfo"
        const val MAX_CPUINFO_LINES = 40
        const val TOP_PROCESSES = 5
        val MEMINFO_KEYS = setOf("MemTotal", "MemAvailable", "SwapTotal", "SwapFree")
        val WHITESPACE = Regex("""\s+""")
        // "  12% 1234/com.android.chrome: 8% user + 4% kernel"; "-" marks a process that exited.
        val PROCESS_LINE = Regex("""^ [ +](\d+(?:\.\d+)?)% \d+/(.+?): """)
    }
}
