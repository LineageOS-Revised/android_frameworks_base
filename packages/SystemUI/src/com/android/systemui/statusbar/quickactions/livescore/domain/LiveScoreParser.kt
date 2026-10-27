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

package com.android.systemui.statusbar.quickactions.livescore.domain

import com.android.systemui.statusbar.quickactions.delivery.domain.DeliveryParser

/** Notification text used to detect a live score. Plain strings so it is easy to unit test. */
data class LiveScoreInput(
    val packageName: String,
    val title: String?,
    val text: String?,
    val subText: String?,
    val bigText: String?,
    val shortCriticalText: String?,
    val category: String?,
    val isOngoing: Boolean,
    val isPromoted: Boolean,
    /** Determinate progress bar, call style or media style: never a score. */
    val isExcludedStyle: Boolean,
)

/** A score found in a notification. */
data class ParsedLiveScore(
    /** Matchup, e.g. "Arsenal vs Chelsea". */
    val title: String?,
    /** The score itself, e.g. "2 – 1" or "185/4 (18.2 ov)". */
    val score: String,
    /** Match status and other details, e.g. "75' · Premier League". */
    val subtitle: String?,
    /** Short text for the collapsed island, e.g. "ARS 2–1 CHE". */
    val compactText: String,
    /** False for one-off alerts (goal, full time). Those are only shown for a short while. */
    val isLive: Boolean,
)

/**
 * Finds live scores in notifications from sports apps.
 *
 * Most sports apps post a plain ongoing notification rather than an Android "Live Update", so the
 * score has to be read from the notification text. Known sports apps get looser matching; any
 * other app needs an ongoing notification with a clear "Team 2 - 1 Team" style score.
 */
object LiveScoreParser {

    /** Packages of dedicated sports apps. Matched exactly or as a prefix ending in '.'. */
    private val SPORTS_PACKAGES =
        setOf(
            "com.espn.score_center", // ESPN
            "com.mobilefootie.wc2010", // FotMob
            "de.motain.iliga", // OneFootball
            "com.sofascore.results", // SofaScore
            "com.livescore", // LiveScore
            "eu.livesport", // Flashscore and its regional variants
            "se.footballaddicts.livescore", // Forza Football
            "com.cricbuzz.android", // Cricbuzz
            "com.july.cricinfo", // ESPNcricinfo
            "com.fivemobile.thescore", // theScore
            "com.yahoo.mobile.client.android.sportacular", // Yahoo Sports
            "com.handmark.sportcaster", // CBS Sports
            "com.bleacherreport.android.teamstream", // Bleacher Report
            "com.nbaimd.gametime.nba2011", // NBA
            "com.gotv.nflgamecenter.us.lite", // NFL
            "com.bamnetworks.mobile.android.gameday.atbat", // MLB
            "com.nhl.gc1112.free", // NHL
        )

    /** Categories that are never sports scores. */
    private val EXCLUDED_CATEGORIES =
        setOf(
            "call",
            "missed_call",
            "msg",
            "email",
            "transport",
            "navigation",
            "progress",
            "alarm",
            "stopwatch",
            "location_sharing",
            "car_emergency",
            "car_warning",
            "car_information",
        )

    // "2 - 1", "2–1", "2:1". Not part of a longer number, date, decimal, percentage or time.
    private val PAIR_SCORE =
        Regex(
            """(?<![\d.:/,+$€£])(\d{1,3})\s*([-–—:])\s*(\d{1,3})(?![\d.:/%]|\s*(?:am|pm|AM|PM)\b)"""
        )

    // "Lakers 102, Celtics 98"
    private val COMMA_SCORE =
        Regex(
            """(\p{L}[\p{L}\p{M}.'&\- ]{0,30}?)\s+(\d{1,3}),\s+(\p{L}[\p{L}\p{M}.'&\- ]{0,30}?)\s+(\d{1,3})(?![\d.:/%])"""
        )

    // Cricket: "185/4" with optional overs "(18.2 ov)".
    private val CRICKET_SCORE =
        Regex(
            """(?<![\d/.])(\d{1,3})/(\d{1,2})(?![\d/.])(\s*\(\s*\d{1,2}(?:\.\d)?\s*(?:ov|overs?)?\s*\))?"""
        )

