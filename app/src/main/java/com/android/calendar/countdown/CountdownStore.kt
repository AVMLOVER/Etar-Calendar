/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Storage and the date arithmetic behind the countdown ("倒数日") screen.
 *
 * Countdowns are kept in SharedPreferences as JSON rather than in a table of their own. They are a
 * handful of records that only this app reads, they need no querying, and the calendar provider is
 * not the right place for something that is not a calendar event - an event is only created when the
 * user asks for one when adding a countdown.
 */
package com.android.calendar.countdown

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** A countdown the user is tracking. */
data class Countdown(
    val id: String,
    val name: String,
    /** ARGB colour the card and its widget are drawn in. */
    val color: Int,
    /** The target date, as an ISO-8601 yyyy-MM-dd string so it survives storage without a zone. */
    val dateIso: String,
    /** The target time, or null when only a date was given. Also ISO-8601 (HH:mm). */
    val timeIso: String?,
    /** Repeat every year. Only meaningful for a date that has not passed yet. */
    val repeatYearly: Boolean,
    /**
     * The calendar event this countdown is paired with, or null.
     *
     * Set when the countdown was created from the add dialog with "add to calendar" ticked, so the
     * two can be deleted together and so a list row can say whether one exists. Null for a countdown
     * that was never given an event, and for entries written before this field existed.
     */
    val eventId: Long? = null,
    /**
     * A picture to use behind this countdown instead of its flat colour, or null.
     *
     * Held as a string because that is what survives storage, and read with a persisted permission
     * grant - see CountdownBackgroundPicker - so it still resolves after a restart.
     */
    val backgroundImageUri: String? = null,
    /**
     * True to let a background picture show through in the widget, with only the text drawn over it.
     *
     * The alternative is the picture sitting behind the countdown's colour, which is what the card
     * does on screen. Both are wanted: a photograph reads better bare, a busy one reads better
     * tinted. Only meaningful when there is a picture.
     */
    val transparentBackground: Boolean = true,
) {
    val date: LocalDate get() = LocalDate.parse(dateIso)
    val time: LocalTime? get() = timeIso?.let { LocalTime.parse(it) }

    /** The moment this countdown points at, in the given zone. */
    fun targetMillis(zone: ZoneId): Long =
        LocalDateTime.of(date, time ?: LocalTime.MIDNIGHT)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    /**
     * The moment to count towards now.
     *
     * For a repeating countdown whose date has passed, that is the same day and time next year -
     * which is what makes an annual countdown roll over instead of sitting at "passed".
     */
    fun effectiveTarget(zone: ZoneId, now: Long): Long {
        val thisYear = targetMillis(zone)
        if (!repeatYearly || thisYear > now) {
            return thisYear
        }
        var candidate = date
        var millis = thisYear
        // A loop rather than a single plusYears, so a date that is somehow years behind still lands
        // in the future instead of stopping one year short. The comparison matches describe()'s: a
        // target that has been reached counts as needing the next occurrence.
        while (millis <= now) {
            candidate = candidate.plusYears(1)
            millis = LocalDateTime.of(candidate, time ?: LocalTime.MIDNIGHT)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }
        return millis
    }
}

/** The units a countdown can be shown in. */
enum class CountdownUnit {
    MINUTES, HOURS, DAYS, MONTHS, YEARS;

    companion object {
        /** The default, and the fallback when a stored value is not recognised. */
        val DEFAULT = DAYS

        fun fromKey(key: String?): CountdownUnit =
            entries.firstOrNull { it.name == key } ?: DEFAULT
    }
}

/** Which setting controls how a countdown is drawn in the list. */
enum class CountdownDisplay {
    /** Show the configured unit, stepping down when the remainder is smaller than one unit. */
    CONFIGURED,

    /** Let the card choose the most readable unit on its own. */
    AUTO,
}

/** One countdown resolved for display: whether it has passed, and the number and unit to print. */
data class CountdownState(
    val countdown: Countdown,
    val passed: Boolean,
    /** The number, already formatted - "12", "3.5". */
    val value: String,
    /** The unit that number is in. */
    val unit: CountdownUnit,
    /** The next annual occurrence, for a repeating countdown that has passed. */
    val repeated: Boolean,
)

object CountdownStore {

    private const val PREFS = "m3e_countdowns"
    private const val KEY_ITEMS = "items"

