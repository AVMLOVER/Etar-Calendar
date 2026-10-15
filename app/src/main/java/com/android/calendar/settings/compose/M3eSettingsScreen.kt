/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The Material 3 Expressive calendar settings screen.
 *
 * It reads and writes exactly the same SharedPreferences keys as the classic
 * PreferenceFragmentCompat screens, so existing user configuration carries over untouched.
 */
package com.android.calendar.settings.compose

import androidx.compose.ui.res.painterResource
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.SearchRecentSuggestions
import android.widget.Toast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.android.calendar.chrome.compose.M3eEdgeToEdgeContent
import com.android.calendar.chrome.compose.m3eSystemBarPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.android.calendar.CalendarRecentSuggestionsProvider
import com.android.calendar.Utils
import ws.xsoh.etar.R

// Preference keys - deliberately identical to the classic preference screens.
private const val KEY_THEME = "pref_theme"
private const val KEY_REAL_EVENT_COLORS = "pref_real_event_colors"
private const val KEY_SHOW_LUNAR = "pref_show_lunar"
private const val KEY_SHOW_WEEK_NUM = "preferences_show_week_num"
private const val KEY_HIDE_DECLINED = "preferences_hide_declined"
private const val KEY_STAGGERED = "preferences_staggered_display"
private const val KEY_WEEK_START = "preferences_week_start_day"
private const val KEY_DAYS_PER_WEEK = "preferences_days_per_week"
private const val KEY_DURATION = "preferences_default_event_duration"

// Month view layout (was the separate "view details" screen)
private const val KEY_LINES_V = "pref_number_of_lines_vertical"
private const val KEY_TIME_V = "pref_display_time_vertical"
private const val KEY_LOCATION_V = "pref_display_location_vertical"
private const val KEY_LINES_H = "pref_number_of_lines_horizontal"
private const val KEY_TIME_H = "pref_display_time_horizontal"
private const val KEY_LOCATION_H = "pref_display_location_horizontal"

// Notifications & reminders
private const val KEY_ALERTS = "preferences_alerts"
private const val KEY_ALERTS_VIBRATE = "preferences_alerts_vibrate"
private const val KEY_ALERTS_POPUP = "preferences_alerts_popup"
private const val KEY_SNOOZE = "preferences_default_snooze_delay"

// Other
private const val KEY_HOME_TZ = "preferences_home_tz_enabled"
private const val KEY_BATTERY = "pref_do_not_check_battery_optimization"
private const val KEY_HOME_TZ_VALUE = "preferences_home_tz"
private const val KEY_CLEAR_HISTORY = "preferences_clear_search_history"


/**
 * Year view scrolling. Not a key any classic preference screen had, so it is read straight from
 * SharedPreferences by the year screen through SettingsRepository.
 */
const val KEY_YEAR_SCROLL = "pref_year_scroll"
const val YEAR_SCROLL_HORIZONTAL = "horizontal"
const val YEAR_SCROLL_VERTICAL = "vertical"

/** Read by ComposeYearActivity, so it lives next to the setting that writes it. */
fun yearScrollMode(repo: SettingsRepository): String =
    repo.string(KEY_YEAR_SCROLL, YEAR_SCROLL_HORIZONTAL)

// Remaining notification options carried over from the classic screen
private const val KEY_RINGTONE = "preferences_alerts_ringtone"
private const val KEY_CUSTOM_SNOOZE = "preferences_custom_snooze_delay"
private const val KEY_DEFAULT_REMINDER = "preferences_default_reminder"
private const val KEY_SKIP_REMINDERS = "preferences_reminders_responded"
private const val KEY_DEFAULT_VIEW = "preferences_default_start"

/** Sentinel used by the platform for "no reminder" (Integer.MIN_VALUE). */
private const val NO_REMINDER = "-2147483648"