    private val VERSUS = Regex("""(?i)\s(?:vs\.?|v)\s|\s@\s""")
    private val MATCH_MINUTE = Regex("""\s*\b\d{1,3}(?:\+\d{1,2})?'$""")
    private val LEFT_DELIMITERS = charArrayOf('!', ':', '|', '·', '•', '\n')
    private val RIGHT_DELIMITERS = charArrayOf('!', ':', '|', '·', '•', '(', ',', '\n')
    private val WHITESPACE = Regex("""\s+""")
    private val DASH_MATCHUP = Regex("""^(\D{2,30}?)\s+[-–—]\s+(\D{2,30})$""")

    private const val MAX_COMPACT_LENGTH = 20
    private const val EN_DASH = "–"

    fun isSportsApp(packageName: String): Boolean =
        packageName in SPORTS_PACKAGES || SPORTS_PACKAGES.any { packageName.startsWith("$it.") }

    fun parse(input: LiveScoreInput): ParsedLiveScore? {
        if (input.isExcludedStyle ||
            input.category in EXCLUDED_CATEGORIES ||
            DeliveryParser.isDeliveryApp(input.packageName)
        ) {
            return null
        }
        val sportsApp = isSportsApp(input.packageName)
        val isLive = input.isOngoing || input.isPromoted

        // Other apps only count while ongoing, so news headlines with numbers don't show up.
        if (!sportsApp && !isLive) {
            return null
        }

        val title = input.title.clean()
        val text = input.text.clean()
        val bigText = input.bigText.clean()?.takeUnless { it == text }
        val subText = input.subText.clean()
        val fields = listOfNotNull(title, text, bigText, subText)

        val match =
            fields.firstNotNullOfOrNull { field -> findScore(field, sportsApp)?.takeIf { it.strong } }
                ?: fields.firstNotNullOfOrNull { field -> findScore(field, sportsApp) }

        if (match == null) {
            // A sports app's Live Update without a readable score: use its short critical text.
            val critical = input.shortCriticalText.clean()
            if (sportsApp && input.isPromoted && critical != null) {
                val matchup = title ?: text
                return ParsedLiveScore(
                    title = matchup,
                    score = critical,
                    subtitle = joinDetails(fields.filter { it != matchup }),
                    compactText = critical,
                    isLive = true,
                )
            }
            return null
        }

        if (!match.strong && !sportsApp) {
            return null
        }

        val matchup =
            match.matchup
                ?: fields.firstOrNull { it != match.field && VERSUS.containsMatchIn(it) }
                ?: fields.firstOrNull { it != match.field && it.any(Char::isLetter) }
        val details =
            listOfNotNull(match.status) +
                fields.filter { it != match.field && it != matchup }

        val compactText =
            match.compact?.takeIf { it.length <= MAX_COMPACT_LENGTH }
                ?: match.score.replace(" $EN_DASH ", EN_DASH)

        return ParsedLiveScore(
            title = matchup?.normalizeMatchup(),
            score = match.score,
            subtitle = joinDetails(details),
            compactText = compactText,
            isLive = isLive,
        )
    }

    private class ScoreMatch(
        val field: String,
        val score: String,
        /** True when the text clearly reads as a score, e.g. team names on both sides. */
        val strong: Boolean,
        val matchup: String?,
        val compact: String?,
        val status: String?,
    )