    /**
     * Colours offered for a new countdown.
     *
     * The same Material palette the app's other pickers use, so a countdown and an event can be given
     * the same colour and read as related.
     */
    val PALETTE = listOf(
        0xFFE57373.toInt(), 0xFFF06292.toInt(), 0xFFBA68C8.toInt(), 0xFF9575CD.toInt(),
        0xFF7986CB.toInt(), 0xFF64B5F6.toInt(), 0xFF4FC3F7.toInt(), 0xFF4DD0E1.toInt(),
        0xFF4DB6AC.toInt(), 0xFF81C784.toInt(), 0xFFFFB74D.toInt(), 0xFFA1887F.toInt(),
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<Countdown> {
        val raw = prefs(context).getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val dateIso = o.optString("date").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                Countdown(
                    id = id,
                    name = o.optString("name"),
                    color = o.optInt("color", PALETTE.first()),
                    dateIso = dateIso,
                    timeIso = o.optString("time").takeIf { it.isNotEmpty() },
                    repeatYearly = o.optBoolean("repeat", false),
                    // Absent in entries written before the pair existed, which is why it is read as
                    // "no event" rather than treated as corrupt.
                    eventId = o.optLong("eventId", -1L).takeIf { it > 0L },
                    backgroundImageUri = o.optString("background").takeIf { it.isNotEmpty() },
                    // Absent in entries written before the switch existed, where the bare picture was
                    // the only behaviour, so that is the default read back.
                    transparentBackground = o.optBoolean("transparent", true),
                )
            }
        } catch (t: Throwable) {
            // A corrupt store must not take the screen down; an empty list is recoverable.
            emptyList()
        }
    }

    fun save(context: Context, items: List<Countdown>) {
        val array = JSONArray()
        items.forEach { c ->
            array.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("color", c.color)
                    put("date", c.dateIso)
                    put("time", c.timeIso ?: "")
                    put("repeat", c.repeatYearly)
                    put("eventId", c.eventId ?: -1L)
                    put("background", c.backgroundImageUri ?: "")
                    put("transparent", c.transparentBackground)
                }
            )
        }
        prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    fun add(context: Context, countdown: Countdown) {
        save(context, all(context) + countdown)
    }

    fun update(context: Context, countdown: Countdown) {
        save(context, all(context).map { if (it.id == countdown.id) countdown else it })
    }

    fun remove(context: Context, id: String) {
        save(context, all(context).filterNot { it.id == id })
    }

    /** The countdown paired with a calendar event, if there is one. */
    fun forEvent(context: Context, eventId: Long): Countdown? =
        all(context).firstOrNull { it.eventId == eventId }

    /**
     * The countdown paired with an event, falling back to a match on name and date.
     *
     * The stored id is the reliable link and is tried first. The fallback exists because a countdown
     * written before the pairing was recorded - or by a path that failed to record it - otherwise
     * looks unpaired forever, and the user has no way to repair that short of deleting and recreating
     * it. Name and date together are specific enough that a wrong match is very unlikely, and a wrong
     * match only ever offers to delete a countdown the user can decline.
     */
    fun forEventOrSimilar(
        context: Context,
        eventId: Long,
        eventTitle: String?,
        eventStartMillis: Long,
        zone: java.time.ZoneId,
    ): Countdown? {
        forEvent(context, eventId)?.let { return it }
        val title = eventTitle?.trim().orEmpty()
        if (title.isEmpty()) {
            return null
        }
        val date = java.time.Instant.ofEpochMilli(eventStartMillis).atZone(zone).toLocalDate()
        return all(context).firstOrNull {
            // Only an unpaired one: adopting a countdown that already belongs to another event would
            // miscount what is being deleted.
            it.eventId == null && it.name.trim() == title && it.date == date
        }
    }

    /**
     * Records the link on a countdown that matched only by name and date.
     *
     * Called once the pairing has been established, so the next lookup does not have to guess again.
     */
    fun adoptEvent(context: Context, countdownId: String, eventId: Long) {
        save(
            context,
            all(context).map {
                if (it.id == countdownId && it.eventId == null) it.copy(eventId = eventId) else it
            },
        )
    }

    /** Breaks the pairing without touching either record. */
    fun unlinkEvent(context: Context, eventId: Long) {
        save(context, all(context).map { if (it.eventId == eventId) it.copy(eventId = null) else it })
    }

    // --------------------------------------------------------------------------------- ordering

    /**
     * How the countdown list is ordered.
     *
     * The two added orders are the stored order and its reverse: [ADDED_REVERSED] puts the newest
     * entry first, which is what "order added" means to a reader - the thing just added is at the top
     * - while [ADDED] keeps the oldest first.
     *
     * [TIME_ASC] and [TIME_DESC] order by the moment each countdown points at - the next occurrence
     * for a repeating one, so a birthday that has already been this year sorts by when it comes round
     * again rather than by the year it was first entered.
     */
    enum class SortOrder {
        ADDED_REVERSED, ADDED, NAME, TIME_MIXED, TIME_ASC, TIME_DESC;

        companion object {
            /** The default, and the fallback when a stored value is not recognised. */
            val DEFAULT = TIME_ASC

            fun fromKey(key: String?): SortOrder =
                entries.firstOrNull { it.name == key } ?: DEFAULT
        }
    }

    /**
     * Orders a list for display.
     *
     * TIME_MIXED ignores direction entirely: every countdown is measured in days and ordered by how
     * far away it is, so the nearest - past or future - comes first and the most distant last.
     */
    fun sorted(
        items: List<Countdown>,
        order: SortOrder,
        zone: ZoneId,
        now: Long,
    ): List<Countdown> = when (order) {
        // Newest first, which is the order the list is stored in reversed.
        SortOrder.ADDED_REVERSED -> items.asReversed()
        SortOrder.ADDED -> items
        SortOrder.NAME -> items.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
        SortOrder.TIME_MIXED -> items.sortedBy { countdown ->
            kotlin.math.abs(daysFromNow(countdown, zone, now))
        }
        SortOrder.TIME_ASC -> items.sortedBy { daysFromNow(it, zone, now) }
        SortOrder.TIME_DESC -> items.sortedByDescending { daysFromNow(it, zone, now) }
    }

    /**
     * How many days away a countdown is, negative when it has passed.
     *
     * Whole days between the two dates, so a countdown later today is 0 rather than a fraction, and
     * the ordering does not shift with the time of day.
     */
    private fun daysFromNow(countdown: Countdown, zone: ZoneId, now: Long): Long {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val target = java.time.Instant.ofEpochMilli(
            countdown.effectiveTarget(zone, now)
        ).atZone(zone).toLocalDate()
        return java.time.temporal.ChronoUnit.DAYS.between(today, target)
    }
    /** A fresh id. */
    fun newId(): String = UUID.randomUUID().toString()

    /**
     * A colour for a new countdown: the first palette entry not already in use.
     *
     * Counting from a stored "last colour" did not work, because the add dialog asks for a colour
     * every time it is composed - so merely opening the dialog advanced the counter, and the colour
     * the user actually saved was not the one the counter had moved past. Looking at what is in the
     * list is both simpler and gives what was asked for: every countdown gets its own colour until
     * the palette runs out, and only then does it start again from the beginning.
     */
    fun nextColor(context: Context, existing: List<Countdown> = all(context)): Int {
        val used = existing.map { it.color }.toSet()
        return PALETTE.firstOrNull { it !in used } ?: PALETTE[existing.size % PALETTE.size]
    }

    // ------------------------------------------------------------------------------ arithmetic

    /**
     * Resolve a countdown for display.
     *
     * Stepping down matters more than the configured unit: a countdown set to days that is only an
     * hour away would otherwise read "0 天" for the whole of its last day. When the remainder is
     * smaller than the configured unit, the next unit down is used instead.
     */
    fun describe(
        countdown: Countdown,
        zone: ZoneId,
        now: Long,
        unit: CountdownUnit,
        allowDecimals: Boolean,
        countToday: Boolean,
    ): CountdownState {
        val rawTarget = countdown.targetMillis(zone)
        // The occurrence being counted towards: for a repeating countdown whose date has gone, next
        // year's. Everything below is about that occurrence, not the date as first stored.
        val target = countdown.effectiveTarget(zone, now)

        // Whether it has arrived is decided by the day, not by the millisecond.
        //
        // A countdown with no time points at midnight of the chosen day, so compared to the clock it
        // was "passed" from one second after midnight - and because the configured unit is usually
        // days, the elapsed time floored to 0 and the card read "0 days left" for the whole of the
        // day the user was counting towards. Taking the start of that day as the boundary makes the
        // day itself read "passed, 0 days", which is what arriving means.
        val boundary = if (countdown.time == null) {
            java.time.Instant.ofEpochMilli(target).atZone(zone).toLocalDate()
                .atStartOfDay(zone).toInstant().toEpochMilli()
        } else {
            target
        }
        val passed = boundary <= now
        // A repeating countdown shows that it is counting towards next year's occurrence.
        val repeated = countdown.repeatYearly && rawTarget <= now
        val ordered = if (passed) now - target else target - now

        val chosen = if (passed) {
            // Counting up from the target: always in the unit the user configured. "0 days ago" on
            // the day itself and "1 day ago" from the next day is what that unit means, and it is
            // stable - stepping down to hours would turn it into "59 minutes ago" an hour after the
            // day began and back into "1 day" later.
            unit
        } else {
            configuredOrSmaller(ordered, unit)
        }

        return CountdownState(
            countdown = countdown,
            passed = passed,
            value = format(ordered, chosen, allowDecimals, passed, countToday),
            unit = chosen,
            repeated = repeated,
        )
    }

    /** Whole [unit]s in a span, using the same divisors the display does. */
    private fun wholeUnits(millis: Long, unit: CountdownUnit): Double = when (unit) {
        CountdownUnit.MINUTES -> millis / 60_000.0
        CountdownUnit.HOURS -> millis / 3_600_000.0
        CountdownUnit.DAYS -> millis / DAY_MILLIS.toDouble()
        CountdownUnit.MONTHS -> millis / (DAY_MILLIS * 30.0)
        CountdownUnit.YEARS -> millis / (DAY_MILLIS * 365.0)
    }

    /** The configured unit when the remainder fills it, otherwise the next unit down. */
    private fun configuredOrSmaller(millis: Long, unit: CountdownUnit): CountdownUnit {
        if (unit == CountdownUnit.MINUTES) return CountdownUnit.MINUTES
        val bigger = when (unit) {
            CountdownUnit.YEARS -> millis >= DAY_MILLIS * 365
            CountdownUnit.MONTHS -> millis >= DAY_MILLIS * 30
            CountdownUnit.DAYS -> millis >= DAY_MILLIS
            CountdownUnit.HOURS -> millis >= HOUR_MILLIS
            CountdownUnit.MINUTES -> true
        }
        if (bigger) return unit
        // Too small for the configured unit: step down one at a time until something fits.
        var candidate = when (unit) {
            CountdownUnit.YEARS -> CountdownUnit.MONTHS
            CountdownUnit.MONTHS -> CountdownUnit.DAYS
            CountdownUnit.DAYS -> CountdownUnit.HOURS
            CountdownUnit.HOURS -> CountdownUnit.MINUTES
            CountdownUnit.MINUTES -> CountdownUnit.MINUTES
        }
        return configuredOrSmaller(millis, candidate)
    }

    /** The smallest unit that still leaves a whole number, used once the date has passed. */
    private fun smallestFit(millis: Long, configured: CountdownUnit): CountdownUnit = when {
        configured == CountdownUnit.MINUTES -> CountdownUnit.MINUTES
        millis < HOUR_MILLIS -> CountdownUnit.MINUTES
        millis < DAY_MILLIS -> CountdownUnit.HOURS
        configured == CountdownUnit.HOURS -> CountdownUnit.HOURS
        millis < DAY_MILLIS * 30 -> CountdownUnit.DAYS
        configured == CountdownUnit.DAYS -> CountdownUnit.DAYS
        millis < DAY_MILLIS * 365 -> CountdownUnit.MONTHS
        else -> CountdownUnit.YEARS
    }

    private fun format(
        millis: Long,
        unit: CountdownUnit,
        allowDecimals: Boolean,
        passed: Boolean,
        countToday: Boolean,
    ): String {
        var raw = when (unit) {
            CountdownUnit.MINUTES -> millis / 60_000.0
            CountdownUnit.HOURS -> millis / 3_600_000.0
            CountdownUnit.DAYS -> millis / DAY_MILLIS.toDouble()
            CountdownUnit.MONTHS -> millis / (DAY_MILLIS * 30.0)
            CountdownUnit.YEARS -> millis / (DAY_MILLIS * 365.0)
        }

        // "Count today" makes the current day count as one whole unit, the way the date calculator's
        // inclusive mode does. Day-and-larger units only: an hour is not a day.
        if (countToday && !passed &&
            (unit == CountdownUnit.DAYS || unit == CountdownUnit.MONTHS || unit == CountdownUnit.YEARS)
        ) {
            raw += 1.0
        }

        return if (allowDecimals) {
            // Rounded to the precision this unit can meaningfully carry, at most three places.
            //
            // Three places on days would read "245.917 天", which claims a precision of seconds on a
            // unit that is a day long. The finer the unit, the more of its fraction is worth showing.
            val places = when (unit) {
                CountdownUnit.MINUTES, CountdownUnit.HOURS -> 3
                CountdownUnit.DAYS -> 1
                CountdownUnit.MONTHS, CountdownUnit.YEARS -> 2
            }
            val scale = when (places) {
                1 -> 10.0
                2 -> 100.0
                else -> 1000.0
            }
            val rounded = Math.round(raw * scale) / scale
            if (rounded == Math.floor(rounded)) {
                rounded.toLong().toString()
            } else {
                rounded.toString()
            }
        } else {
            Math.floor(raw).toLong().toString()
        }
    }

    const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    const val HOUR_MILLIS = 60L * 60L * 1000L

    /** The format the card prints a date in. */
    val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
}