private val DEFAULT_VIEW_VALUES = listOf("-2", "2", "3", "4", "1")
private val REMINDER_VALUES = listOf(
    NO_REMINDER, "0", "1", "5", "10", "15", "20", "25", "30", "45", "60",
    "120", "180", "360", "540", "720", "1440", "2880", "10080", "20160", "40320",
)
private val SKIP_REMINDER_VALUES = listOf("declined", "not_responded")

private val NUMBER_OF_LINES = listOf("1", "2", "3", "4", "5", "6", "7")
private val SHOW_TIME_VALUES = listOf("0", "1", "2", "3", "4")
private val SNOOZE_VALUES =
    listOf("-1", "5", "10", "15", "20", "25", "30", "45", "60", "120", "180", "720")

private val WEEK_START_VALUES = listOf("-1", "1", "2", "3", "4", "5", "6", "7")
private val DAYS_PER_WEEK_VALUES = listOf("2", "3", "4", "5", "6", "7")
private val DURATION_VALUES = listOf("0", "15", "30", "60", "90", "120")

/** Horizontal first: it is the default the year view opens with. */
private val YEAR_SCROLL_VALUES = listOf(YEAR_SCROLL_HORIZONTAL, YEAR_SCROLL_VERTICAL)

/**
 * @param repo backing store over the app's SharedPreferences.
 * @param darkTheme whether the app is currently in night mode (drives the preview palette).
 * @param onBack invoked when the user navigates up.
 * @param onOpenLegacySettings opens the classic PreferenceFragmentCompat screen, which still
 *        hosts the long tail of options that this screen does not (yet) cover.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun M3eSettingsScreen(
    repo: SettingsRepository,
    darkTheme: Boolean,
    onBack: () -> Unit,
    onOpenCalendars: () -> Unit = {},
    onThemeModeChanged: (String) -> Unit = {},
) {
    val context = LocalContext.current

    var showImportDialog by remember { mutableStateOf(false) }

    if (showImportDialog) {
        // Shared with the overflow menu's import entry, which offers the same two routes.
        com.android.calendar.chrome.compose.IcsImportDialog(
            onDismiss = { showImportDialog = false },
        )
    }

    // Local mirrors of the stored values, so the UI reacts immediately on change.
    var theme by remember { mutableStateOf(repo.string(KEY_THEME, "system")) }
    var realEventColors by remember { mutableStateOf(repo.bool(KEY_REAL_EVENT_COLORS, false)) }
    var showLunar by remember {
        mutableStateOf(repo.bool(KEY_SHOW_LUNAR, com.android.calendar.LunarUtils.defaultShowLunar()))
    }
    // Week numbers are on by default, matching GeneralPreferences.DEFAULT_SHOW_WEEK_NUM - a fresh
    // install shows them until the user turns the switch off.
    var showWeekNum by remember { mutableStateOf(repo.bool(KEY_SHOW_WEEK_NUM, true)) }
    var hideDeclined by remember { mutableStateOf(repo.bool(KEY_HIDE_DECLINED, false)) }
    var staggered by remember { mutableStateOf(repo.bool(KEY_STAGGERED, false)) }
    var weekStart by remember { mutableStateOf(repo.string(KEY_WEEK_START, "-1")) }
    var daysPerWeek by remember { mutableStateOf(repo.string(KEY_DAYS_PER_WEEK, "7")) }
    var duration by remember { mutableStateOf(repo.string(KEY_DURATION, "60")) }

    // Month view layout - these six used to live on the separate "view details" screen.
    var linesVertical by remember { mutableStateOf(repo.string(KEY_LINES_V, "7")) }
    var timeVertical by remember { mutableStateOf(repo.string(KEY_TIME_V, "0")) }
    var locationVertical by remember { mutableStateOf(repo.bool(KEY_LOCATION_V, false)) }
    var linesHorizontal by remember { mutableStateOf(repo.string(KEY_LINES_H, "7")) }
    var timeHorizontal by remember { mutableStateOf(repo.string(KEY_TIME_H, "0")) }
    var locationHorizontal by remember { mutableStateOf(repo.bool(KEY_LOCATION_H, false)) }

    // Notifications & reminders
    var alerts by remember { mutableStateOf(repo.bool(KEY_ALERTS, true)) }
    var vibrate by remember { mutableStateOf(repo.bool(KEY_ALERTS_VIBRATE, false)) }
    var popup by remember { mutableStateOf(repo.bool(KEY_ALERTS_POPUP, false)) }
    var snooze by remember { mutableStateOf(repo.string(KEY_SNOOZE, "5")) }

    // Other
    var homeTz by remember { mutableStateOf(repo.bool(KEY_HOME_TZ, false)) }
    var battery by remember { mutableStateOf(repo.bool(KEY_BATTERY, false)) }
    var homeTzValue by remember { mutableStateOf(repo.string(KEY_HOME_TZ_VALUE, "")) }

    // Countdown display options. Each default is off, so a fresh install shows whole units, does not
    // count the current day, and does not tick every second.
    var countdownDynamic by remember {
        mutableStateOf(
            repo.bool(com.android.calendar.countdown.compose.KEY_DYNAMIC, false)
        )
    }
    var countdownCountToday by remember {
        mutableStateOf(
            repo.bool(com.android.calendar.countdown.compose.KEY_COUNT_TODAY, false)
        )
    }
    var countdownDecimals by remember {
        mutableStateOf(
            repo.bool(com.android.calendar.countdown.compose.KEY_DECIMALS, false)
        )
    }
    // Ordering of the countdown list. Stored as the enum name so an unknown value can fall back to
    // the default rather than breaking the screen.
    var countdownSort by remember {
        mutableStateOf(
            com.android.calendar.countdown.CountdownStore.SortOrder.fromKey(
                repo.string(
                    com.android.calendar.countdown.compose.KEY_SORT,
                    com.android.calendar.countdown.CountdownStore.SortOrder.DEFAULT.name,
                )
            ).name
        )
    }

    // Remaining notification options, carried over from the classic settings screen
    var ringtone by remember { mutableStateOf(repo.string(KEY_RINGTONE, "")) }
    var customSnooze by remember { mutableStateOf(repo.bool(KEY_CUSTOM_SNOOZE, false)) }
    var defaultReminder by remember {
        mutableStateOf(repo.string(KEY_DEFAULT_REMINDER, "10"))
    }
    var skipReminders by remember {
        mutableStateOf(repo.string(KEY_SKIP_REMINDERS, "declined"))
    }
    var defaultView by remember { mutableStateOf(repo.string(KEY_DEFAULT_VIEW, "-2")) }
    var yearScroll by remember { mutableStateOf(yearScrollMode(repo)) }

    M3eEdgeToEdgeContent {
    Scaffold(
        modifier = Modifier.m3eSystemBarPadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preferences_title)) },
                navigationIcon = {
                    // A back arrow, like the other screens reached from the menu, rather than a button
                    // captioned "back": the arrow alone is what the platform does and what the rest of
                    // this app does.
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.m3e_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // The navigation bar inset goes into the content padding rather than into a modifier, so
            // the list scrolls behind the gesture bar and its last row still comes to rest above it.
            // Added by hand because PaddingValues has no plus operator.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 32.dp + androidx.compose.foundation.layout.WindowInsets.navigationBars
                    .asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------------------------------------------------------------- appearance
            item { SectionTitle(stringResource(R.string.m3e_section_appearance)) }
            item {
                SettingsCard {
                    Text(
                        text = stringResource(R.string.m3e_theme_mode),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                    ThemeModeButtonGroup(
                        selected = theme,
                        onSelect = {
                            theme = it
                            repo.setString(KEY_THEME, it)
                            onThemeModeChanged(it)
                        },
                    )
                }
            }
            item {
                MonetCard(darkTheme = darkTheme)
            }
            item {
                SettingsCard {
                    // No pure-black switch here: the theme group above already offers Black, and a
                    // second control for the same outcome is a second thing that can disagree with
                    // the first.
                    SwitchRow(
                        title = stringResource(R.string.preferences_real_event_colors),
                        checked = realEventColors,
                        onCheckedChange = {
                            realEventColors = it; repo.setBool(KEY_REAL_EVENT_COLORS, it)
                        },
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = stringResource(R.string.preferences_show_lunar_title),
                        summary = stringResource(R.string.preferences_show_lunar_summary),
                        checked = showLunar,
                        onCheckedChange = { showLunar = it; repo.setBool(KEY_SHOW_LUNAR, it) },
                    )
                }
            }

            // --------------------------------------------------------------------- views
            item { SectionTitle(stringResource(R.string.m3e_section_views)) }
            item {
                SettingsCard {
                    SwitchRow(
                        title = stringResource(R.string.preferences_show_week_num_title),
                        checked = showWeekNum,
                        onCheckedChange = { showWeekNum = it; repo.setBool(KEY_SHOW_WEEK_NUM, it) },
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = stringResource(R.string.preferences_hide_declined_title),
                        checked = hideDeclined,
                        onCheckedChange = { hideDeclined = it; repo.setBool(KEY_HIDE_DECLINED, it) },
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = stringResource(R.string.preferences_staggered_display_title),
                        summary = stringResource(R.string.preferences_staggered_display_summary),
                        checked = staggered,
                        onCheckedChange = { staggered = it; repo.setBool(KEY_STAGGERED, it) },
                    )
                    HorizontalDivider()
                    OptionRow(
                        title = stringResource(R.string.preferences_week_start_day_title),
                        value = weekStart,
                        options = WEEK_START_VALUES,
                        labelOf = { weekStartLabel(it) },
                        onSelect = { weekStart = it; repo.setString(KEY_WEEK_START, it) },
                    )
                    HorizontalDivider()
                    OptionRow(
                        title = stringResource(R.string.preferences_days_per_week_title),
                        value = daysPerWeek,
                        options = DAYS_PER_WEEK_VALUES,
                        labelOf = { it },
                        onSelect = { daysPerWeek = it; repo.setString(KEY_DAYS_PER_WEEK, it) },
                    )
                    HorizontalDivider()
                    OptionRow(
                        title = stringResource(R.string.default_start_title),
                        value = defaultView,
                        options = DEFAULT_VIEW_VALUES,
                        labelOf = { defaultViewLabel(it) },
                        onSelect = { defaultView = it; repo.setString(KEY_DEFAULT_VIEW, it) },
                    )
                }
            }

            // ----------------------------------------------------------------- year view
            // Its own card rather than another row in Views: the choice is about one screen only.
            item { SectionTitle(stringResource(R.string.m3e_section_year_view)) }
            item {
                SettingsCard {
                    OptionRow(
                        title = stringResource(R.string.m3e_year_scroll_title),
                        summary = stringResource(R.string.m3e_year_scroll_summary),
                        value = yearScroll,
                        options = YEAR_SCROLL_VALUES,
                        labelOf = { yearScrollLabel(it) },
                        onSelect = { yearScroll = it; repo.setString(KEY_YEAR_SCROLL, it) },
                    )
                }
            }

            // -------------------------------------------------------------------- events
            item { SectionTitle(stringResource(R.string.m3e_section_events)) }
            item {
                SettingsCard {
                    OptionRow(
                        title = stringResource(R.string.preferences_default_event_duration_title),
                        value = duration,
                        options = DURATION_VALUES,
                        labelOf = { durationLabel(it) },
                        onSelect = { duration = it; repo.setString(KEY_DURATION, it) },
                    )
                }
            }

            // ------------------------------------------- month view layout (view details)
            item { SectionTitle(stringResource(R.string.m3e_section_month_view)) }
            item {
                SettingsCard {
                    Text(
                        text = stringResource(R.string.preferences_month_view_portrait),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                    OptionRow(
                        title = stringResource(R.string.maximum_number_of_lines),
                        value = linesVertical,
                        options = NUMBER_OF_LINES,
                        labelOf = { it },
                        onSelect = { linesVertical = it; repo.setString(KEY_LINES_V, it) },
                    )
                    HorizontalDivider()
                    OptionRow(
                        title = stringResource(R.string.display_time),
                        value = timeVertical,
                        options = SHOW_TIME_VALUES,
                        labelOf = { showTimeLabel(it) },
                        onSelect = { timeVertical = it; repo.setString(KEY_TIME_V, it) },
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = stringResource(R.string.display_location),
                        checked = locationVertical,
                        onCheckedChange = {
                            locationVertical = it; repo.setBool(KEY_LOCATION_V, it)
                        },
                    )
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.preferences_month_view_landscape),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                    OptionRow(
                        title = stringResource(R.string.maximum_number_of_lines),
                        value = linesHorizontal,
                        options = NUMBER_OF_LINES,
                        labelOf = { it },
                        onSelect = { linesHorizontal = it; repo.setString(KEY_LINES_H, it) },
                    )
                    HorizontalDivider()
                    OptionRow(
                        title = stringResource(R.string.display_time),
                        value = timeHorizontal,
                        options = SHOW_TIME_VALUES,
                        labelOf = { showTimeLabel(it) },
                        onSelect = { timeHorizontal = it; repo.setString(KEY_TIME_H, it) },
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = stringResource(R.string.display_location),
                        checked = locationHorizontal,
                        onCheckedChange = {
                            locationHorizontal = it; repo.setBool(KEY_LOCATION_H, it)
                        },
                    )
                }
            }

            // ---------------------------------------------------- notifications & reminders
            item { SectionTitle(stringResource(R.string.preferences_reminder_title)) }
            item {
                SettingsCard {
                    SwitchRow(
                        title = stringResource(R.string.preferences_alerts_title),
                        checked = alerts,
                        onCheckedChange = { alerts = it; repo.setBool(KEY_ALERTS, it) },
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = stringResource(R.string.preferences_alerts_vibrateWhen_title),
                        checked = vibrate,
                        onCheckedChange = { vibrate = it; repo.setBool(KEY_ALERTS_VIBRATE, it) },
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = stringResource(R.string.preferences_alerts_popup_title),
                        checked = popup,
                        onCheckedChange = { popup = it; repo.setBool(KEY_ALERTS_POPUP, it) },
                    )
                    HorizontalDivider()
                    OptionRow(
                        title = stringResource(R.string.preferences_default_snooze_delay_title),
                        value = snooze,
                        options = SNOOZE_VALUES,
                        labelOf = { snoozeLabel(it) },
                        onSelect = { snooze = it; repo.setString(KEY_SNOOZE, it) },
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = stringResource(
                            R.string.preferences_alerts_custom_snooze_delay_title
                        ),
                        checked = customSnooze,
                        onCheckedChange = {
                            customSnooze = it; repo.setBool(KEY_CUSTOM_SNOOZE, it)
                        },
                    )
                    HorizontalDivider()
                    OptionRow(
                        title = stringResource(R.string.preferences_default_reminder_title),
                        value = defaultReminder,
                        options = REMINDER_VALUES,
                        labelOf = { reminderLabel(it) },
                        onSelect = {
                            defaultReminder = it; repo.setString(KEY_DEFAULT_REMINDER, it)
                        },
                    )
                    HorizontalDivider()
                    RingtoneRow(
                        value = ringtone,
                        onPicked = { ringtone = it; repo.setString(KEY_RINGTONE, it) },
                    )
                    HorizontalDivider()
                    OptionRow(
                        title = stringResource(R.string.preferences_reminders_responded_label),
                        value = skipReminders,
                        options = SKIP_REMINDER_VALUES,
                        labelOf = { skipReminderLabel(it) },
                        onSelect = {
                            skipReminders = it; repo.setString(KEY_SKIP_REMINDERS, it)
                        },
                    )
                }
            }

            // ----------------------------------------------------------------- countdown
            item { SectionTitle(stringResource(R.string.m3e_countdown_settings)) }
            item {
                SettingsCard {
                    SwitchRow(
                        title = stringResource(R.string.m3e_countdown_setting_dynamic),
                        summary = stringResource(R.string.m3e_countdown_setting_dynamic_summary),
                        checked = countdownDynamic,
                        onCheckedChange = {
                            countdownDynamic = it
                            repo.setBool(
                                com.android.calendar.countdown.compose.KEY_DYNAMIC, it
                            )
                        },
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = stringResource(R.string.m3e_countdown_setting_count_today),
                        summary = stringResource(R.string.m3e_countdown_setting_count_today_summary),
                        checked = countdownCountToday,
                        onCheckedChange = {
                            countdownCountToday = it
                            repo.setBool(
                                com.android.calendar.countdown.compose.KEY_COUNT_TODAY, it
                            )
                        },
                    )
                    HorizontalDivider()
                    OptionRow(
                        title = stringResource(R.string.m3e_countdown_setting_sort),
                        value = countdownSort,
                        options = com.android.calendar.countdown.CountdownStore.SortOrder.entries
                            .map { it.name },
                        labelOf = { sortLabel(it) },
                        onSelect = {
                            countdownSort = it
                            repo.setString(
                                com.android.calendar.countdown.compose.KEY_SORT, it
                            )
                        },
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = stringResource(R.string.m3e_countdown_setting_decimals),
                        summary = stringResource(R.string.m3e_countdown_setting_decimals_summary),
                        checked = countdownDecimals,
                        onCheckedChange = {
                            countdownDecimals = it
                            repo.setBool(
                                com.android.calendar.countdown.compose.KEY_DECIMALS, it
                            )
                        },
                    )
                }
            }

            // ----------------------------------------------------------------------- other
            item { SectionTitle(stringResource(R.string.m3e_section_other)) }
            item {
                SettingsCard {
                    SwitchRow(
                        title = stringResource(R.string.preferences_use_home_tz_title),
                        checked = homeTz,
                        onCheckedChange = { homeTz = it; repo.setBool(KEY_HOME_TZ, it) },
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = stringResource(R.string.preferences_do_not_check_battery_optimization),
                        summary = stringResource(
                            R.string.preferences_do_not_check_battery_optimization_summary
                        ),
                        checked = battery,
                        onCheckedChange = { battery = it; repo.setBool(KEY_BATTERY, it) },
                    )
                    if (homeTz) {
                        HorizontalDivider()
                        OptionRow(
                            title = stringResource(R.string.preferences_home_tz_title),
                            value = homeTzValue,
                            options = TimeZoneIds,
                            labelOf = { it.ifEmpty { stringResource(R.string.m3e_back) } },
                            onSelect = {
                                homeTzValue = it; repo.setString(KEY_HOME_TZ_VALUE, it)
                            },
                        )
                    }
                    HorizontalDivider()
                    ClickRow(
                        title = stringResource(R.string.preferences_clear_search_history_title),
                        summary = stringResource(R.string.preferences_clear_search_history_summary),
                        onClick = { clearSearchHistory(context) },
                    )
                    HorizontalDivider()
                    // Same flow as the overflow menu entry, plus a "download from a url" option.
                    ClickRow(
                        title = stringResource(R.string.cal_import_menu_title),
                        summary = stringResource(R.string.m3e_import_summary),
                        onClick = { showImportDialog = true },
                    )
                }
            }

            // --------------------------------------------------------------------- more
            item { SectionTitle(stringResource(R.string.m3e_section_more)) }
            item {
                SettingsCard {
                    ClickRow(
                        title = stringResource(R.string.m3e_calendars_title),
                        summary = stringResource(R.string.m3e_calendars_hint),
                        onClick = onOpenCalendars,
                    )
                    HorizontalDivider()
                    ClickRow(
                        title = stringResource(R.string.quick_response_settings),
                        summary = stringResource(R.string.quick_response_settings_summary),
                        onClick = {
                            context.startActivity(
                                Intent(context, ComposeQuickResponsesActivity::class.java)
                            )
                        },
                    )
                }
            }
        }
    }
    }
}

// ------------------------------------------------------------------------------ building blocks

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
    )
}

/** The name shown for a countdown sort order. */
@Composable
private fun sortLabel(key: String): String = stringResource(
    when (key) {
        "ADDED" -> R.string.m3e_countdown_sort_added
        "ADDED_REVERSED" -> R.string.m3e_countdown_sort_added_reversed
        "NAME" -> R.string.m3e_countdown_sort_name
        "TIME_MIXED" -> R.string.m3e_countdown_sort_time_mixed
        "TIME_DESC" -> R.string.m3e_countdown_sort_time_desc
        else -> R.string.m3e_countdown_sort_time_asc
    }
)
/** A Material 3 Expressive surface: extra-large container colour and a large corner radius. */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.padding(12.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ClickRow(title: String, summary: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OptionRow(
    title: String,
    value: String,
    options: List<String>,
    labelOf: @Composable (String) -> String,
    onSelect: (String) -> Unit,
    summary: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (summary != null) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = labelOf(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    onClick = { expanded = false; onSelect(option) },
                )
            }
        }
    }
}