    private fun findScore(field: String, sportsApp: Boolean): ScoreMatch? {
        COMMA_SCORE.find(field)?.let { m ->
            val (home, homeScore, away, awayScore) = m.destructured
            val homeName = home.trim()
            val awayName = away.trim()
            return ScoreMatch(
                field = field,
                score = "$homeScore $EN_DASH $awayScore",
                strong = true,
                matchup = "$homeName vs $awayName",
                compact = "$homeName $homeScore$EN_DASH$awayScore $awayName",
                status =
                    joinDetails(
                        listOf(field.substring(0, m.range.first), field.substring(m.range.last + 1))
                    ),
            )
        }

        PAIR_SCORE.findAll(field).forEach { m ->
            val (home, separator, away) = m.destructured
            // "3:15" or "20:00" is a clock, not a score.
            if (separator == ":" && away.length == 2) return@forEach

            val before = field.substring(0, m.range.first)
            val after = field.substring(m.range.last + 1)
            val leftPart = before.splitAtLast(LEFT_DELIMITERS)
            val leftName = leftPart.second.cleanName()
            val rightPart = after.splitAtFirst(RIGHT_DELIMITERS)
            val rightName = rightPart.first.cleanName()
            val hasBothSides = leftName != null && rightName != null
            val score = "$home $EN_DASH $away"

            return if (hasBothSides) {
                ScoreMatch(
                    field = field,
                    score = score,
                    strong = true,
                    matchup = "$leftName vs $rightName",
                    compact = "$leftName $home$EN_DASH$away $rightName",
                    status =
                        joinDetails(
                            listOf(leftPart.first, rightPart.first.minuteOnly(), rightPart.second)
                        ),
                )
            } else {
                ScoreMatch(
                    field = field,
                    score = score,
                    strong = VERSUS.containsMatchIn(field),
                    matchup = field.takeIf { VERSUS.containsMatchIn(it) }?.let { stripScore(it, m.range) },
                    compact = null,
                    status = joinDetails(listOf(before, after)).takeUnless { VERSUS.containsMatchIn(field) },
                )
            }
        }

        CRICKET_SCORE.findAll(field).forEach { m ->
            val wickets = m.groupValues[2].toInt()
            val hasOvers = m.groupValues[3].isNotBlank()
            if (wickets > 10) return@forEach
            if (!hasOvers && !sportsApp) return@forEach
            val score = m.value.trim().replace(WHITESPACE, " ")
            val rest = stripScore(field, m.range)
            val restIsMatchup = rest != null && VERSUS.containsMatchIn(" $rest ")
            return ScoreMatch(
                field = field,
                score = score,
                strong = hasOvers,
                matchup = rest.takeIf { restIsMatchup },
                compact = null,
                status = rest.takeUnless { restIsMatchup },
            )
        }
        return null
    }

    private fun stripScore(field: String, range: IntRange): String? =
        (field.substring(0, range.first) + " " + field.substring(range.last + 1))
            .replace(WHITESPACE, " ")
            .trim(' ', '-', '–', '—', ',', ':', '|', '·', '•')
            .takeIf { it.isNotEmpty() }

    /**
     * Trims a team name; null if it doesn't look like one. Team names start with a capital (or a
     * caseless script), which keeps out text like "Uploading 3 - 5 photos".
     */
    private fun String.cleanName(): String? {
        val name = replace(MATCH_MINUTE, "").trim(' ', '-', '–', '—', ',', '.')
        val firstLetter = name.firstOrNull(Char::isLetter) ?: return null
        return name.takeIf { it.length in 2..30 && !firstLetter.isLowerCase() }
    }

    /** The trailing match minute of a right-hand team name, e.g. "75'" in "Chelsea 75'". */
    private fun String.minuteOnly(): String? = MATCH_MINUTE.find(this)?.value?.trim()

    /** Splits after the last delimiter: "GOAL! Arsenal" -> ("GOAL!", " Arsenal"). */
    private fun String.splitAtLast(delimiters: CharArray): Pair<String, String> {
        val index = lastIndexOfAny(delimiters)
        return if (index < 0) "" to this else substring(0, index + 1) to substring(index + 1)
    }

    /** Splits before the first delimiter: "Chelsea (75')" -> ("Chelsea ", "(75')"). */
    private fun String.splitAtFirst(delimiters: CharArray): Pair<String, String> {
        val index = indexOfAny(delimiters)
        return if (index < 0) this to "" else substring(0, index) to substring(index)
    }

    private fun joinDetails(parts: List<String?>): String? =
        parts
            .mapNotNull { part ->
                part?.trim(' ', '-', '–', '—', ',', ':', '|', '·', '•', '!', '(', ')')
                    ?.takeIf { it.isNotEmpty() }
            }
            .distinct()
            .joinToString(" · ")
            .takeIf { it.isNotEmpty() }

    /** "Inter - Milan" -> "Inter vs Milan". */
    private fun String.normalizeMatchup(): String =
        DASH_MATCHUP.matchEntire(this)?.let { "${it.groupValues[1]} vs ${it.groupValues[2]}" } ?: this

    private fun String?.clean(): String? =
        this?.replace(WHITESPACE, " ")?.trim()?.takeIf { it.isNotEmpty() }
}
