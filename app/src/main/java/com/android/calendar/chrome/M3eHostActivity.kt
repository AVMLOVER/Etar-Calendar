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
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.android.calendar.CalendarController
import com.android.calendar.Utils
import com.android.calendar.chrome.compose.M3eMonthPickerDialog
import com.android.calendar.composeui.CalendarViewStep
import com.android.calendar.composeui.ComposeCalendarViewActivity
import com.android.calendar.composeui.ExperimentalCalendarUi
import com.android.calendar.composeui.PendingNavigation
import com.android.calendar.composeui.SlideOperation
import com.android.calendar.composeui.animationPairFor
import com.android.calendar.composeui.calendarViewStep
import com.android.calendar.composeui.finishOperation
import com.android.calendar.composeui.isEffectivelyLanding
import com.android.calendar.composeui.isReadyToNavigate
import com.android.calendar.composeui.landingViewToShow
import com.android.calendar.settings.compose.EtarM3ETheme
import com.android.calendar.theme.applyTheme
import com.android.calendar.theme.isDarkAppearance
import com.android.calendar.theme.tonalSurfaceArgb
import com.google.android.material.navigation.NavigationView
import ws.xsoh.etar.R

abstract class M3eHostActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    /** Drawer entry that represents this screen, or 0 when it is not a calendar view. */
    protected open val checkedMenuItemId: Int = 0

    /**
     * Whether back leaves the app, or hands the screen back to the one underneath.
     *
     * A screen the drawer opens is opened *over* the calendar, which stays underneath - and the
     * intent says so with [ComposeCalendarViewActivity.EXTRA_RETURN_TO_OPENER]. Back on such a screen
     * is a plain finish, so it plays the reverse of the way in: the screen leaves to the right and the
     * one it covered comes back from the left.
     *
     * A screen with nothing underneath is where the user arrived, so its back leaves the app. So does
     * a screen showing the view the settings name as the default start view, wherever it was opened
     * from: the month a year view opens is the month the app begins on, so back from it leaves the
     * app instead of walking back into the year view. See [isEffectivelyLanding].
     */
    protected open val exitOnBack: Boolean
        get() = isEffectivelyLanding(
            hasOpener = hasOpener(),
            isDefaultView = isConfiguredDefaultViewScreen(),
        )

    /** True when this screen shows the view the "Default view" setting names. */
    private fun isConfiguredDefaultViewScreen(): Boolean {
        val mine = landingViewType ?: return false
        return ExperimentalCalendarUi.isConfiguredDefault(this, mine)
    }

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

    /**
     * The year the picker should be centred on.
     *
     * Used by both pickers: the year picker's row of years, and the year the month picker opens on.
     */
    protected open fun pickerYear(): Int = java.time.LocalDate.now().year

    /** Called with the year chosen by the picker. Only reached when [pickerChoosesYear] is true. */
    protected open fun onYearPicked(year: Int) {
    }

    /**
     * The 1-based month the heading's month picker should open on, or null for the current month.
     *
     * The month view is the screen with a month of its own to offer, so it opens the picker where it
     * is looking rather than on today's month; the other screens have no month to name and leave it
     * at null.
     */
    protected open fun pickerMonth(): Int? = null

    /**
     * Called with the month chosen from the heading's picker.
     *
     * The default leaves for that month by opening the month view, which is what picking a month
     * means from a screen that is not the month view. The month view overrides this and moves its
     * own grid: it *is* the destination, so starting another copy of itself would stack a screen the
     * user did not ask for and lose the one they were on.
     */
    protected open fun onMonthPicked(year: Int, month: Int) {
        jumpToMonth(year, month)
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

    /**
     * True to build the title bar's menu at all, overflow entries included.
     *
     * Every other screen wants it: the overflow is the way to the calendar-wide actions - refresh,
     * import, the calendars list, the other screens. The per-calendar screen does not. Everything it
     * does is a row on the screen itself, and a menu there would offer to leave the calendar being
     * edited; hiding the entries one by one still leaves the three dots in the bar, so a screen that
     * wants none asks for no menu instead. See [onCreateOptionsMenu].
     */
    protected open val showsTitleBarMenu: Boolean = true

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

    /**
     * The one menu-driven navigation step waiting for the next main-loop turn.
     *
     * A drawer choice and an overflow choice both come through here, and neither is run inside its
     * menu callback: a drawer choice snaps the drawer shut without animation and posts the step, and
     * an overflow choice posts one retry for its popup to be dismissed. See [requestNavigation] and
     * [schedulePendingNavigation].
     */
    private val pendingNavigation = PendingNavigation()

    /** True while an overflow-menu callback is still on the stack, so its popup has not gone yet. */
    private var menuCallbackActive = false

    /** Posts the pending navigation step. Everything that uses it runs on the main thread. */
    private val uiHandler = Handler(Looper.getMainLooper())

    override fun onResume() {
        super.onResume()
        // A drawer that is open or half closed when this screen comes back is snapped shut. A panel
        // left mid-slide (the screen was paused while its drawer was closing) would otherwise be
        // the first thing the revealed screen shows, and the next open would start from a partial
        // offset. Closing it here costs nothing and leaves the drawer at a clean 0.
        if (drawerLayout.isDrawerOpen(GravityCompat.START) ||
            drawerLayout.isDrawerVisible(GravityCompat.START)
        ) {
            drawerLayout.closeDrawer(GravityCompat.START, false)
        }
        // The drawer's current-screen mark, put back on this screen every time it comes forward.
        //
        // It used to be set once, in onCreate, and nothing touched it again. A screen that was
        // revealed rather than created - the week view uncovered when the three day view opened over
        // it finished, for instance - came back with its own drawer unchanged, which is the right
        // item for it; but a screen whose drawer had been opened and had its item moved while it was
        // covered came back naming the other screen. Re-applying it on every resume makes the
        // drawer always describe the screen that is actually on top, whichever way it arrived.
        applyDrawerCheckedItem()
        // The default view may have been changed while this screen was stopped behind the settings
        // screen; a landing screen hands over to the new one here rather than after a relaunch.
        applyConfiguredLandingView()
    }

    override fun onPause() {
        // A menu choice is only valid while this screen is in front. If anything takes the screen
        // away before the choice has run, drop it rather than starting or finishing a screen from
        // the background.
        pendingNavigation.cancel()
        super.onPause()
    }

    /**
     * Starts [intent] with the app's forward pair passed explicitly with the operation.
     *
     * Direction travels with the start instead of being stored on the activity and read whenever
     * the platform gets round to building the transition - the per-activity scheme this replaces,
     * which was silently dropped mid-animation and was the source of every stacking report. Every
     * start from a host screen goes through this overload: the one-argument `startActivity(Intent)`
     * delegates to it, so no call site can miss the pair. The app passes no ActivityOptions of its
     * own, so replacing the bundle here cannot lose a caller's intent.
     */
    @Suppress("DEPRECATION")
    override fun startActivity(intent: Intent, options: Bundle?) {
        val pair = animationPairFor(SlideOperation.START)
        super.startActivity(
            intent,
            ActivityOptions.makeCustomAnimation(this, pair.enterAnim, pair.exitAnim).toBundle(),
        )
    }

    /**
     * The view type this screen shows when it is the landing screen, or null when it is not one of
     * the views the "Default view" setting names.
     *
     * The day, three day, week, month and agenda screens set this: they are the views the setting
     * offers, and each one compares itself with the setting when it resumes and when it decides
     * what back does. The year view, the to-do list and the other tool screens leave it null and
     * stay where they are - they are destinations reached from the calendar, not the state the app
     * opens in.
     */
    protected open val landingViewType: Int? = null

    /**
     * Moves a landing screen to the view the "Default view" setting now names.
     *
     * The launcher redirect reads that setting once, when the app is started, so a change made in
     * the settings while the app is open used to take effect only after a relaunch: the user came
     * back to the calendar and it was still showing the view they had just replaced. Resume is the
     * moment that matters, because it is when the app returns from the settings screen.
     *
     * The screen opened here carries no opener and no opener name, so it is the new landing screen,
     * and its own check on resume finds that it matches the setting - which is what settles the
     * switch instead of letting the two screens hand over to each other. A screen that is not
     * effectively landing - one opened over another and showing something other than the configured
     * view - is a step into the calendar and never gets this far.
     */
    private fun applyConfiguredLandingView() {
        val mine = landingViewType ?: return
        // The same resolution the launcher redirect makes, so the two cannot disagree about what
        // "the default view" means.
        val configured = Utils.getViewTypeFromIntentAndSharedPref(this)
        if (!isEffectivelyLanding(hasOpener(), configured == mine)) {
            return
        }
        val targetView = landingViewToShow(configured, mine, hasOpener()) ?: return
        val target = ExperimentalCalendarUi.screenForViewType(targetView) ?: return
        if (target.isInstance(this)) {
            return
        }
        startActivity(
            Intent(this, target).apply {
                // The launcher redirect opens the landing screen on today; handing over to another
                // landing screen is the same move, so it lands on the same day.
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis())
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        // A switch between landing screens is a forward step, not a return: this screen leaves to
        // the left and the new one arrives the way any screen opened from here does. The start
        // above already carries that pair; finishForward() passes the matching one with the finish.
        finishForward()
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
        // The window, the host container and the app bar all take the drawn screen's tonal surface.
        //
        // The theme's own roles cannot be trusted for this: on the Black appearance the palette can
        // resolve the surface to #000 (and the window background is the theme's pure black), so the
        // bar and the strip behind the system bars stayed black while the grid was expected to be a
        // dark surface. The colour is resolved once here and painted on all three, so the chrome and
        // the grids are the same tonal step in every appearance. See TonalSurface.kt.
        applyTonalChrome(toolbar)
        setSupportActionBar(toolbar)

        // A screen that is arrived at rather than switched to has no drawer to open: the drawer is
        // locked shut before anything can install a drawer indicator for it.
        if (navigationIconShowsBack) {
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

        val toggle: ActionBarDrawerToggle?
        // The drawer toggle belongs to the screens that show the drawer, and only to them.
        //
        // syncState() is what puts the toggle's hamburger on the toolbar, and it replaces whatever
        // navigation icon is already there - which is why the back arrow never survived on the
        // screens that are arrived at: the toggle was installed afterwards and drew its own icon over
        // it. Such a screen also has its drawer locked shut, so there is nothing for a toggle to
        // toggle; leaving it out is what keeps the arrow on the bar and the drawer closed.
        if (navigationIconShowsBack) {
            toggle = null
        } else {
            val drawerToggle = ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.app_label, R.string.app_label
            )
            drawerLayout.addDrawerListener(drawerToggle)
            drawerToggle.syncState()
            toggle = drawerToggle
        }

        // The back arrow is installed here, after the toggle has had its chance to put a hamburger on
        // the bar, so it is the toolbar's navigation icon rather than something painted over later.
        // It goes through the back dispatcher, so the arrow does exactly what the system back gesture
        // does on this screen - finish it and reveal the list underneath.
        if (navigationIconShowsBack) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            toolbar.setNavigationContentDescription(
                androidx.appcompat.R.string.abc_action_bar_up_description
            )
        }

        // The hamburger, the three dots and the menu they open, in the theme's own colour.
        //
        // The toggle's arrow paints itself and ignores a tint, the overflow dots are the platform's
        // own fixed-colour drawable, and the popup's entries took their colour from an overlay that
        // left them white on a light popup. Each is set here so the light appearance reads properly.
        val barIconColor = resolveBarIconColor()
        toggle?.drawerArrowDrawable?.setColor(barIconColor)
        toolbar.setOverflowIcon(
            androidx.appcompat.content.res.AppCompatResources
                .getDrawable(this, R.drawable.ic_overflow_menu)
        )
        toolbar.popupTheme = R.style.ThemeOverlay_Etar_PopupMenu

        // The to-do entry's icon is the today icon in the drawer's 24dp size, so today's day number
        // is drawn into it - the same treatment the toolbar's own today button gets. The size comes
        // from @drawable/today_icon_24: the bar's 28dp form was the one drawer row whose icon did
        // not line up with the 24dp view icons above it.
        navigationView.menu.findItem(R.id.action_todo)?.let { item ->
            (item.icon as? LayerDrawable)?.let { icon ->
                Utils.setTodayIcon(icon, this, Utils.getTimeZone(this, null))
            }
        }

        // Back is the platform's own for a screen that was opened over another one: it finishes, and
        // a finish plays the reverse of the way in. A screen that is effectively landing - the only
        // screen, or one showing the view the settings name as the default - leaves the app instead.
        // Nothing is started to "return" to: doing that put a month view the user had not asked for
        // between them and the launcher, and its open transition made the movement go left twice.
        // The one case handled here is the drawer: while it is open, back closes it.
        val callback = object : OnBackPressedCallback(exitOnBack || drawerLayout.isDrawerOpen(GravityCompat.START)) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    // Backing out of the drawer cancels the choice it was about to act on; it must
                    // not navigate after the drawer has closed.
                    pendingNavigation.cancel()
                    drawerLayout.closeDrawers()
                } else if (exitOnBack) {
                    // finishAffinity(), not a new screen and not just finish(): a landing screen is
                    // the state the app opens in, so its back leaves the app. finish() would only
                    // reveal whatever is underneath - the year view that opened this month view,
                    // for instance - which is exactly the walk back the landing rule exists to stop.
                    // The return pair travels with the operation, exactly as it does for a finish().
                    val pair = animationPairFor(SlideOperation.FINISH_RETURN)
                    finishAffinity()
                    @Suppress("DEPRECATION")
                    overridePendingTransition(pair.enterAnim, pair.exitAnim)
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
                // Nothing is gated on this any more: a choice made from the drawer closes it
                // without animation and runs on the next main-loop turn, so there is no sliding
                // panel for a transition to be declared under.
            }
        })
    }

    /**
     * Keeps the intent current when a running instance is reused.
     *
     * The launcher brings the screen that is already running forward instead of starting a second
     * copy, and so do the tool entries, which still move a running screen rather than stacking one.
     * The intent that arrives is the one that says what was asked for - including whether back
     * should reveal the screen underneath. Without this the first intent would go on being
     * reported, and a screen first opened as a landing screen would go on treating back as "leave
     * the app" after it had been opened over something that back is meant to reveal.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The intent decides which back this is, so the callback follows it.
        backCallback?.isEnabled = exitOnBack || drawerLayout.isDrawerOpen(GravityCompat.START)
    }

    /** Installs the Compose UI into the host's content slot. */
    protected fun setM3eContent(content: @Composable () -> Unit) {
        val dark = isDarkAppearance(this)

        findViewById<ComposeView>(R.id.m3e_content).setContent {
            EtarM3ETheme(darkTheme = dark) {
                content()
            }
        }
    }

    /**
     * Paints the tonal surface on the window, the host container and the app bar.
     *
     * The palette's own roles are read first, then sanitised with [tonalSurfaceArgb]. On the Black
     * appearance they can resolve to #000 - the window background is the theme's pure black and the
     * dynamic surface can come out black as well - which left the bar and the strips behind the
     * system bars black while the grid was meant to be a dark surface. Resolving the colour once and
     * painting all three with it keeps the chrome and the grids on the same tonal step in every
     * appearance; a palette that supplied a real surface is used unchanged.
     */
    private fun applyTonalChrome(toolbar: Toolbar) {
        val tonal = tonalSurfaceArgb(this, isDarkAppearance(this)) ?: return
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(tonal))
        findViewById<android.view.View>(R.id.coordinator_layout)?.setBackgroundColor(tonal)
        toolbar.setBackgroundColor(tonal)
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
        // The click and the rounded highlight live on the inner control, not on the root: the root
        // fills the bar (so the heading is measured against the width that is left beside the action
        // icons), and a ripple on it would be a square across the whole bar. See the layout.
        val control = view.findViewById<android.view.View>(R.id.m3e_title_button)
        if (titleOpensPicker) {
            control.setOnClickListener { showTitlePicker() }
        } else {
            control.isClickable = false
            control.isFocusable = false
            control.background = null
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
     * Opens the picker behind the toolbar heading, and behind the "go to" action.
     *
     * One control for both: the heading names a period and "go to" asks to move to another one, so
     * they are the same question and were given the same picker on every screen. The picker itself
     * is the app's own - a date for the screens whose heading is a day or a range of days, a month
     * for the month screen, a year for the year screen - and each of those moves the screen that is
     * showing instead of starting another one.
     *
     * The dialog lives in M3eMonthPickerDialog, which is shared with the View-based calendar.
     * Building a ComposeView inside a PopupWindow here instead would crash on attach: a popup is its
     * own window and Compose needs a ViewTreeLifecycleOwner from the view tree it is added to.
     *
     * It opens on the period the screen is showing when the screen has one, and the choice goes to
     * [onDatePicked], [onMonthPicked] or [onYearPicked] according to which picker was used.
     */
    private fun showTitlePicker() {
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
            pickerYear(),
            pickerMonth(),
            java.util.function.BiConsumer { year, month -> onMonthPicked(year, month) },
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

    /**
     * Opens the month view on the given month, leaving this screen underneath it.
     *
     * The picker belongs to the screen that is showing, so the month view is opened *over* it: back
     * returns here, and the month is not a landing screen that would hand over to the default view
     * the moment it resumes. The one exception is when the month *is* the configured default view:
     * then it is where the app begins wherever it is opened, so it is started without the opener
     * extras and its back leaves the app.
     */
    private fun jumpToMonth(year: Int, month: Int) {
        val millis = java.time.LocalDate.of(year, month, 1)
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // With the experimental UI on, "the month view" is the all-Compose screen. Sending the picker
        // result to the classic activity put the old View-based grid back on screen from every heading
        // in the app, which is not what the switch promises.
        if (com.android.calendar.composeui.ExperimentalCalendarUi.enabled(this)) {
            val intent = Intent(this, com.android.calendar.composeui.ComposeMonthActivity::class.java)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, millis)
                .addFlags(
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            if (!ExperimentalCalendarUi.isConfiguredDefault(
                    this,
                    CalendarController.ViewType.MONTH,
                )
            ) {
                // This screen stays underneath, so the month it opens is told which screen to hand
                // back to. Without that the month read itself as a landing screen.
                intent.openingOver(this)
            }
            startActivity(intent)
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
        // The choice is not acted on inside this callback. The drawer is snapped shut without
        // animation and the step runs on the next main-loop turn, so no panel is sliding alongside
        // the transition and nothing waits for a close animation. See requestNavigation().
        val action: (() -> Unit)? = when (item.itemId) {
            R.id.day_menu_item -> ({ openCalendarView(Utils.INTENT_VALUE_VIEW_TYPE_DAY) })
            // The three day view is one of the calendar views, so it moves through the same rule
            // as the others instead of opening through openScreen(): from the landing screen it
            // opens over it, and from a view that already has one it takes that view's place.
            R.id.threeday_menu_item -> ({
                openComposeCalendarView(
                    com.android.calendar.threeday.compose.ComposeThreeDayActivity::class.java,
                    CalendarController.ViewType.THREE_DAY,
                    beginMillis = anchorDayOnScreenMillis(),
                )
            })
            R.id.week_menu_item -> ({ openCalendarView(Utils.INTENT_VALUE_VIEW_TYPE_WEEK) })
            R.id.month_menu_item -> ({ openCalendarView(Utils.INTENT_VALUE_VIEW_TYPE_MONTH) })
            R.id.year_menu_item -> ({ openScreen(ComposeScreen.YEAR) })
            R.id.agenda_menu_item -> ({ openScreen(ComposeScreen.AGENDA) })
            R.id.action_date_calculator -> ({ openScreen(ComposeScreen.DATE_CALCULATOR) })
            R.id.action_countdown -> ({ openScreen(ComposeScreen.COUNTDOWN) })
            R.id.action_todo -> ({ openScreen(ComposeScreen.TODO) })
            R.id.action_settings -> ({ openScreen(ComposeScreen.SETTINGS) })
            R.id.action_about -> ({ openScreen(ComposeScreen.ABOUT) })
            else -> null
        }
        if (action == null) {
            return false
        }
        requestNavigation(action, fromMenu = false)
        return true
    }

    /**
     * Holds a menu-driven navigation step for one main-loop turn, then runs it.
     *
     * One slot: a second choice replaces the first, so a double tap cannot navigate twice. The step
     * is never run inside the menu callback itself - that is where the drawer's panel or the popup
     * is still animating, and a start or a close requested then loses its transition.
     *
     * A drawer choice also closes the drawer **without animation**. A panel still sliding left as
     * the next transition starts was both the delay the user felt and a second, leftward slide; a
     * drawer that is gone instantly has neither. Nothing is lost by snapping it: the drawer is not
     * seen again once the next screen is up.
     *
     * @param fromMenu true for an overflow-menu choice, whose popup is dismissed only after the
     *   callback returns, so the step has to wait one main-loop turn for it.
     */
    private fun requestNavigation(action: () -> Unit, fromMenu: Boolean) {
        pendingNavigation.request(action)
        if (fromMenu) {
            // The popup is dismissed after this callback returns; schedulePendingNavigation() sees
            // the callback still on the stack, defers, and posts the single retry.
            menuCallbackActive = true
            schedulePendingNavigation()
        } else {
            // Snap the drawer shut rather than animating it closed, then run on the next turn.
            drawerLayout.closeDrawer(GravityCompat.START, false)
            uiHandler.post { schedulePendingNavigation() }
        }
    }

    /**
     * Runs the waiting navigation step once the menu popup is out of the way.
     *
     * The only remaining gate is the overflow popup, which is dismissed after its callback returns;
     * a menu choice therefore takes exactly one posted retry. The drawer is not a gate: it was
     * snapped shut before the step was posted, and `onDrawerClosed` gates nothing.
     */
    private fun schedulePendingNavigation() {
        if (!pendingNavigation.hasPending) {
            return
        }
        if (!isReadyToNavigate(menuDismissing = menuCallbackActive)) {
            // The menu callback has returned by the time this retry runs; it must not keep seeing
            // the callback as active. One retry, not a chain.
            menuCallbackActive = false
            uiHandler.post { schedulePendingNavigation() }
            return
        }
        // take() empties the slot before the step runs, so a second request cannot run it again.
        pendingNavigation.take()?.invoke()
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
        // A screen can ask for no menu at all; see showsTitleBarMenu. Returning false leaves the
        // toolbar with no items, and an empty menu draws no overflow button.
        if (!showsTitleBarMenu) {
            return false
        }
        menuInflater.inflate(R.menu.all_in_one_title_bar, menu)

        // The classic calendar's chrome controls are not part of these screens.
        //
        // The landscape and tablet variants of the menu resource declare "show/hide the calendar
        // controls" - the toggle for the mini month and the calendar list the classic tablet layout
        // draws on its right - and the one tap view buttons that title bar can be asked to show.
        // This host has neither, and nothing here ever hid them, so on its side the toggle turned up
        // in the overflow: it is the entry the portrait menu does not declare, which is why the menu
        // looked right upright and had an extra item on its side. Hidden here rather than deleted
        // from the resource, because the classic AllInOneActivity inflates the same menu and still
        // uses the toggle on a tablet.
        menu.findItem(R.id.action_hide_controls)?.isVisible = false
        menu.findItem(R.id.action_view_day)?.isVisible = false
        menu.findItem(R.id.action_view_week)?.isVisible = false
        menu.findItem(R.id.action_view_month)?.isVisible = false

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

        // Every entry in the three dot menu, given the icon of the thing it does. Set here rather
        // than in the menu resource because that resource is shared with the classic calendar,
        // whose title bar is not part of this change.
        applyOverflowIcons(menu)

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
     * Gives the three dot menu's entries their icons.
     *
     * The menu resource carries titles only, which left the popup a column of words beside a bar of
     * icons. Each entry takes a drawable this app already draws for the same idea wherever there is
     * one - the drawer's own glyphs for the agenda list and the settings gear, the arrows already
     * used for refresh and for going back, and the today calendar - rather than a second set of
     * icons invented for the popup. The one exception is the import arrow, which had no equivalent
     * in res/drawable and was added as res/drawable/ic_m3e_import.xml.
     *
     * The colour is not chosen per icon: every icon in this menu is tinted by onCreateOptionsMenu
     * afterwards, so each one follows the bar's colour instead of the colour its file was built
     * with. The to-do entry is the one that is drawn rather than tinted: it is the drawer's today
     * icon with today's day number in it, the same treatment the title bar's own today button gets.
     */
    private fun applyOverflowIcons(menu: Menu) {
        // AppCompat keeps a menu entry's icon for the bar and drops it from the popup unless the menu
        // says icons are wanted - the popup asks MenuItemImpl.shouldShowIcon, which reports off by
        // default. That is why these rows were text only even where the resource already declared an
        // icon (the to-do entry does). Turning it on is what makes the icons below show at all.
        (menu as? androidx.appcompat.view.menu.MenuBuilder)?.setOptionalIconsVisible(true)
        // A left pointing arrow, the direction the app already draws "back" with: the entry goes to
        // a date the user names, which is a step back into the calendar from wherever they are.
        menu.findItem(R.id.action_goto)?.setIcon(R.drawable.ic_arrow_back)
        // The circular arrow pair. The drawable is named outline_repeat, but the path it draws is the
        // refresh glyph - two arrows chasing each other round - and there is no other one to use.
        menu.findItem(R.id.action_refresh)?.setIcon(R.drawable.ic_outline_repeat)
        // Calendars and software settings are different screens, but both are configured rather than
        // used, which is the one thing the gear says.
        menu.findItem(R.id.action_m3e_calendars)?.setIcon(R.drawable.outline_settings)
        menu.findItem(R.id.action_view_settings)?.setIcon(R.drawable.outline_settings)
        // The drawer's agenda entry, so the two ways to the same screen look like each other.
        menu.findItem(R.id.action_m3e_agenda)?.setIcon(R.drawable.outline_view_agenda)
        // An import turns an outside file back towards the calendar it is added to.
        menu.findItem(R.id.action_import)?.setIcon(R.drawable.ic_m3e_import)
        // The to-do entry: the drawer's today icon - in the 24dp form, so it sits with the menu's
        // other icons - with today's day number drawn into it by the same call the drawer uses.
        menu.findItem(R.id.action_todo)?.let { item ->
            val icon = androidx.appcompat.content.res.AppCompatResources
                .getDrawable(this, R.drawable.today_icon_24)
            item.icon = icon
            (icon as? LayerDrawable)?.let { layer ->
                Utils.setTodayIcon(layer, this, Utils.getTimeZone(this, null))
            }
        }
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
        // Anything that starts or finishes a screen is requested through requestNavigation(), not
        // run here: the popup is dismissed only after this callback returns, and a navigation
        // started while it is still dismissing is the same race as the drawer's panel. Actions that
        // change nothing but data - the refresh, the search field - stay synchronous.
        when (item.itemId) {
            R.id.action_today -> requestNavigation({ onToday() }, fromMenu = true)

            // "Go to" is the heading's picker, on every screen.
            //
            // The heading already names the period being shown and opens the right picker for it,
            // and each of those pickers moves the screen that is showing rather than starting
            // another one: a date on the day, three day, week and to-do screens, a month on the
            // month screen, a year on the year screen, and a day that moves the loaded window on
            // the agenda. The entry had no branch at all, so it closed the menu and did nothing.
            R.id.action_goto -> requestNavigation({ showTitlePicker() }, fromMenu = true)

            // The refresh the classic title bar offers, followed by the screen's own re-read.
            //
            // Both halves are needed. refreshCalendars asks the providers for fresh events, which
            // is all the classic calendar does - but the Compose grids and lists hold what they
            // last queried, so without the second half the screen kept showing the events it had
            // until it was left and opened again. See onRefresh().
            R.id.action_refresh -> {
                CalendarController.getInstance(this).refreshCalendars()
                onRefresh()
            }

            // No branch for action_search: the entry is a search field, and its listener carries the
            // query to SearchActivity. Selecting the item only expands that field.

            // The dialog the settings screen offers: a file on the device, or a url to download.
            //
            // This used to open a document picker and never look at the result, so choosing a file
            // did nothing at all. It now goes through the same activity the settings entry uses.
            R.id.action_import -> requestNavigation(
                {
                    startActivity(
                        Intent(
                            this,
                            com.android.calendar.chrome.compose.ComposeImportActivity::class.java,
                        )
                    )
                },
                fromMenu = true,
            )

            R.id.action_m3e_calendars ->
                requestNavigation({ openScreen(ComposeScreen.CALENDARS) }, fromMenu = true)
            R.id.action_m3e_agenda ->
                requestNavigation({ openScreen(ComposeScreen.AGENDA) }, fromMenu = true)
            R.id.action_view_settings ->
                requestNavigation({ openScreen(ComposeScreen.SETTINGS) }, fromMenu = true)
            R.id.action_todo ->
                requestNavigation({ openScreen(ComposeScreen.TODO) }, fromMenu = true)
            R.id.action_todo_edit -> requestNavigation({ onTodoEdit() }, fromMenu = true)

            else -> return false
        }
        return true
    }

    // --------------------------------------------------------------------------- navigation

    private enum class ComposeScreen {
        YEAR, AGENDA, DATE_CALCULATOR, COUNTDOWN, CALENDARS, SETTINGS, ABOUT, TODO
    }

    private fun openScreen(screen: ComposeScreen): Boolean {
        // Selecting the screen we are already on just closes the drawer.
        //
        // The calendar views do not come through here: they are peers of one another and move
        // through openComposeCalendarView, which keeps the stack two deep. This is the path for the
        // tool screens. What they open goes on top of them and back returns to them - except when
        // the entry names the screen this one was opened over, which is a step back.
        if (screen.name == currentScreenName) {
            return true
        }
        val target = when (screen) {
            ComposeScreen.YEAR -> com.android.calendar.year.compose.ComposeYearActivity::class.java
            ComposeScreen.AGENDA ->
                com.android.calendar.agenda.compose.ComposeAgendaActivity::class.java
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
        // The entry that names the screen this one was opened over is the same move as back: finish
        // this screen and let the platform reveal that one, with the return slide - no screen is
        // started. Starting the month over the year view instead is what read as a stacked
        // animation: two screens, and the entry slide rather than the return one.
        if (isOpenerScreen(target)) {
            finish()
            return true
        }
        val agendaDate = if (screen == ComposeScreen.AGENDA) agendaDateMillis() else null
        val launch = when (screen) {
            // The to-do screen opens on the day this screen is showing rather than on today, so a
            // week or three day view paged to another part of the calendar hands its day over -
            // and today when today is part of what is on screen.
            ComposeScreen.TODO ->
                com.android.calendar.todo.compose.ComposeTodoActivity.intent(this, anchorDayOnScreen())
            else -> Intent(this, target)
        }
        startActivity(launch.apply {
            // The agenda opens around the day this screen is on, when it has one, so coming from
            // the three day view lands on the days the user was looking at instead of on today.
            if (agendaDate != null) {
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, agendaDate)
            }
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
        }.openingOver(this))
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
     * With the experimental UI on this is one of the all-Compose screens, and the drawer's rule for
     * keeping those two deep applies; see [openComposeCalendarView]. The rest of this method is the
     * classic calendar path, which is still reached when the experimental UI is off.
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
            val experimentalViewType = Utils.viewTypeFromIntentValue(viewTypeValue)
            val experimentalTarget =
                ExperimentalCalendarUi.screenForViewType(experimentalViewType)
            if (experimentalTarget != null) {
                return openComposeCalendarView(experimentalTarget, experimentalViewType)
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
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        // Stepping out to the month is the reverse of the way in; a step along the day / three day /
        // week sequence keeps going leftwards. The start passed the forward pair with it, and the
        // finish does the same - see finishForward().
        if (leavesTheSequence) {
            finish()
        } else {
            finishForward()
        }
        return true
    }

    /**
     * Opens one of the all-Compose calendar views, keeping the stack two screens deep.
     *
     * The stack is the landing screen - the view the launcher opened, which is the one named under
     * "Default view" in the settings - and the screen the user is on now. A view asked for from the
     * landing screen opens *over* it ([CalendarViewStep.PUSH]) and the landing screen stays
     * underneath, because it is what back has to return to before the app is left. A view asked for
     * from a screen that was itself opened over the landing one replaces it
     * ([CalendarViewStep.REPLACE]), so the stack does not grow with every drawer choice. And asking
     * for the view that is underneath is a step back to it ([CalendarViewStep.REVEAL]) rather than a
     * second copy of it, which is what a plain start would leave - the landing screen re-intented
     * with an opener it does not have.
     *
     * A view that *is* the configured default view is the landing screen wherever it is opened (see
     * [isEffectivelyLanding]), so [calendarViewIntent] gives it no opener to hand back to.
     *
     * The movement follows the direction of travel. A push is a start, and a start plays the forward
     * slide declared in onResume: the new screen arrives from the right while this one leaves to the
     * left. A replace is a finish *and* a start, so this screen hands over with finishForward() - the
     * forward pair is part of its finish - because declaring nothing would let the platform reverse
     * the way in and send it out to the right. A reveal is a plain finish, and every finish that is
     * not a hand-over plays the reverse of the way in: this screen leaves to the right and the one
     * underneath comes back from the left.
     *
     * It used to finish the landing screen whenever the month was asked for, which left the month
     * alone in the stack: back went to the launcher instead of to the default view.
     */
    private fun openComposeCalendarView(
        target: Class<out android.app.Activity>,
        targetViewType: Int,
        beginMillis: Long? = null,
    ): Boolean {
        // Selecting the view that is already on screen just closes the drawer.
        if (target.isInstance(this)) {
            return true
        }

        val opener = openerClass()
        val step = calendarViewStep(
            isCalendarView = isCalendarViewScreen,
            hasOpener = hasOpener(),
            targetIsOpener = isOpenerScreen(target),
        )
        val begin = beginMillis ?: calendarViewDateMillis() ?: System.currentTimeMillis()

        when (step) {
            CalendarViewStep.REVEAL -> {
                // The screen this one was opened over is still running underneath it; finish and
                // let the platform reveal it exactly as it was left. The finish declares the return
                // pair itself.
                finish()
            }

            CalendarViewStep.PUSH -> {
                // The landing screen stays underneath, and this screen becomes the opener the view
                // being opened has to return to. Naming it is what lets that view's drawer tell
                // "go back to it" from "open another view over it".
                startActivity(calendarViewIntent(target, targetViewType, begin, javaClass.name))
            }

            CalendarViewStep.REPLACE -> {
                // This screen finishes, so the view taking its place must inherit the screen this
                // one was opened over - that is still the bottom of the stack. The view opens first,
                // then the finish declares the forward pair as part of itself.
                startActivity(calendarViewIntent(target, targetViewType, begin, opener))
                finishForward()
            }
        }
        return true
    }

    /**
     * The intent that opens [target] over the screen underneath it.
     *
     * [opener] names that screen when it is known; a screen that finishes while the view opens must
     * hand its own opener on, so the extra is inherited rather than renamed.
     *
     * A target that shows the configured default view gets neither extra: it is the landing screen
     * wherever it is opened, its back leaves the app, and an opener would only make it hand back to
     * a screen the session does not begin on.
     */
    private fun calendarViewIntent(
        target: Class<out android.app.Activity>,
        targetViewType: Int,
        beginMillis: Long,
        opener: String?,
    ): Intent = Intent(this, target).apply {
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
        if (!ExperimentalCalendarUi.isConfiguredDefault(this@M3eHostActivity, targetViewType)) {
            // The screen underneath stays, so the one being opened returns to it by finishing,
            // which the platform plays as the reverse of this entry.
            putExtra(ComposeCalendarViewActivity.EXTRA_RETURN_TO_OPENER, true)
            if (opener != null) {
                putExtra(ComposeCalendarViewActivity.EXTRA_OPENER_CLASS, opener)
            }
        }
        // SINGLE_TOP only: an instance that is already running is not brought forward here. The one
        // that could be is the landing screen of the same class as the view asked for, and handing
        // it a new opener would leave it finishing itself on the next drawer choice - taking with it
        // the screen back is meant to return to.
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    /**
     * Marks this intent as opening a screen over [opener], which stays underneath.
     *
     * The screen is passed in rather than read as `javaClass` where the intent is built: inside an
     * `apply` block the implicit receiver is the Intent, so a bare `javaClass` there names
     * android.content.Intent. That slip gave every screen opened through [openScreen] the wrong
     * opener, so none of them recognised the screen underneath and the month was pushed over them
     * instead of being revealed - the two leftward slides reported from the year view, the agenda
     * and the other tool screens.
     */
    private fun Intent.openingOver(opener: android.app.Activity): Intent = apply {
        putExtra(ComposeCalendarViewActivity.EXTRA_RETURN_TO_OPENER, true)
        putExtra(ComposeCalendarViewActivity.EXTRA_OPENER_CLASS, opener.javaClass.name)
    }

    /** True when this screen was opened over another one, which back should reveal. */
    private fun hasOpener(): Boolean =
        intent?.getBooleanExtra(ComposeCalendarViewActivity.EXTRA_RETURN_TO_OPENER, false) == true

    /** The class name of the screen this one was opened over, or null when it is not known. */
    private fun openerClass(): String? =
        intent?.getStringExtra(ComposeCalendarViewActivity.EXTRA_OPENER_CLASS)

    /**
     * True when [target] is the screen this one was opened over.
     *
     * An entry that names it is the back gesture made from the menu: finishing this screen reveals
     * that one, so the request is a reveal rather than a start. The rule is the same for every kind
     * of host screen - a tool screen's entry for the month it came from is a step back just as a
     * calendar view's is - and it is only ever true when the opener was named: a screen that was not
     * opened over anything, or was opened by a caller that did not know the class, has no entry that
     * is a step back, and its menu entries all start something.
     */
    private fun isOpenerScreen(target: Class<out android.app.Activity>): Boolean =
        hasOpener() && openerClass() == target.name

    /**
     * True for the screens that are themselves one of the calendar views in the drawer.
     *
     * The day, three day, week and month entries are peers, so choosing one from another replaces
     * the screen that is showing and the stack stays the landing screen plus the view on top. A
     * tool screen - the year view, the agenda, the to-do list - is not one of those peers: what it
     * opens goes on top of it and back returns to it, which is how those screens have always
     * behaved.
     */
    protected open val isCalendarViewScreen: Boolean = false

    /** True for a view that is not part of the day / three day / week sequence. */
    private fun isFirstOfSequence(viewTypeValue: String): Boolean =
        viewTypeValue == Utils.INTENT_VALUE_VIEW_TYPE_MONTH ||
            viewTypeValue == Utils.INTENT_VALUE_VIEW_TYPE_AGENDA

    /**
     * What the title bar's today button does.
     *
     * By default it goes to the month view, which is right for a screen that has no notion of
     * "today on this screen". The three day view shows dates, so it overrides this and moves
     * itself to today rather than leaving the screen.
     */
    protected open fun onToday() {
        openCalendarView(Utils.INTENT_VALUE_VIEW_TYPE_MONTH)
    }

    /**
     * Called by the title bar's refresh action, after the calendars have been asked to refresh.
     *
     * The screens that draw events bump the same key here that they bump when they resume, which is
     * what makes the grid or the list run its query again. A screen with nothing of its own to
     * re-read - the date calculator, the about screen - leaves this alone: the provider refresh has
     * already happened by the time it is called.
     */
    protected open fun onRefresh() {
    }

    /**
     * True once this screen has decided to finish by handing over to the screen it is opening.
     *
     * The direction is part of the finish, not a separate declaration: [finishForward] sets this,
     * and [finish] reads it to pick the pair. It starts false on every screen, which is the return
     * direction - the only way a screen may move left as it closes is a hand-over.
     */
    private var finishingForward = false

    /**
     * Finishes this screen with its animation passed explicitly to the finish.
     *
     * `overridePendingTransition` is the mechanism that takes the pair with the operation, so it
     * cannot be dropped later or read at a moment when the activity is in the wrong state - which is
     * exactly what the per-activity override used to do. Every way a host screen closes comes through
     * here: the back key, the menu entry that reveals the screen underneath, a hand-over, and the
     * landing screen leaving the app.
     *
     * The pair is computed before `super.finish()` so nothing can run between the two calls, and
     * `overridePendingTransition` is called immediately after `finish()` - the ordering the platform
     * expects.
     */
    @Suppress("DEPRECATION")
    override fun finish() {
        val pair = animationPairFor(finishOperation(finishingForward))
        super.finish()
        overridePendingTransition(pair.enterAnim, pair.exitAnim)
    }

    override fun onDestroy() {
        // A posted navigation step must not outlive the activity: if this instance is going away for
        // another reason, the runnable would hold it and could navigate from the background.
        uiHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /**
     * Finishes this screen because the screen it is opening takes its place.
     *
     * The direction is decided before the finish so [finish] can pass the forward pair with it: the
     * replacement view keeps the movement going left instead of reversing halfway.
     */
    protected fun finishForward() {
        finishingForward = true
        finish()
    }

    companion object {
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