/**
 * Material 3 Expressive [ButtonGroup] used as a segmented control for the theme mode.
 * This is one of the components that only exist in the Expressive component set.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeModeButtonGroup(selected: String, onSelect: (String) -> Unit) {
    val system = stringResource(R.string.m3e_theme_system)
    val light = stringResource(R.string.preferences_light_theme)
    val dark = stringResource(R.string.preferences_dark_theme)

    // Black is not offered.
    //
    // Dark covers what it was for, and three choices fit without the group having to scroll. The
    // theme itself is untouched: Theme.BLACK, its palette, its background style and the drawer entry
    // all still exist, so a device already set to black keeps working and the choice can be offered
    // again by adding one line here.
    val entries = listOf(
        "system" to system,
        "light" to light,
        "dark" to dark,
    )

    // Three labels still do not fit on every phone width in every language, so the group scrolls.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        ButtonGroup(overflowIndicator = { Text("…") }) {
            entries.forEach { (key, label) ->
                toggleableItem(
                    checked = selected == key,
                    label = label,
                    onCheckedChange = { onSelect(key) },
                )
            }
        }
    }
}

/**
 * Makes the monet / dynamic-colour state visible: it renders the live Material 3 colour roles,
 * which are the values re-mapped from the wallpaper on Android 12+.
 */
