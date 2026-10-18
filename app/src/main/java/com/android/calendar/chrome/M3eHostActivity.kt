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
import com.android.calendar.composeui.ComposeCalendarViewActivity
import com.android.calendar.settings.compose.EtarM3ETheme
import com.android.calendar.settings.compose.SettingsRepository
import com.android.calendar.theme.applyTheme
import com.google.android.material.navigation.NavigationView
import ws.xsoh.etar.R

/**
 * Set when the calendar is being entered from the day / three day / week sequence.
 *
 * Those three are read as one after another, so a step between them slides the same way every time;
 * only leaving the sequence reverses. The calendar is told which of the two this is, because it
 * cannot work it out from the view being asked for - the three day view's step to the week view and
 * its step to the day view look identical from there.
 */
const val EXTRA_FORWARD_FROM_SEQUENCE = "m3eForwardFromSequence"

abstract class M3eHostActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    /** Drawer entry that represents this screen, or 0 when it is not a calendar view. */
    protected open val checkedMenuItemId: Int = 0

    /**
     * Whether back steps on to the month view, or hands the screen back to the one underneath.
     *
     * Every screen the drawer opens is opened *over* the calendar, which stays underneath - and the
     * intent says so with [ComposeCalendarViewActivity.EXTRA_RETURN_TO_OPENER]. Back on such a screen
     * is a plain finish, so it plays the reverse of the way in: the screen leaves to the right and the
     * one it covered comes back from the left. Stepping to the month view instead *starts* a screen,
     * and a start is an open transition, so the return slid left a second time.
     *
     * A screen with nothing underneath keeps the calendar's own back, which goes on to the month view.
     */
    protected open val exitOnBack: Boolean
        get() = !intent.getBooleanExtra(
            ComposeCalendarViewActivity.EXTRA_RETURN_TO_OPENER,
            false,
        )

    /** The back callback installed in onCreate; kept so a new intent can update whether it is on. */
    private var backCallback: OnBackPressedCallback? = null


    /**
     * True when tapping the heading should open the month picker.
     *
     * The heading names a period on the screens that show one, so tapping it to jump somewhere else
     * is the natural gesture. A screen whose heading is just its own name has nothing to open, and
     * the down caret would be a promise the screen does not keep.
     */
    protected open val titleOpensPicker: Boolean = true

    /**
     * True when that picker should choose a date rather than a month.
     *
     * The month picker suits a screen whose heading is a month. The three day view's heading is a
     * range of days, so a month is the wrong thing to be asked for: choosing one left the screen - it
     * went to the month view - and choosing a day is what the heading is actually showing. The screen
     * that wants a date overrides [onDatePicked] and opens its own picker.
     */
    protected open val pickerChoosesDate: Boolean = false

    /** Called with the day chosen by a date picker. Only reached when [pickerChoosesDate] is true. */
    protected open fun onDatePicked(date: java.time.LocalDate) {
    }

    /**
     * True when the picker behind the heading should choose a whole year rather than a month.
     *
     * The year view's heading is a year, so a row of that year's twelve months is not what tapping
     * it is asking to do, and a month is not a destination the year view has. Screens that want a
     * year override [pickerYear] and [onYearPicked] instead.
     */
    protected open val pickerChoosesYear: Boolean = false

    /** The year the picker should be centred on. Only reached when [pickerChoosesYear] is true. */
    protected open fun pickerYear(): Int = java.time.LocalDate.now().year

    /** Called with the year chosen by the picker. Only reached when [pickerChoosesYear] is true. */
    protected open fun onYearPicked(year: Int) {
    }

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

    /** The heading's second line, when a screen gives it one. */
    private var subtitleText: android.widget.TextView? = null

    /** Open month picker, kept so picking a month can close it. */
    private var pickerPopup: android.app.Dialog? = null

    override fun onResume() {
        super.onResume()
        // The slide this screen plays when it is covered, and the reverse when it is revealed.
        // Declared here rather than in onCreate because overrideActivityTransition is only accepted
        // while the activity is resumed; from onCreate it is discarded and the platform default -
        // the next screen simply laid over this one - plays instead.
        declareForwardSlide()
        declareReturnSlide()
        // The drawer's current-screen mark, put back on this screen every time it comes forward.
        //
        // It used to be set once, in onCreate, and nothing touched it again. A screen that was
        // revealed rather than created - the week view uncovered when the three day view opened over
        // it finished, for instance - came back with its own drawer unchanged, which is the right
        // item for it; but a screen whose drawer had been opened and had its item moved while it was
        // covered came back naming the other screen. Re-applying it on every resume makes the
        // drawer always describe the screen that is actually on top, whichever way it arrived.
        applyDrawerCheckedItem()
    }

    /**
     * Marks [checkedMenuItemId] as the current screen in the drawer, and clears every other view
     * entry.
     *
     * Set on the menu items themselves rather than through NavigationView.setCheckedItem. The
     * checked-item call keeps its own idea of which item is current, and after the clear below it
     * decided there was nothing to do: every calendar screen came back with no entry marked at all,
     * which is worse than the stale mark it was meant to fix. Setting the item is also what the
     * classic calendar's own drawer handler does, for the same reason.
     *
     * The menu XML ships day_menu_item checked, so the whole group is cleared first. A screen that is
     * not a calendar view passes 0 and is left with nothing selected.
     */
    private fun applyDrawerCheckedItem() {
        if (!this::navigationView.isInitialized) {
            return
        }
        CALENDAR_VIEW_ITEMS.forEach { id ->
            navigationView.menu.findItem(id)?.isChecked = false
        }
        if (checkedMenuItemId != 0) {
            navigationView.menu.findItem(checkedMenuItemId)?.isChecked = true
        }
    }

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
        // the control exists even if a screen never calls setPickerTitle. A year is what the screens
        // that do not set it are showing, and it carries its unit like the year view's own heading.
        installTitlePicker(toolbar)
        setPickerTitle(
            getString(R.string.m3e_year_title, java.time.LocalDate.now().year.toString())
        )

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view)
        navigationView.setNavigationItemSelectedListener(this)
        applyDrawerCheckedItem()

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.app_label, R.string.app_label
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // The hamburger, the three dots and the menu they open, in the theme's own colour.
        //
        // The toggle's arrow paints itself and ignores a tint, the overflow dots are the platform's
        // own fixed-colour drawable, and the popup's entries took their colour from an overlay that
        // left them white on a light popup. Each is set here so the light appearance reads properly.
        val barIconColor = resolveBarIconColor()
        toggle.drawerArrowDrawable?.setColor(barIconColor)
        toolbar.setOverflowIcon(
            androidx.appcompat.content.res.AppCompatResources
                .getDrawable(this, R.drawable.ic_overflow_menu)
        )
        toolbar.popupTheme = R.style.ThemeOverlay_Etar_PopupMenu

        // The to-do entry's icon is the title bar's today icon, so today's day number is drawn into
        // it - the same treatment the toolbar's own today button gets.
        navigationView.menu.findItem(R.id.action_todo)?.let { item ->
            (item.icon as? LayerDrawable)?.let { icon ->
                Utils.setTodayIcon(icon, this, Utils.getTimeZone(this, null))
            }
        }

        // Back is the platform's own for a screen that was opened over another one: it finishes, and
        // a finish plays the reverse of the way in. A screen with nothing underneath keeps the
        // calendar's back, which steps on to the month view. The one exception in the callback below
        // is the drawer: while it is open, back closes it.
        val callback = object : OnBackPressedCallback(exitOnBack || drawerLayout.isDrawerOpen(GravityCompat.START)) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawers()
                } else if (exitOnBack) {
                    openCalendarView(Utils.INTENT_VALUE_VIEW_TYPE_MONTH)
                }
            }
        }
        backCallback = callback
        onBackPressedDispatcher.addCallback(this, callback)
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                callback.isEnabled = true
            }

            override fun onDrawerClosed(drawerView: View) {
                callback.isEnabled = exitOnBack
            }
        })
    }

    /**
     * Keeps the intent current when a running instance is reused.
     *
     * The drawer brings a screen forward with REORDER_TO_FRONT instead of starting a second copy, so
     * the intent that arrives is the one that says what was asked for - including whether back should
     * reveal the screen underneath. Without this the first intent would go on being reported, and a
     * screen first opened without that would keep stepping to the month view when it later had a
     * screen to return to.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The intent decides what back does, so the callback follows it.
        backCallback?.isEnabled = exitOnBack || drawerLayout.isDrawerOpen(GravityCompat.START)
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
    /**
     * Sets the heading's second line, or clears it when [text] is empty.
     *
     * This is the ActionBar subtitle's job on the screens that use one; it lives in the custom
     * view here so that it does not steal width from the heading beside it.
     */
    protected fun setPickerSubtitle(text: String) {
        // Kept as the heading's own second line, under the first.
        //
        // Joining it to the heading on one line was tried and put back: the heading sits in a weighted
        // row with the caret after it, so a longer line pushes the caret out to the edge of the bar
        // instead of leaving it against the text. Two lines, as the layout was built for.
        subtitleText?.apply {
            this.text = text
            visibility = if (text.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        }
    }

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
        subtitleText = view.findViewById(R.id.m3e_title_subtitle)
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
        // Auto-sizing set in code rather than trusted to the style.
        //
        // The theme's TextAppearance carries the same settings, but whether AppCompat applies them
        // to a view given this way is not something worth relying on blind - and a heading that is
        // clipped mid-word, which is what a long German date did, is a defect the user sees on
        // every screen. Called directly, it is certain: the text shrinks between these sizes
        // instead of being cut off.
        titleText?.let { text: android.widget.TextView ->
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                text, 9, 18, 1, android.util.TypedValue.COMPLEX_UNIT_SP,
            )
        }
        subtitleText?.let { text: android.widget.TextView ->
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                text, 8, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP,
            )
        }
        titleText?.text = pickerTitle

        supportActionBar?.apply {
            displayOptions = displayOptions or
                androidx.appcompat.app.ActionBar.DISPLAY_SHOW_CUSTOM
            setDisplayShowTitleEnabled(false)
            setCustomView(
                view,
                // MATCH_PARENT, not WRAP_CONTENT.
                //
                // The toolbar measures a custom view before it lays the action icons out, so a
                // wrapping one is measured against the whole bar and is then cut off by the icons
                // drawn over that space - which is how a German date came out as "9. Oktober 2...".
                // Filling the bar makes the heading measure against the width that is really left,
                // and its own auto-sizing then shrinks the text to fit instead of clipping it.
                androidx.appcompat.app.ActionBar.LayoutParams(
                    androidx.appcompat.app.ActionBar.LayoutParams.MATCH_PARENT,
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
        if (pickerChoosesDate) {
            showDatePicker()
            return
        }
        if (pickerChoosesYear) {
            showYearPicker()
            return
        }
        pickerPopup = M3eMonthPickerDialog.show(
            this,
            findViewById(R.id.toolbar),
            null,
            null,
            java.util.function.BiConsumer { year, month -> jumpToMonth(year, month) },
        )
    }

    /**
     * A picker for the screens whose heading is a whole year.
     *
     * Same dialog and placement as the month picker; only its contents differ. Choosing a year is
     * handed to the screen through onYearPicked rather than turned into an intent: the year view is
     * already the screen on top, and the pick has to move its own list rather than leave it.
     */
    private fun showYearPicker() {
        pickerPopup = M3eMonthPickerDialog.showYear(
            this,
            findViewById(R.id.toolbar),
            pickerYear(),
        ) { year -> onYearPicked(year) }
    }

    /**
     * A day picker for the screens whose heading is a range of days.
     *
     * Material's own picker rather than the app's month chips: the chips choose a month, which is the
     * thing that has to be avoided here, and a picker that hands back a day is what the screen needs.
     * The day is handed to the screen through onDatePicked rather than turned into an intent - the
     * three day view is already the screen on top, and starting the calendar underneath it to come
     * back would put the month view on screen instead of moving the range.
     */
    private fun showDatePicker() {
        val zone = java.time.ZoneId.systemDefault()
        // Opens on the day the view is showing - the first of the three days for the three day
        // view - rather than always on today: a picker that opens somewhere else than the heading
        // it was tapped from reads as though it forgot what the view was on. The picker reads its
        // selection as a UTC day, so the local day is handed over as UTC midnight; anything else
        // lands one day off for a zone ahead of UTC.
        val initial = calendarViewDateMillis()
            ?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            ?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
            ?: java.time.LocalDate.now(zone)
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
            .setSelection(initial)
            .setTitleText(R.string.goto_date)
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            if (selection != null) {
                onDatePicked(
                    java.time.Instant.ofEpochMilli(selection).atZone(zone).toLocalDate()
                )
            }
        }
        picker.show(supportFragmentManager, "M3eDatePicker")
    }

    /** Opens the calendar on the given month. */
    private fun jumpToMonth(year: Int, month: Int) {
        val millis = java.time.LocalDate.of(year, month, 1)
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // With the experimental UI on, "the month view" is the all-Compose screen. Sending the picker
        // result to the classic activity put the old View-based grid back on screen from every heading
        // in the app, which is not what the switch promises.
        if (com.android.calendar.composeui.ExperimentalCalendarUi.enabled(this)) {
            startActivity(
                Intent(this, com.android.calendar.composeui.ComposeMonthActivity::class.java)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, millis)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
            )
            return
        }

        startActivity(
            Intent(this, AllInOneActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(Utils.INTENT_KEY_VIEW_TYPE, Utils.INTENT_VALUE_VIEW_TYPE_MONTH)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, millis)
                // Marks this as a destination the user named, like the year view's month choice:
                // without it the month view treats the request as a tapped grid tail and stays put.
                putExtra(AllInOneActivity.EXTRA_GOTO_EXPLICIT, true)
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
            R.id.action_todo -> openScreen(ComposeScreen.TODO)
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

        // The to-do screen's edit action: there is one only while the day on screen has a to-do.
        menu.findItem(R.id.action_todo_edit)?.isVisible = showTodoEditAction

        // Every action icon in the theme's own colour, so a drawable built white cannot show white
        // on a light bar. The framework does not tint action icons itself.
        val iconColor = resolveBarIconColor()
        for (i in 0 until menu.size()) {
            menu.getItem(i).icon?.let { drawable ->
                drawable.mutate()
                drawable.setTintList(android.content.res.ColorStateList.valueOf(iconColor))
            }
        }
        return true
    }

    /**
     * True to offer "edit this to-do" in the title bar.
     *
     * The to-do screen is the only screen that has one, and only while the day it is showing has a
     * to-do; it calls [refreshTitleBarMenu] when that changes as the user pages between days.
     */
    protected open val showTodoEditAction: Boolean = false

    /** What the title bar's edit-the-to-do action does. */
    protected open fun onTodoEdit() {}

    /** Re-reads the title bar actions; see [showTodoEditAction]. */
    protected fun refreshTitleBarMenu() {
        invalidateOptionsMenu()
    }

    /**
     * The colour a title bar icon is drawn in: black on a light bar, white on a dark one.
     *
     * The theme's control colour, which is exactly that pair, read here because the framework
     * applies it to none of the icons that need it.
     */
    private fun resolveBarIconColor(): Int {
        val typedArray = obtainStyledAttributes(intArrayOf(android.R.attr.colorControlNormal))
        val color = typedArray.getColor(0, android.graphics.Color.BLACK)
        typedArray.recycle()
        return color
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
            R.id.action_todo -> openScreen(ComposeScreen.TODO)
            R.id.action_todo_edit -> onTodoEdit()

            else -> return false
        }
        return true
    }

    // --------------------------------------------------------------------------- navigation

    private enum class ComposeScreen {
        YEAR, AGENDA, DATE_CALCULATOR, COUNTDOWN, CALENDARS, SETTINGS, ABOUT, THREE_DAY, TODO
    }

    private fun openScreen(screen: ComposeScreen): Boolean {
        // Selecting the screen we are already on just closes the drawer.
        //
        // A host in the middle of the sequence - the three day view, in practice - is covered by the
        // calendar underneath it, so this early return never fires there and the launch below is what
        // has to keep the copy count at one. See the flags on the intent.
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
            ComposeScreen.TODO ->
                com.android.calendar.todo.compose.ComposeTodoActivity::class.java
            ComposeScreen.CALENDARS ->
                com.android.calendar.selectcalendars.compose.ComposeCalendarsActivity::class.java
            ComposeScreen.SETTINGS ->
                com.android.calendar.settings.compose.ComposeSettingsActivity::class.java
            ComposeScreen.ABOUT ->
                com.android.calendar.about.compose.ComposeAboutActivity::class.java
        }
        val agendaDate = if (screen == ComposeScreen.AGENDA) agendaDateMillis() else null
        val launch = when (screen) {
            // The to-do screen opens on the day this screen is showing rather than on today, so a
            // week or three day view paged to another part of the calendar hands its day over -
            // and today when today is part of what is on screen.
            ComposeScreen.TODO ->
                com.android.calendar.todo.compose.ComposeTodoActivity.intent(this, anchorDayOnScreen())
            // The three day view is one of the calendar views, so it opens where this screen is
            // looking rather than on today. The day handed over is the same one the calendar's own
            // views pass on: today while today is part of what is on screen, and the first day shown
            // otherwise - so a week containing today opens the three day block holding today, and any
            // other week opens the block that starts on the week's first day. Screens with no day of
            // their own fall back to today inside the screen itself.
            ComposeScreen.THREE_DAY ->
                Intent(this, target)
                    .putExtra(
                        CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                        anchorDayOnScreenMillis(),
                    )
                    // This screen stays underneath rather than being finished, so back from the
                    // three day view must reveal it instead of stepping on to the month view.
                    .putExtra(
                        com.android.calendar.composeui.ComposeCalendarViewActivity
                            .EXTRA_RETURN_TO_OPENER,
                        true,
                    )
            else -> Intent(this, target)
        }
        startActivity(launch.apply {
            // The agenda opens around the day this screen is on, when it has one, so coming from
            // the three day view lands on the days the user was looking at instead of on today.
            if (agendaDate != null) {
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, agendaDate)
            }
            // This screen stays underneath rather than being finished, so back from the screen being
            // opened reveals it instead of stepping on to the month view. A step to the month starts
            // a screen, and a start plays the forward slide - which is why the return came out as a
            // second slide to the left instead of the reverse of the way in.
            putExtra(ComposeCalendarViewActivity.EXTRA_RETURN_TO_OPENER, true)
            // Every screen switch stays in the task this one is in, including the year view and
            // the agenda.
            //
            // Those two used to be launched into a task of their own (NEW_TASK|CLEAR_TASK) so that
            // back from them landed on the launcher. A screen started into a new task is animated by
            // the platform's task transition - a cross-fade, which reads as the old screen being
            // covered by the new one - and per-activity transitions are ignored for it. That is why
            // going between the year view and the agenda looked like an overlap while every other
            // switch slid sideways. Back is handled by this screen's own back callback rather than
            // by the task root, so nothing is lost by keeping them here; SINGLE_TOP and
            // REORDER_TO_FRONT bring an instance that is already running forward instead of
            // starting a second copy of it.
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        })
        return true
    }

    /** Name of the ComposeScreen this host represents, used to avoid re-opening itself. */
    protected open val currentScreenName: String = ""

    /**
     * How many days the screen on top shows, starting at [calendarViewDateMillis].
     *
     * One by default. The three day view is three, which is what lets a screen opened from it land on
     * today whenever today is one of the days on screen. See [anchorDayOnScreen].
     */
    protected open val visibleDaySpan: Int = 1

    /**
     * The day another screen should open on when it is started from this one.
     *
     * Today while today is part of what is on screen, and the first day shown otherwise - the rule
     * the calendar's own views use when they hand a day over. With a single day on screen the two
     * answers are the same, so the span is only consulted for the screens that show more.
     */
    private fun anchorDayOnScreen(): java.time.LocalDate {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val millis = calendarViewDateMillis() ?: return today
        val first = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val last = first.plusDays((visibleDaySpan - 1).coerceAtLeast(0).toLong())
        return if (!today.isBefore(first) && !today.isAfter(last)) today else first
    }

    /** [anchorDayOnScreen] as epoch millis, which is how a day travels in an intent. */
    private fun anchorDayOnScreenMillis(): Long = anchorDayOnScreen()
        .atStartOfDay(java.time.ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    /**
     * The date the calendar views should open on when this screen sends the user to one of them,
     * or null to leave the calendar on its own date.
     *
     * The three day view overrides this with the first day of the block on screen, so opening the
     * day view from there lands on the days the user was looking at instead of on today. Screens
     * that have no day of their own leave it at null. See openCalendarView().
     */
    protected open fun calendarViewDateMillis(): Long? = null

    /**
     * The date the agenda should open around when this screen sends the user to it, or null to
     * leave it centred on today.
     *
     * Defaults to the date calendarViewDateMillis() reports: the screens that know which day they
     * are on are also the ones whose agenda entry should open around that day.
     */
    protected open fun agendaDateMillis(): Long? = calendarViewDateMillis()

    /**
     * Opens the calendar on the given view, reusing the running calendar activity instead of
     * stacking another copy of it.
     *
     * The return slide is chosen by where this screen sits in the order of views rather than by the
     * view being asked for. Day, three day and week are read as one after another, so travelling
     * between them always moves the same way - leftwards - and only coming back out to the month
     * reverses. Asking for the month was the wrong question: it sent the three day view's step to
     * the week view the opposite way from its step to the day view.
     */
    private fun openCalendarView(viewTypeValue: String): Boolean {
        // With the experimental UI switched on, the day, week and month views are the all-Compose
        // screens of their own. The drawer, the back gesture and the default today action all come
        // through here, so routing the request once covers all three.
        if (com.android.calendar.composeui.ExperimentalCalendarUi.enabled(this)) {
            val experimentalTarget = when (viewTypeValue) {
                Utils.INTENT_VALUE_VIEW_TYPE_DAY ->
                    com.android.calendar.composeui.ComposeDayActivity::class.java
                Utils.INTENT_VALUE_VIEW_TYPE_WEEK ->
                    com.android.calendar.composeui.ComposeWeekActivity::class.java
                Utils.INTENT_VALUE_VIEW_TYPE_MONTH ->
                    com.android.calendar.composeui.ComposeMonthActivity::class.java
                else -> null
            }
            if (experimentalTarget != null) {
                return openComposeCalendarView(experimentalTarget, viewTypeValue)
            }
        }
        val leavesTheSequence = isFirstOfSequence(viewTypeValue)
        val dateMillis = calendarViewDateMillis()
        startActivity(
            Intent(this, AllInOneActivity::class.java).apply {
                // AllInOneActivity.onNewIntent only honours the date for ACTION_VIEW intents (or
                // for an explicit view request), so the action has to be set.
                action = Intent.ACTION_VIEW
                putExtra(Utils.INTENT_KEY_VIEW_TYPE, viewTypeValue)
                // A screen that knows which day it is on - the three day view - hands that day
                // over, so the calendar opens on it rather than on today. The fallback keeps the
                // old behaviour on the screens that have no date of their own.
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                    dateMillis ?: System.currentTimeMillis())
                if (dateMillis != null) {
                    // A day handed over by a screen is a destination the user chose, the same as a
                    // month named in the year view. The month view otherwise holds still for a date
                    // that lands in another month - the rule that stops a tap on a grid tail from
                    // moving the page - so without this a hop from three day to month could be
                    // refused when the two were showing different months.
                    putExtra(AllInOneActivity.EXTRA_GOTO_EXPLICIT, true)
                }
                // Tells the calendar to come in from the right, as a step along the sequence,
                // rather than from the left as a return to it.
                putExtra(EXTRA_FORWARD_FROM_SEQUENCE, !leavesTheSequence)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        if (leavesTheSequence) {
            // Back out to the month, from somewhere inside the sequence: the reverse of the way in.
            // Declared here because this screen is the one finishing, and a finishing screen that
            // declares nothing gets the reverse of the animation it was entered with.
            declareReturnSlide()
        } else {
            // A step along the sequence. The calendar reads the extra above and comes in from the
            // right, and this screen leaves to the left, so the movement keeps going the same way
            // instead of reversing halfway. leaveForward, not declareForwardSlide: this activity
            // is finishing, so it is its close transition that decides the pair - declaring only
            // the open one left the platform to reverse the way in, which sent every step out of
            // this screen back to the right.
            leaveForward()
        }
        finish()
        return true
    }

    /**
     * Opens one of the all-Compose calendar views over this screen.
     *
     * The movement follows the direction of travel: a step to the day, three day or week view moves
     * leftwards, and the way back moves rightwards - out of the screen that is closing, back onto the
     * one that opened it.
     *
     * What makes the return go the right way is that this screen is *not* finished. It stays
     * underneath, and the screen opened over it hands back with [EXTRA_RETURN_TO_OPENER] so that its
     * back is a plain finish. A finish is a close transition, and the platform plays the reverse of
     * the way in - the closing screen leaves to the right and this one returns from the left.
     *
     * It used to finish this screen and let the screen above step back to a freshly started month
     * view. A start is an *open* transition, so both halves of the return moved left: the day or week
     * view left to the left, and the new month arrived from the right. That is the second leftward
     * slide the animation showed.
     *
     * SINGLE_TOP and REORDER_TO_FRONT bring an instance that is already running forward instead of
     * stacking a second copy of it; the day is passed on so that instance can move to it rather than
     * staying on whatever it was showing.
     */
    private fun openComposeCalendarView(
        target: Class<out android.app.Activity>,
        viewTypeValue: String,
    ): Boolean {
        // Selecting the view that is already on screen just closes the drawer.
        if (target.isInstance(this)) {
            return true
        }

        // Back out to the month view from a screen that was opened over one: the month is still
        // underneath, so this is a finish, not a step. Starting another month here would stack a
        // second one and play an open transition, which is the movement that went the wrong way.
        if (viewTypeValue == Utils.INTENT_VALUE_VIEW_TYPE_MONTH) {
            if (hasOpener()) {
                declareReturnSlide()
                finish()
                return true
            }
            // Nothing underneath, so this screen is replaced by the month instead of being revealed
            // by it - the way in again, declared on the screen that is finishing.
            startActivity(
                Intent(this, target).apply {
                    putExtra(
                        CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                        calendarViewDateMillis() ?: System.currentTimeMillis(),
                    )
                    addFlags(
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
                }
            )
            leaveForward()
            finish()
            return true
        }

        startActivity(
            Intent(this, target).apply {
                putExtra(
                    CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                    calendarViewDateMillis() ?: System.currentTimeMillis(),
                )
                // The screen underneath stays, so the one being opened returns to it by finishing,
                // which the platform plays as the reverse of this entry.
                putExtra(ComposeCalendarViewActivity.EXTRA_RETURN_TO_OPENER, true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
        return true
    }

    /** True when this screen was opened over another one, which back should reveal. */
    private fun hasOpener(): Boolean =
        intent?.getBooleanExtra(ComposeCalendarViewActivity.EXTRA_RETURN_TO_OPENER, false) == true

    /** True for a view that is not part of the day / three day / week sequence. */
    private fun isFirstOfSequence(viewTypeValue: String): Boolean =
        viewTypeValue == Utils.INTENT_VALUE_VIEW_TYPE_MONTH ||
            viewTypeValue == Utils.INTENT_VALUE_VIEW_TYPE_AGENDA

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
                "TODO" -> com.android.calendar.todo.compose.ComposeTodoActivity::class.java
                else ->
                    com.android.calendar.datecalc.compose.ComposeDateCalculatorActivity::class.java
            }
            from.startActivity(Intent(from, target).apply {
                // The screen being opened returns to whatever launched it by finishing, so the slide
                // back is the reverse of the way in rather than a second slide to the left.
                putExtra(ComposeCalendarViewActivity.EXTRA_RETURN_TO_OPENER, true)
                // Destinations of their own become a task root, so the platform's back - the one
                // that carries the predictive animation - leaves the app.
                if (screen == "YEAR" || screen == "AGENDA") {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            })
        }
    }
}
