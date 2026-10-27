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

package com.android.systemui.statusbar.quickactions.delivery.domain

data class ParsedDelivery(
    val title: String?,
    val eta: String?,
    val stage: Int?,
    val progress: Float?,
    val subtitle: String?,
)

/** Reads order status from ongoing notifications of food and grocery delivery apps. */
object DeliveryParser {

    const val STAGE_PLACED = 0
    const val STAGE_PREPARING = 1
    const val STAGE_ON_THE_WAY = 2
    const val STAGE_ARRIVING = 3

    private val PACKAGES =
        setOf(
            "in.swiggy.android",
            "com.application.zomato",
            "com.grofers.customerapp",
            "com.zeptoconsumerapp",
            "com.ubercab.eats",
            "com.bigbasket.mobileapp",
            "com.dunzo.user",
            "com.done.faasos",
            "com.dd.doordash",
        )

    private val ETA_RANGE =
        Regex("""(?i)\b(\d{1,3})\s*(?:-|–|—|to)\s*(\d{1,3})\s*(?:mins?|minutes?)\b""")
    private val ETA_MINUTES = Regex("""(?i)\b(\d{1,3})\s*(?:mins?|minutes?)\b""")
    private val ETA_CLOCK = Regex("""(?i)\b(?:by|at|around)\s+(\d{1,2}[:.]\d{2}\s*(?:[ap]m)?)""")

    private val STAGES =
        listOf(
            STAGE_ARRIVING to
                Regex(
                    """(?i)\b(?:arriving|arrived|almost there|nearby|reach(?:ed|ing) (?:you|your)|at your (?:door|doorstep|location|gate))\b"""
                ),
            STAGE_ON_THE_WAY to
                Regex(
                    """(?i)\b(?:picked up|on (?:the|its|his|her) way|out for delivery|en route|heading (?:to|towards) you|dispatched)\b"""
                ),
            STAGE_PREPARING to
                Regex("""(?i)\b(?:preparing|being prepared|cooking|packing|packed|getting ready)\b"""),
            STAGE_PLACED to Regex("""(?i)\b(?:placed|confirmed|received|accepted)\b"""),
        )

    private val WHITESPACE = Regex("""\s+""")

    fun isDeliveryApp(packageName: String): Boolean = packageName in PACKAGES

    fun parse(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        shortCriticalText: String?,
        progress: Float?,
    ): ParsedDelivery? {
        if (packageName !in PACKAGES) return null
        val t = title.clean()
        val body = (bigText ?: text).clean()
        val fields = listOfNotNull(shortCriticalText.clean(), t, body)
        if (fields.isEmpty()) return null

        val eta = fields.firstNotNullOfOrNull(::findEta)
        val stage =
            fields.firstNotNullOfOrNull { f ->
                STAGES.firstOrNull { it.second.containsMatchIn(f) }?.first
            }
        if (eta == null && stage == null && progress == null) return null

        val status = t ?: body
        return ParsedDelivery(
            title = status,
            eta = eta,
            stage = stage,
            progress = progress,
            subtitle = body?.takeIf { it != status },
        )
    }

    private fun findEta(field: String): String? {
        ETA_RANGE.find(field)?.let {
            val (from, to) = it.destructured
            return if (from == to) "$from min" else "$from–$to min"
        }
        ETA_MINUTES.find(field)?.let {
            val minutes = it.groupValues[1].toInt()
            if (minutes > 0) return "$minutes min"
        }
        return ETA_CLOCK.find(field)?.let { it.groupValues[1].uppercase() }
    }

    private fun String?.clean(): String? =
        this?.replace(WHITESPACE, " ")?.trim()?.takeIf { it.isNotEmpty() }
}
