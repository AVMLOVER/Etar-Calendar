/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * View-based host for the standalone Material 3 screens.
 *
 * These screens used to draw their own Compose drawer and top bar, which looked and behaved
 * slightly differently from the calendar's. They now sit inside the same chrome the calendar
 * itself uses - res/layout/m3e_screen.xml mirrors all_in_one_material.xml, the drawer is the
 * same @menu/calendar_view driven NavigationView and the bar is the same @menu/all_in_one_title_bar
 * toolbar - with the Compose UI placed in the content slot.
 */
package com.android.calendar.chrome

import androidx.appcompat.widget.SearchView
import android.content.Intent
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.provider.CalendarContract
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.android.calendar.AllInOneActivity
import com.android.calendar.Utils
import com.android.calendar.chrome.compose.M3eMonthPickerDialog
import com.android.calendar.settings.compose.EtarM3ETheme
import com.android.calendar.settings.compose.SettingsRepository
import com.android.calendar.theme.applyTheme
import com.google.android.material.navigation.NavigationView
import ws.xsoh.etar.R

abstract class M3eHostActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    /** Drawer entry that represents this screen, or 0 when it is not a calendar view. */
    protected open val checkedMenuItemId: Int = 0

    /**
     * True for the screens that are destinations in their own right (the year view, the agenda):
     * back leaves the app. False for the ones you dip into and come back from, like the date
     * calculator.
     */
    protected open val exitOnBack: Boolean = true

    /**
     * True when tapping the heading should open the month picker.
     *
     * The heading names a period on the screens that show one, so tapping it to jump somewhere else
     * is the natural gesture. A screen whose heading is just its own name has nothing to open, and
     * the down caret would be a promise the screen does not keep.
     */
    protected open val titleOpensPicker: Boolean = true

    /**
     * True to offer the Today action.
     *
     * Only meaningful where there is a "today" to go back to on a grid. On a tool screen the icon
     * would switch the user away to the month view, which is not what a screen called Today implies.
     */
    /**
     * True for a screen that is arrived at rather than switched to: it shows a back arrow where
     * the drawer button would be, and its drawer is locked shut.
     */
    protected open val navigationIconShowsBack: Boolean = false

    protected open val showTodayAction: Boolean = true

    /**
     * True to offer "go to date" in the overflow menu.
     *
     * It is about moving around the calendar, which the tool screens do not do - the date calculator
     * works on the dates typed into it, and the countdown list is its own thing. Their headings are
     * screen names rather than periods, so there is nothing for it to act on.
     */
    protected open val showGotoAction: Boolean = true

    /**
     * True to offer search in the overflow menu.
     *
     * Separate from [showGotoAction] because the two are wanted in different places: searching events
     * makes sense from the countdown list, while there is nothing to jump to from either tool.
     */
    protected open val showSearchAction: Boolean = true

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    /** Text the toolbar is showing, used to find the title view the ActionBar owns. */
    private var pickerTitle: String = ""

    /** The heading view; created once in [installTitlePicker] and reused for every title change. */
    private var titleText: android.widget.TextView? = null

    /** Open month picker, kept so picking a month can close it. */
    private var pickerPopup: android.app.Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Same theme application the calendar and every other activity uses.
        applyTheme()
        setContentView(R.layout.m3e_screen)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // A screen that is arrived at rather than switched to shows a back arrow in place of the
        // drawer button, and has no drawer to open.
        if (navigationIconShowsBack) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            // Routed through the back dispatcher rather than straight to finish(), so the arrow
            // does exactly what the system back gesture does on this screen.
            toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            toolbar.setNavigationContentDescription(androidx.appcompat.R.string.abc_action_bar_up_description)
            drawerLayout = findViewById(R.id.drawer_layout)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        }

        // Screens replace this the moment they know what period they are showing. It is set here so
        // the control exists even if a screen never calls setPickerTitle.
        installTitlePicker(toolbar)
        setPickerTitle(java.time.LocalDate.now().year.toString())

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view)
        navigationView.setNavigationItemSelectedListener(this)
                // day_menu_item ships checked in the menu XML, so clear the whole view group first: for a
        // calendar view setCheckedItem then re-checks just that one, and for anything else nothing
        // stays selected. Without this the drawer claimed the user was on the day view.
        CALENDAR_VIEW_ITEMS.forEach { id ->
            navigationView.menu.findItem(id)?.isChecked = false
        }
        if (checkedMenuItemId != 0) {
            navigationView.setCheckedItem(checkedMenuItemId)
        } else {
            // Not a calendar view (the date calculator): no view entry should look selected. The
            // menu XML ships with day_menu_item checked, so it has to be cleared explicitly -
            // otherwise the drawer claims the user is on the day view.
            CALENDAR_VIEW_ITEMS.forEach { id ->
                navigationView.menu.findItem(id)?.isChecked = false
            }
        }

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.app_label, R.string.app_label
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Back returns to the month view for the screens that are destinations of their own
        // (the year view and the agenda); the month view's own back then leaves the app. The date
        // calculator keeps the platform's default back, which is what carries the predictive
        // animation, so it goes back to the screen it was opened from.
        val backCallback = object : OnBackPressedCallback(exitOnBack) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawers()
                } else if (exitOnBack) {
                    openCalendarView(Utils.INTENT_VALUE_VIEW_TYPE_MONTH)
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                backCallback.isEnabled = true
            }

            override fun onDrawerClosed(drawerView: View) {
                backCallback.isEnabled = exitOnBack
            }
        })
    }

    /** Installs the Compose UI into the host's content slot. */
    protected fun setM3eContent(content: @Composable () -> Unit) {
        val repo = SettingsRepository(this)
        val themeMode = repo.string(KEY_THEME, "system")
        val dark = when (themeMode) {
            "light" -> false
            "dark", "black" -> true
            else -> (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        

        findViewById<ComposeView>(R.id.m3e_content).setContent {
            EtarM3ETheme(darkTheme = dark) {
                content()
            }
        }
    }

    // ----------------------------------------------------------------------- toolbar title picker

    /**
     * Sets the toolbar heading and makes it the control that opens the month picker.
     *
     * The heading names the period on screen - a year, a range of three days - so tapping it is the
     * natural way to jump somewhere else. Screens call this instead of setting the ActionBar title
     * directly.
     */
    protected fun setPickerTitle(text: String) {
        pickerTitle = text
        // The ActionBar's own title is switched off in installTitlePicker. Passing null here again
        // makes ActionBar collapse the whole heading - which reads as the screen going away - so it
        // is only the custom view's text that is updated.
        titleText?.text = text
    }

    /**
     * Installs the heading as the ActionBar's custom view.
     *
     * The ActionBar owns its own title view and rebuilds it whenever the title changes, so a click
     * attached to it does not survive; the custom view is created once and kept instead. It also
     * carries the down caret that marks the heading as something that opens.
     */
    private fun installTitlePicker(toolbar: Toolbar) {
        val view = layoutInflater.inflate(R.layout.m3e_toolbar_title, toolbar, false)
        titleText = view.findViewById(R.id.m3e_title_text)
        // The caret marks the heading as something that opens; without a picker it is only a heading.
        view.findViewById<android.widget.ImageView>(R.id.m3e_title_caret)?.visibility =
            if (titleOpensPicker) android.view.View.VISIBLE else android.view.View.GONE
        if (titleOpensPicker) {
            view.setOnClickListener { showMonthPicker() }
        } else {
            view.isClickable = false
            view.isFocusable = false
            view.background = null
        }
        titleText?.text = pickerTitle

        supportActionBar?.apply {
            displayOptions = displayOptions or
                androidx.appcompat.app.ActionBar.DISPLAY_SHOW_CUSTOM
            setDisplayShowTitleEnabled(false)
            setCustomView(
                view,
                androidx.appcompat.app.ActionBar.LayoutParams(
                    androidx.appcompat.app.ActionBar.LayoutParams.WRAP_CONTENT,
                    androidx.appcompat.app.ActionBar.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    /**
     * Opens the month picker under the toolbar heading.
     *
     * The picker itself lives in M3eMonthPickerDialog, which is shared with the View-based calendar.
     * Building a ComposeView inside a PopupWindow here instead would crash on attach: a popup is its
     * own window and Compose needs a ViewTreeLifecycleOwner from the view tree it is added to.
     */
    private fun showMonthPicker() {
        pickerPopup?.dismiss()
        pickerPopup = M3eMonthPickerDialog.show(
            this,
            findViewById(R.id.toolbar),
            java.util.function.BiConsumer { year, month -> jumpToMonth(year, month) },
        )
    }

    /** Opens the calendar on the given month. */
    private fun jumpToMonth(year: Int, month: Int) {
        val millis = java.time.LocalDate.of(year, month, 1)
            .atStartOfDay(java.time.ZoneId.systemDefault())
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
    }

    // ------------------------------------------------------------------------------- drawer

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val handled = when (item.itemId) {
            R.id.day_menu_item -> openCalendarView(Utils.INTENT_VALUE_VIEW_TYPE_DAY)
            R.id.threeday_menu_item -> openScreen(ComposeScreen.THREE_DAY)
            R.id.week_menu_item -> openCalendarView(Utils.INTENT_VALUE_VIEW_TYPE_WEEK)
            R.id.month_menu_item -> openCalendarView(Utils.INTENT_VALUE_VIEW_TYPE_MONTH)
            R.id.year_menu_item -> openScreen(ComposeScreen.YEAR)
            R.id.agenda_menu_item -> openScreen(ComposeScreen.AGENDA)
            R.id.action_date_calculator -> openScreen(ComposeScreen.DATE_CALCULATOR)
            R.id.action_countdown -> openScreen(ComposeScreen.COUNTDOWN)
            R.id.action_settings -> openScreen(ComposeScreen.SETTINGS)
            R.id.action_about -> openScreen(ComposeScreen.ABOUT)
            else -> false
        }
        if (!handled) {
            return false
        }
        drawerLayout.closeDrawers()
        return true
    }

    // ------------------------------------------------------------------------------ toolbar

    /**
     * The intent SearchActivity expects.
     *
     * It reads the query out of an ACTION_SEARCH intent, so a plain startActivity with no action and
     * no query extra leaves it with nothing to show - which is what an empty search screen was.
     */
    private fun searchIntent(query: String): Intent {
        val intent = Intent(Intent.ACTION_SEARCH)
        intent.putExtra(android.app.SearchManager.QUERY, query)
        intent.setClass(this, com.android.calendar.search.compose.ComposeSearchActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return intent
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.all_in_one_title_bar, menu)

        // Screens that have no "today" to return to drop the action; the overflow menu stays, which
        // is what the screen is left with.
        if (!showTodayAction) {
            menu.findItem(R.id.action_today)?.isVisible = false
        }

        // Dropped individually: a tool has nowhere to "go to", but searching events from the
        // countdown list is still useful. The rest of the menu stays either way.
        if (!showGotoAction) {
            menu.findItem(R.id.action_goto)?.isVisible = false
        }
        if (!showSearchAction) {
            menu.findItem(R.id.action_search)?.isVisible = false
        } else {
            // The same search the calendar offers: the entry opens a field in the title bar, and what
            // is typed into it is carried to the search screen.
            //
            // Starting SearchActivity directly was wrong - it requires ACTION_SEARCH with the query
            // attached, and without them it shows an empty screen that cannot search.
            menu.findItem(R.id.action_search)?.let { item ->
                // Placed in the bar rather than the overflow, and given its icon here - see the
                // note in AllInOneActivity.
                item.setShowAsAction(
                    MenuItem.SHOW_AS_ACTION_ALWAYS or
                        MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW,
                )
                item.setIcon(R.drawable.ic_menu_search)
            }
            (menu.findItem(R.id.action_search)?.actionView as? SearchView)?.let { view ->
                Utils.setUpSearchView(view, this)
                view.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        menu.findItem(R.id.action_search)?.collapseActionView()
                        if (!query.isNullOrBlank()) {
                            startActivity(searchIntent(query))
                        }
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean = false
                })
            }
        }

        // Same treatment the calendar gives its Today icon: the day of the month is drawn into it.
        menu.findItem(R.id.action_today)?.let { item ->
            (item.icon as? LayerDrawable)?.let { icon ->
                Utils.setTodayIcon(icon, this, Utils.getTimeZone(this, null))
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_today -> {
                onToday()
            }

            // No branch for action_search: the entry is a search field, and its listener carries the
            // query to SearchActivity. Selecting the item only expands that field.

            // The dialog the settings screen offers: a file on the device, or a url to download.
            //
            // This used to open a document picker and never look at the result, so choosing a file
            // did nothing at all. It now goes through the same activity the settings entry uses.
            R.id.action_import -> {
                startActivity(
                    Intent(
                        this,
                        com.android.calendar.chrome.compose.ComposeImportActivity::class.java,
                    )
                )
            }

            R.id.action_m3e_calendars -> openScreen(ComposeScreen.CALENDARS)
            R.id.action_m3e_agenda -> openScreen(ComposeScreen.AGENDA)
            R.id.action_view_settings -> openScreen(ComposeScreen.SETTINGS)

            else -> return false
        }
        return true
    }

    // --------------------------------------------------------------------------- navigation

    private enum class ComposeScreen {
        YEAR, AGENDA, DATE_CALCULATOR, COUNTDOWN, CALENDARS, SETTINGS, ABOUT, THREE_DAY
    }

    private fun openScreen(screen: ComposeScreen): Boolean {
        // Selecting the screen we are already on just closes the drawer.
        if (screen.name == currentScreenName) {
            return true
        }
        val target = when (screen) {
            ComposeScreen.YEAR -> com.android.calendar.year.compose.ComposeYearActivity::class.java
            ComposeScreen.AGENDA ->
                com.android.calendar.agenda.compose.ComposeAgendaActivity::class.java
            ComposeScreen.THREE_DAY ->
                com.android.calendar.threeday.compose.ComposeThreeDayActivity::class.java
            ComposeScreen.DATE_CALCULATOR ->
                com.android.calendar.datecalc.compose.ComposeDateCalculatorActivity::class.java
            ComposeScreen.COUNTDOWN ->
                com.android.calendar.countdown.compose.ComposeCountdownActivity::class.java
            ComposeScreen.CALENDARS ->
                com.android.calendar.selectcalendars.compose.ComposeCalendarsActivity::class.java
            ComposeScreen.SETTINGS ->
                com.android.calendar.settings.compose.ComposeSettingsActivity::class.java
            ComposeScreen.ABOUT ->
                com.android.calendar.about.compose.ComposeAboutActivity::class.java
        }
        startActivity(Intent(this, target).apply {
            // A destination in its own right becomes its own task root, so the platform's default
            // back (which is what carries the predictive animation) lands on the launcher. The
            // screens you come back from are left in the current task.
            if (screen == ComposeScreen.YEAR || screen == ComposeScreen.AGENDA) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        })
        if (screen == ComposeScreen.YEAR || screen == ComposeScreen.AGENDA ||
            screen == ComposeScreen.DATE_CALCULATOR || screen == ComposeScreen.COUNTDOWN
        ) {
            finish()
        }
        return true
    }

    /** Name of the ComposeScreen this host represents, used to avoid re-opening itself. */
    protected open val currentScreenName: String = ""

    /**
     * Opens the calendar on the given view, reusing the running calendar activity instead of
     * stacking another copy of it.
     */
    private fun openCalendarView(viewTypeValue: String): Boolean {
        startActivity(
            Intent(this, AllInOneActivity::class.java).apply {
                // AllInOneActivity.onNewIntent only honours the date for ACTION_VIEW intents (or
                // for an explicit view request), so the action has to be set.
                action = Intent.ACTION_VIEW
                putExtra(Utils.INTENT_KEY_VIEW_TYPE, viewTypeValue)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis())
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
                finish()
        return true
    }

    /**
     * What the title bar's today button does.
     *
     * By default it returns to the month view, which is right for a screen that has no notion of
     * "today on this screen". The three day view shows dates, so it overrides this and moves
     * itself to today rather than leaving the screen.
     */
    protected open fun onToday() {
        openCalendarView(Utils.INTENT_VALUE_VIEW_TYPE_MONTH)
    }

    /**
     * The screens that are activities of their own cross-fade when they close, so going back to the
     * calendar looks the same as switching to it from the drawer.
     */
    override fun finish() {
        super.finish()
            }

    companion object {
        private const val KEY_THEME = "pref_theme"

        /** The drawer entries that represent an actual calendar view. */
        private val CALENDAR_VIEW_ITEMS = intArrayOf(
            R.id.day_menu_item,
            R.id.threeday_menu_item,
            R.id.week_menu_item,
            R.id.month_menu_item,
            R.id.year_menu_item,
            R.id.agenda_menu_item,
        )

        private fun isCalendarView(itemId: Int): Boolean =
            CALENDAR_VIEW_ITEMS.any { it == itemId }

        /**
         * Launches one of the standalone screens from somewhere else (the calendar's drawer, for
         * instance) with the same task flags [openScreen] uses.
         */
        @JvmStatic
        fun launchScreen(from: android.content.Context, screen: String) {
            val target = when (screen) {
                "YEAR" -> com.android.calendar.year.compose.ComposeYearActivity::class.java
                "AGENDA" -> com.android.calendar.agenda.compose.ComposeAgendaActivity::class.java
                "COUNTDOWN" ->
                    com.android.calendar.countdown.compose.ComposeCountdownActivity::class.java
                else ->
                    com.android.calendar.datecalc.compose.ComposeDateCalculatorActivity::class.java
            }
            from.startActivity(Intent(from, target).apply {
                // Destinations of their own become a task root, so the platform's back - the one
                // that carries the predictive animation - leaves the app.
                if (screen == "YEAR" || screen == "AGENDA") {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            })
        }
    }
}
