/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Host for the Material 3 Expressive year view.
 *
 * The drawer and top bar come from M3eHostActivity, which uses the same layouts and menus the
 * calendar itself does.
 */
package com.android.calendar.year.compose

import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import com.android.calendar.AllInOneActivity
import com.android.calendar.LunarUtils
import com.android.calendar.Utils
import com.android.calendar.chrome.M3eHostActivity
import com.android.calendar.settings.compose.SettingsRepository
import com.android.calendar.settings.compose.YEAR_SCROLL_VERTICAL
import com.android.calendar.settings.compose.yearScrollMode
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import ws.xsoh.etar.R

class ComposeYearActivity : M3eHostActivity() {

    override val checkedMenuItemId: Int = R.id.year_menu_item
    override val currentScreenName: String = "YEAR"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = SettingsRepository(this)

        // preferences_week_start_day: -1 means "follow the locale".
        val weekStartPref = repo.string(KEY_WEEK_START, "-1").toIntOrNull() ?: -1
        val firstDayOfWeek = if (weekStartPref == -1) {
            Calendar.getInstance().firstDayOfWeek
        } else {
            weekStartPref
        }
        val initialYear = LocalDate.now().year
        val showLunar = LunarUtils.showLunar(this)

        // Read once here rather than inside the screen: the setting is what chooses the mode, and a
        // change takes effect the next time the year view is opened.
        val horizontal = yearScrollMode(repo) != YEAR_SCROLL_VERTICAL

        setM3eContent {
            M3eYearScreen(
                initialYear = initialYear,
                firstDayOfWeek = firstDayOfWeek,
                showLunar = showLunar,
                horizontal = horizontal,
                // The toolbar is the app's own, so the year goes to the ActionBar title.
                onVisibleYearChanged = { year -> setPickerTitle(year.toString()) },
                onOpenMonth = { year, month -> openMonth(year, month) },
            )
        }
    }

    /** Opens the month view on the given month, reusing the running calendar activity. */
    private fun openMonth(year: Int, month: Int) {
        val millis = LocalDate.of(year, month, 1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        startActivity(
            Intent(this, AllInOneActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(Utils.INTENT_KEY_VIEW_TYPE, Utils.INTENT_VALUE_VIEW_TYPE_MONTH)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, millis)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }

    private companion object {
        const val KEY_WEEK_START = "preferences_week_start_day"
    }
}