@Composable
private fun MonetCard(darkTheme: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val roles = listOf(
        scheme.primary,
        scheme.secondary,
        scheme.tertiary,
        scheme.primaryContainer,
        scheme.secondaryContainer,
        scheme.tertiaryContainer,
        scheme.surfaceContainerHighest,
        scheme.error,
    )

    SettingsCard {
        Text(
            text = stringResource(R.string.m3e_monet_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(
                if (isMonetSupported) R.string.m3e_monet_on else R.string.m3e_monet_off
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            roles.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(color = color, shape = CircleShape),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                if (darkTheme) R.string.m3e_mode_dark else R.string.m3e_mode_light
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
    }
}

// ----------------------------------------------------------------------------- option labelling

@Composable
private fun weekStartLabel(value: String): String {
    if (value == "-1") return stringResource(R.string.week_start_locale_default)
    val names = listOf(
        stringResource(R.string.m3e_day_sun),
        stringResource(R.string.m3e_day_mon),
        stringResource(R.string.m3e_day_tue),
        stringResource(R.string.m3e_day_wed),
        stringResource(R.string.m3e_day_thu),
        stringResource(R.string.m3e_day_fri),
        stringResource(R.string.m3e_day_sat),
    )
    val index = value.toIntOrNull()?.minus(1) ?: 0
    return names.getOrElse(index) { value }
}

@Composable
private fun durationLabel(value: String): String = when (value) {
    "0" -> stringResource(R.string.zero_minutes)
    "15" -> stringResource(R.string.fifteen_minutes)
    "30" -> stringResource(R.string.thirty_minutes)
    "60" -> stringResource(R.string.one_hour)
    "90" -> stringResource(R.string.one_and_a_half_hours)
    "120" -> stringResource(R.string.two_hours)
    else -> value
}

@Composable
private fun showTimeLabel(value: String): String = when (value) {
    "0" -> stringResource(R.string.show_time_no)
    "1" -> stringResource(R.string.show_time_start)
    "2" -> stringResource(R.string.show_time_start_end)
    "3" -> stringResource(R.string.m3e_show_time_duration)
    "4" -> stringResource(R.string.show_time_start_end_below)
    else -> value
}

@Composable
private fun snoozeLabel(value: String): String =
    if (value == "-1") {
        stringResource(R.string.m3e_snooze_default)
    } else {
        stringResource(R.string.m3e_minutes, value.toIntOrNull() ?: 0)
    }

// ------------------------------------------------------------------- remaining row renderers

/** All platform time zone ids, used by the "home time zone" picker. */
private val TimeZoneIds: List<String> =
    java.util.TimeZone.getAvailableIDs().sorted()

@Composable
private fun defaultViewLabel(value: String): String = when (value) {
    "-2" -> stringResource(R.string.default_start_last)
    "2" -> stringResource(R.string.day_view)
    "3" -> stringResource(R.string.week_view)
    "4" -> stringResource(R.string.month_view)
    "1" -> stringResource(R.string.agenda_view)
    else -> value
}

@Composable
private fun yearScrollLabel(value: String): String = when (value) {
    YEAR_SCROLL_VERTICAL -> stringResource(R.string.m3e_year_scroll_vertical)
    else -> stringResource(R.string.m3e_year_scroll_horizontal)
}

@Composable
private fun reminderLabel(value: String): String = when (value) {
    NO_REMINDER -> stringResource(R.string.no_reminder_label)
    "0" -> stringResource(R.string.m3e_reminder_at_time)
    else -> stringResource(R.string.m3e_reminder_minutes_before, value.toIntOrNull() ?: 0)
}

@Composable
private fun skipReminderLabel(value: String): String {
    val labels = stringArrayResource(R.array.preferences_skip_reminders_labels)
    val index = SKIP_REMINDER_VALUES.indexOf(value)
    return labels.getOrElse(index) { value }
}

/**
 * Notification sound. It hands off to the platform ringtone picker, exactly like the classic
 * preference does, and stores the picked uri under the same key.
 */
@Composable
private fun RingtoneRow(value: String, onPicked: (String) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val uri: Uri? = data?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    it.getParcelableExtra(
                        RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    it.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as? Uri
                }
            }
            onPicked(uri?.toString().orEmpty())
        }
    }

    val title = remember(value) { ringtoneTitle(context, value) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(
                        RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION
                    )
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                    putExtra(
                        RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    )
                    if (value.isNotEmpty()) {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(value))
                    }
                }
                launcher.launch(intent)
            }
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.preferences_alerts_ringtone_title),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun ringtoneTitle(context: Context, value: String): String =
    if (value.isEmpty()) {
        context.getString(R.string.m3e_ringtone_default)
    } else {
        runCatching {
            RingtoneManager.getRingtone(context, Uri.parse(value))?.getTitle(context)
        }.getOrNull() ?: value
    }

/** Mirrors GeneralPreferences.clearSearchHistory(). */
private fun clearSearchHistory(context: Context) {
    try {
        SearchRecentSuggestions(
            context,
            Utils.getSearchAuthority(context),
            CalendarRecentSuggestionsProvider.MODE,
        ).clearHistory()
        Toast.makeText(context, R.string.search_history_cleared, Toast.LENGTH_SHORT).show()
    } catch (t: Throwable) {
        // Nothing sensible to do; the history simply stays.
    }
}
