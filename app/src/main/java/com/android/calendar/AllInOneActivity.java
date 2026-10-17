/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (C) 2022 The Calyx Institute
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

package com.android.calendar;

import android.content.ActivityNotFoundException;
import static android.provider.CalendarContract.Attendees.ATTENDEE_STATUS;
import static android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY;
import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;
import static android.provider.CalendarContract.EXTRA_EVENT_END_TIME;

import android.Manifest;
import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Events;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.android.calendar.CalendarController.EventHandler;
import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarController.ViewType;
import com.android.calendar.agenda.AgendaFragment;
import com.android.calendar.alerts.AlertService;
import com.android.calendar.month.MonthByWeekFragment;
import com.android.calendar.selectcalendars.SelectVisibleCalendarsFragment;
import com.android.calendar.settings.GeneralPreferences;
import com.android.calendar.settings.ViewDetailsPreferences;
import com.android.calendar.theme.DynamicThemeKt;
import com.android.calendar.calendarcommon2.Time;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import java.io.File;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import ws.xsoh.etar.R;
import ws.xsoh.etar.databinding.AllInOneMaterialBinding;
import ws.xsoh.etar.databinding.DateRangeTitleBinding;

public class AllInOneActivity extends AbstractCalendarActivity implements EventHandler,
        OnSharedPreferenceChangeListener, SearchView.OnQueryTextListener, SearchView.OnSuggestionListener, NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "AllInOneActivity";
    private static final boolean DEBUG = false;
    private static final String EVENT_INFO_FRAGMENT_TAG = "EventInfoFragment";
    private static final String BUNDLE_KEY_RESTORE_TIME = "key_restore_time";
    private static final String BUNDLE_KEY_EVENT_ID = "key_event_id";
    /** Must match ComposeYearActivity.EXTRA_GOTO_EXPLICIT. */
    public static final String EXTRA_GOTO_EXPLICIT =
            "com.android.calendar.extra.GOTO_EXPLICIT";
    /** Must match ComposeYearActivity.EXTRA_INITIAL_YEAR. */
    public static final String EXTRA_INITIAL_YEAR =
            "com.android.calendar.extra.INITIAL_YEAR";

    private static final String BUNDLE_KEY_RESTORE_VIEW = "key_restore_view";
    private static final int HANDLER_KEY = 0;
    private static final int PERMISSIONS_REQUEST_WRITE_CALENDAR = 0;
    private static final int PERMISSIONS_REQUEST_POST_NOTIFICATIONS = 1;

    // Indices of buttons for the drop down menu (tabs replacement)
    // Must match the strings in the array buttons_list in arrays.xml and the
    // OnNavigationListener
    private static final int BUTTON_DAY_INDEX = 0;
    private static final int BUTTON_WEEK_INDEX = 1;
    private static final int BUTTON_MONTH_INDEX = 2;
    private static final int BUTTON_AGENDA_INDEX = 3;
    private static boolean mIsMultipane;
    private static boolean mIsTabletConfig;
    private static boolean mShowAgendaWithMonth;
    private static boolean mShowEventDetailsWithAgenda;
    int mOrientation;
    BroadcastReceiver mCalIntentReceiver;
    private CalendarController mController;
    // Create an observer so that we can update the views whenever a
    // Calendar event changes.
    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            eventsChanged();
        }
    };

    /**
     * The same reaction, registered against Instances.
     *
     * The day, week and month grids all read CalendarContract.Instances, and CalendarProvider treats
     * Instances as its own URI: notifying Events - which is what creating or editing an event does -
     * does not reach an observer registered on Events only. That is why an event created from the
     * month view did not appear until the app was restarted, while the agenda, which queries
     * Instances directly, had it straight away.
     */
    private final ContentObserver mInstancesObserver = new ContentObserver(new Handler()) {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            eventsChanged();
        }
    };
    private boolean mOnSaveInstanceStateCalled = false;
    private boolean mBackToPreviousView = false;
    /** Kept so it can be disabled when back would exit the app (enables predictive back). */
    private OnBackPressedCallback mBackCallback;
    /** The theme mode this activity was created with, to detect changes made in settings. */
    private String mAppliedThemeMode;

    /**
     * What the debug overlay says about the configuration, filled in when it is worked out.
     *
     * Shown over the top left of the screen, so which layout and which fragment a screen came from can
     * be read off it rather than worked out from behaviour.
     */
    private ContentResolver mContentResolver;
    private int mPreviousView;
    private int mCurrentView;
    /** Number of weeks displayed by the month view */
    private static final int MONTH_VIEW_WEEKS = 6;
    /** Month shown by the month view (from its title updates), 0 if unknown */
    private long mMonthViewDisplayedMillis;
    /** Day shown by the day view, or first day of the week shown by the week view, 0 if unknown */
    private long mDayWeekViewDisplayedMillis;

    /**
     * The fragments currently in the main pane and in the secondary pane.
     *
     * Kept so a view switch can bring an existing fragment back rather than build a new one. See
     * attachOrReuse.
     */
    private Fragment mAttachedMainFragment;
    /**
     * The tag the main pane's fragment is filed under by the last committed transaction, while that
     * transaction has not executed yet. See findOrCreateFragment.
     */
    private String mPendingMainPaneTag;
    private Fragment mAttachedSecondaryFragment;

    private boolean mPaused = true;
    private boolean mUpdateOnResume = false;
    private boolean mHideControls = false;
    private boolean mShowSideViews = true;
    private boolean mShowWeekNum = false;
    private TextView mHomeTime;
    private TextView mDateRange;
    private TextView mWeekTextView;
    private View mMiniMonth;
    private View mCalendarsList;
    private View mMiniMonthContainer;
    private final AnimatorListener mSlideAnimationDoneListener = new AnimatorListener() {

        @Override
        public void onAnimationCancel(Animator animation) {
        }

        @Override
        public void onAnimationEnd(android.animation.Animator animation) {
            int visibility = mShowSideViews ? View.VISIBLE : View.GONE;
            mMiniMonth.setVisibility(visibility);
            mCalendarsList.setVisibility(visibility);
            mMiniMonthContainer.setVisibility(visibility);
        }

        @Override
        public void onAnimationRepeat(android.animation.Animator animation) {
        }

        @Override
        public void onAnimationStart(android.animation.Animator animation) {
        }
    };
    private FloatingActionButton mFab;
    private View mSecondaryPane;
    private String mTimeZone;
    private boolean mShowCalendarControls;
    private boolean mShowEventInfoFullScreenAgenda;
    private boolean mShowEventInfoFullScreen;
    private int mWeekNum;
    private int mCalendarControlsAnimationTime;
    private int mControlsAnimateWidth;
    private int mControlsAnimateHeight;
    private long mViewEventId = -1;
    private long mIntentEventStartMillis = -1;
    private long mIntentEventEndMillis = -1;
    private int mIntentAttendeeResponse = Attendees.ATTENDEE_STATUS_NONE;
    private boolean mIntentAllDay = false;
    private Activity mActivity;
    private AllInOneMaterialBinding binding;
    private DrawerLayout mDrawerLayout;
    private Toolbar mToolbar;

    /** The toolbar's title view, so the caret can be re-applied when the title text changes. */
    private TextView mToolbarTitle;

    /** Open month picker dialog, if any. */
    private android.app.Dialog mTitlePopup;
    private NavigationView mNavigationView;
    private CalendarToolbarHandler mCalendarToolbarHandler;
    // Action bar
    private ActionBar mActionBar;
    private SearchView mSearchView;
    private MenuItem mSearchMenu;
    private MenuItem mControlsMenu;
    private MenuItem mViewSettings;
    private Menu mOptionsMenu;
    private QueryHandler mHandler;
    private final Runnable mHomeTimeUpdater = new Runnable() {
        @Override
        public void run() {
            mTimeZone = Utils.getTimeZone(AllInOneActivity.this, mHomeTimeUpdater);
            updateSecondaryTitleFields(-1);
            AllInOneActivity.this.invalidateOptionsMenu();
            Utils.setMidnightUpdater(mHandler, mTimeChangesUpdater, mTimeZone);
        }
    };
    // runs every midnight/time changes and refreshes the today icon
    private final Runnable mTimeChangesUpdater = new Runnable() {
        @Override
        public void run() {
            mTimeZone = Utils.getTimeZone(AllInOneActivity.this, mHomeTimeUpdater);
            AllInOneActivity.this.invalidateOptionsMenu();
            Utils.setMidnightUpdater(mHandler, mTimeChangesUpdater, mTimeZone);
        }
    };

    private String mHideString;
    private String mShowString;
    // Params for animating the controls on the right
    private LayoutParams mControlsParams;
    private LinearLayout.LayoutParams mVerticalControlsParams;
    private AllInOneMenuExtensionsInterface mExtensions = ExtensionsFactory
            .getAllInOneMenuExtensions();

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getAction();
        if (DEBUG)
            Log.d(TAG, "New intent received " + intent);

        // The year view (and its drawer) asks for an explicit view type; the plain ACTION_VIEW
        // path below keeps the current view instead, so it has to be handled separately.
        int requestedView = Utils.viewTypeFromIntentValue(
                intent.getStringExtra(Utils.INTENT_KEY_VIEW_TYPE));
        boolean explicitView = requestedView != -1;
        // Read here, before anything else looks at the intent: onResume runs after this and needs to
        // know the month was named rather than tapped. See mExplicitMonthJump.
        mExplicitMonthJump = intent.getBooleanExtra(EXTRA_GOTO_EXPLICIT, false);

        // Don't change the date if we're just returning to the app's home
        if ((Intent.ACTION_VIEW.equals(action) || explicitView)
                && !intent.getBooleanExtra(Utils.INTENT_KEY_HOME, false)) {
            long millis = parseViewAction(intent);
            if (millis == -1) {
                millis = Utils.timeFromIntentInMillis(intent);
            }
            if (millis != -1 && mViewEventId == -1 && mController != null) {
                Time time = new Time(mTimeZone);
                time.set(millis);
                time.normalize();
                // The time is passed as the selected time as well as the start.
                //
                // The controller keeps the time it already has when the time it is asked for
                // falls inside the range it is given, which is a rule that stops a refresh from
                // jumping the grid away from what the user is looking at. This call gives the same
                // value for both ends of that range, and the range is a whole month when a month
                // is picked, so asking for the month that contains today left the controller on
                // today - and the refresh this activity sends on resume then put the month view
                // back where it already was. Choosing a month is an explicit "go there", so the
                // time is passed as the selected one, which the controller takes unconditionally.
                mController.sendEvent(this, EventType.GO_TO, time, time, time, -1,
                        explicitView ? requestedView : ViewType.CURRENT,
                        CalendarController.EXTRA_GOTO_TIME
                                | (mExplicitMonthJump
                                   ? CalendarController.EXTRA_GOTO_EXPLICIT : 0),
                        null, null);
            }
        }
        // Keep the activity's own intent in sync so later reads see the new extras.
        setIntent(intent);
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        DynamicThemeKt.applyTheme(this);
        mActivity = this;
        mAppliedThemeMode = Utils.getSharedPreference(this,
                GeneralPreferences.KEY_THEME_PREF, "system");
        // This needs to be created before setContentView
        mController = CalendarController.getInstance(this);

        // Check and ask for most needed permissions
        checkAppPermissions();

        // Create notification channels
        AlertService.createChannels(this);

        // Get time from intent or icicle
        long timeMillis = -1;
        int viewType = -1;
        final Intent intent = getIntent();
        if (icicle != null) {
            timeMillis = icicle.getLong(BUNDLE_KEY_RESTORE_TIME);
            viewType = icicle.getInt(BUNDLE_KEY_RESTORE_VIEW, -1);
        } else {
            String action = intent.getAction();
            if (Intent.ACTION_VIEW.equals(action)) {
                // Open EventInfo later
                timeMillis = parseViewAction(intent);
            }

            if (timeMillis == -1) {
                timeMillis = Utils.timeFromIntentInMillis(intent);
            }
        }

        if (viewType == -1 || viewType > ViewType.MAX_VALUE) {
            viewType = Utils.getViewTypeFromIntentAndSharedPref(this);
        }
        mTimeZone = Utils.getTimeZone(this, mHomeTimeUpdater);
        Time t = new Time(mTimeZone);
        t.set(timeMillis);

        // On its side the month stays the month.
        //
        // Google Calendar turns it into a week there, and that was tried: it reads well, but the month
        // view on a phone held sideways is the arrangement that was asked for, and the week view is one
        // rotation away in the drawer. So nothing is swapped and nothing is restored.
        {
            boolean smallestWidthIsTablet =
                    getResources().getConfiguration().smallestScreenWidthDp >= 600;
            mIsTabletConfig = Utils.getConfigBool(this, R.bool.tablet_config)
                    && smallestWidthIsTablet;
            mIsMultipane = Utils.getConfigBool(this, R.bool.multiple_pane_config)
                    && smallestWidthIsTablet;
        }

        if (DEBUG) {
            if (icicle != null && intent != null) {
                Log.d(TAG, "both, icicle:" + icicle + "  intent:" + intent);
            } else {
                Log.d(TAG, "not both, icicle:" + icicle + " intent:" + intent);
            }
        }

        Resources res = getResources();
        mHideString = res.getString(R.string.hide_controls);
        mShowString = res.getString(R.string.show_controls);
        mOrientation = res.getConfiguration().orientation;
        if (mOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            mControlsAnimateWidth = (int) res.getDimension(R.dimen.calendar_controls_width);
            if (mControlsParams == null) {
                mControlsParams = new LayoutParams(mControlsAnimateWidth, 0);
            }
            mControlsParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        } else {
            // Make sure width is in between allowed min and max width values
            mControlsAnimateWidth = Math.max(res.getDisplayMetrics().widthPixels * 45 / 100,
                    (int) res.getDimension(R.dimen.min_portrait_calendar_controls_width));
            mControlsAnimateWidth = Math.min(mControlsAnimateWidth,
                    (int) res.getDimension(R.dimen.max_portrait_calendar_controls_width));
        }

        mControlsAnimateHeight = (int) res.getDimension(R.dimen.calendar_controls_height);

        mHideControls = !Utils.getSharedPreference(
                this, GeneralPreferences.KEY_SHOW_CONTROLS, true);
        // mIsTabletConfig and mIsMultipane were worked out earlier, before the layout was inflated.
        //
        // Written down because "which layout did it choose" is the first question about any of these
        // screens looking wrong, and the answer is not visible from the code.
        {
            android.content.res.Configuration config = getResources().getConfiguration();
        }
        mShowAgendaWithMonth = Utils.getConfigBool(this, R.bool.show_agenda_with_month);
        mShowCalendarControls =
                Utils.getConfigBool(this, R.bool.show_calendar_controls);
        mShowEventDetailsWithAgenda =
                Utils.getConfigBool(this, R.bool.show_event_details_with_agenda);
        mShowEventInfoFullScreenAgenda =
                Utils.getConfigBool(this, R.bool.agenda_show_event_info_full_screen);
        mShowEventInfoFullScreen =
                Utils.getConfigBool(this, R.bool.show_event_info_full_screen);
        mCalendarControlsAnimationTime = res.getInteger(R.integer.calendar_controls_animation_time);
        Utils.setAllowWeekForDetailView(mIsMultipane);

        // setContentView must be called before configureActionBar
        binding = AllInOneMaterialBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        mDrawerLayout = binding.drawerLayout;
        mNavigationView = binding.navigationView;

        // The to-do entry's icon is the title bar's today icon with today's day number drawn into
        // it, the same treatment the toolbar's own today button gets.
        android.view.MenuItem todoItem = mNavigationView.getMenu().findItem(R.id.action_todo);
        if (todoItem != null && todoItem.getIcon() instanceof android.graphics.drawable.LayerDrawable) {
            Utils.setTodayIcon((android.graphics.drawable.LayerDrawable) todoItem.getIcon(),
                    this, Utils.getTimeZone(this, null));
        }

        mFab = binding.floatingActionButton;

        if (mIsTabletConfig) {
            mDateRange = binding.include.dateBar;
            mWeekTextView = binding.include.weekNum;
        } else {
            mDateRange = DateRangeTitleBinding.inflate(getLayoutInflater()).getRoot();
        }

        setupToolbar(viewType);
        setupNavDrawer();
        setupFloatingActionButton();

        mHomeTime = binding.include.homeTime;
        mMiniMonth = binding.include.miniMonth;
        if (mIsTabletConfig && mOrientation == Configuration.ORIENTATION_PORTRAIT) {
            mMiniMonth.setLayoutParams(new RelativeLayout.LayoutParams(mControlsAnimateWidth,
                    mControlsAnimateHeight));
        }
        mCalendarsList = binding.include.calendarList;
        mMiniMonthContainer = binding.include.miniMonthContainer;
        mSecondaryPane = binding.include.secondaryPane;

        // Must register as the first activity because this activity can modify
        // the list of event handlers in it's handle method. This affects who
        // the rest of the handlers the controller dispatches to are.
        mController.registerFirstEventHandler(HANDLER_KEY, this);

        initFragments(timeMillis, viewType, icicle);

        // Listen for changes that would require this to be refreshed
        SharedPreferences prefs = GeneralPreferences.Companion.getSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        mContentResolver = getContentResolver();


        getOnBackPressedDispatcher().addCallback(this, mBackCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mCurrentView == ViewType.EDIT || mBackToPreviousView) {
                    mController.sendEvent(this, EventType.GO_TO, null, null, -1, mPreviousView);
                } else if (mCurrentView == ViewType.DAY || mCurrentView == ViewType.WEEK) {
                    // Day and week step back to the month view rather than straight out of the app.
                    mController.sendEvent(this, EventType.GO_TO, null, null, -1, ViewType.MONTH);
                } else {
                    int defaultStartView = Utils.getViewTypeFromIntentAndSharedPref(mActivity);

                    // If the current view is the default one, quit app. If not, go back to default view.
                    if (mCurrentView == defaultStartView
                            || defaultStartView == com.android.calendar.CalendarController.ViewType.AGENDA
                            || defaultStartView == com.android.calendar.CalendarController.ViewType.DAY ) {
                       finish();
                    } else {
                        mController.sendEvent(this, EventType.GO_TO, null, null, -1, defaultStartView);
                    }
                }
            }
        });
        updateBackCallbackEnabled();

    }

    /**
     * Lets the system handle back when the next press would leave the app.
     *
     * While this callback is enabled it swallows back and moves between views itself, and Android
     * then has nothing to animate. Disabling it in that one case hands back to the platform, which
     * is what plays the predictive "back to the launcher" animation on Android 13+.
     */
    private void updateBackCallbackEnabled() {
        if (mBackCallback == null) {
            return;
        }
        boolean wouldExit;
        if (mCurrentView == ViewType.EDIT || mBackToPreviousView) {
            wouldExit = false;
        } else if (mCurrentView == ViewType.DAY || mCurrentView == ViewType.WEEK) {
            // These step back to the month view first.
            wouldExit = false;
        } else {
            int defaultStartView = Utils.getViewTypeFromIntentAndSharedPref(mActivity);
            wouldExit = mCurrentView == defaultStartView
                    || defaultStartView == com.android.calendar.CalendarController.ViewType.AGENDA
                    || defaultStartView == com.android.calendar.CalendarController.ViewType.DAY;
        }
        mBackCallback.setEnabled(!wouldExit);
    }

    private void checkAppPermissions() {
        // Here, thisActivity is the current activity
        if ((ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_CALENDAR)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED)) {

            ArrayList<String> permissionsList = new ArrayList<>(Arrays.asList(
                    Manifest.permission.WRITE_CALENDAR,
                    Manifest.permission.READ_CALENDAR)
            );

            // Permission for calendar notifications
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED)) {
                permissionsList.add(Manifest.permission.POST_NOTIFICATIONS);
            }

            // No explanation needed, we can request the permission.
            String[] permissionsArray = new String[permissionsList.size()];
            ActivityCompat.requestPermissions(this,
                    permissionsList.toArray(permissionsArray),
                    PERMISSIONS_REQUEST_WRITE_CALENDAR);
        }

    }

    /**
     * Asks the system first, and only warns if the user declines.
     *
     * The system's own prompt is the one that can actually grant the exemption, so it goes first. If
     * the user says no there - or backs out of it - the app's own dialog follows, which spells out
     * what refusing costs and offers to change the answer. Warning before asking told the user off
     * for something they had not yet been asked.
     */
    private void checkAndRequestDisablingDoze() {
        boolean doNotCheckBatteryOptimization = Utils.getSharedPreference(getApplicationContext(),
                GeneralPreferences.KEY_DO_NOT_CHECK_BATTERY_OPTIMIZATION, false);
        if (dozeDisabled() || doNotCheckBatteryOptimization) {
            return;
        }
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + getApplicationContext().getPackageName()));
        try {
            startActivityForResult(intent, REQUEST_CODE_BATTERY);
        } catch (ActivityNotFoundException e) {
            // No system screen for it: go straight to the app's own warning, which can still send
            // the user to the settings list by hand.
            showBatteryWarning();
        }
    }

    /** The app's own warning, shown when the system prompt did not settle the question. */
    private void showBatteryWarning() {
        startActivity(new Intent(this,
                com.android.calendar.chrome.compose.ComposeBatteryDialogActivity.class));
    }

    private Boolean dozeDisabled() {
        String packageName = getApplicationContext().getPackageName();
        PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(packageName);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_WRITE_CALENDAR: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay!

                    // Check and ask to disable battery optimizations
                    checkAndRequestDisablingDoze();

                } else {
                    Toast.makeText(getApplicationContext(), R.string.user_rejected_calendar_write_permission, Toast.LENGTH_LONG).show();
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }

        // Clean up cached ics and vcs files - in case onDestroy() didn't run the last time
        cleanupCachedEventFiles();
    }

    private void setupToolbar(int viewType) {
        mToolbar = binding.toolbar;

        if (!mIsTabletConfig) {
            mCalendarToolbarHandler = new CalendarToolbarHandler(this, mToolbar, viewType);
        } else {
            int titleResource = switch (viewType) {
                case ViewType.AGENDA -> R.string.agenda_view;
                case ViewType.DAY -> R.string.day_view;
                case ViewType.MONTH -> R.string.month_view;
                default -> R.string.week_view;
            };
            mToolbar.setTitle(titleResource);
        }
        setSupportActionBar(mToolbar);

        // The heading the app draws is the only heading.
        //
        // The ActionBar keeps a title of its own whatever the toolbar carries, and it is the app's
        // name until something sets it. It was switched on for a while, during an attempt to use the
        // ActionBar's own title and subtitle instead of a view, and then the heading view was put
        // back and this was left behind: both were drawn, the app's name took the space, and the
        // heading came out as "Eta...". Emptied and hidden again.
        mToolbar.setTitle("");
        mToolbar.setSubtitle("");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        mToolbar.setNavigationOnClickListener(v -> AllInOneActivity.this.openDrawer());
        mActionBar = getSupportActionBar();
        if (mActionBar == null) return;
        mActionBar.setDisplayHomeAsUpEnabled(true);
        mActionBar.setHomeButtonEnabled(true);

        // The hamburger, the three dots and the menu they open, all in the theme's own colour.
        //
        // None of the three follows the theme on its own: the home-as-up arrow and the overflow
        // dots are drawables with a fixed colour of their own, and the popup menu's entries took
        // their colour from a theme overlay that left them white on a light popup. Each is given a
        // colour here - black on a light bar, white on a dark one - so the light appearance reads
        // as it should.
        int barIconColor = resolveBarIconColor();
        android.graphics.drawable.Drawable navigationIcon = mToolbar.getNavigationIcon();
        if (navigationIcon != null) {
            navigationIcon.mutate();
            navigationIcon.setTintList(android.content.res.ColorStateList.valueOf(barIconColor));
        }
        mToolbar.setOverflowIcon(androidx.appcompat.content.res.AppCompatResources
                .getDrawable(this, R.drawable.ic_overflow_menu));
        mToolbar.setPopupTheme(R.style.ThemeOverlay_Etar_PopupMenu);

        // The date in the heading opens the date picker, the same control the Compose screens
        // carry; the down caret is what marks it as something that opens.
        mToolbar.post(this::installTitlePicker);
    }

    /**
     * Adds the down caret to the toolbar heading and makes the heading open a picker.
     *
     * CalendarToolbarHandler rebuilds the title text on every view change, and the caret is a
     * compound drawable on the same TextView, so it is re-applied each time the title changes
     * rather than being installed once.
     */
    private void installTitlePicker() {
        TextView title = findToolbarTitle();
        if (title == null) {
            return;
        }
        mToolbarTitle = title;
        title.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0, 0, R.drawable.ic_caret_down, 0);
        title.setCompoundDrawablePadding((int) (4 * getResources().getDisplayMetrics().density));
        title.setClickable(true);
        // The same ripple the year and three day views' headings carry, so a tap on any of them
        // looks the same. Without it the heading was clickable and opened the picker, but nothing
        // on screen acknowledged the press, so it read as a plain label.
        android.util.TypedValue ripple = new android.util.TypedValue();
        if (getTheme().resolveAttribute(
                androidx.appcompat.R.attr.selectableItemBackground, ripple, true)) {
            title.setBackgroundResource(ripple.resourceId);
        }
        // Padding so the ripple is not clipped to the glyphs themselves.
        int pad = (int) (6 * getResources().getDisplayMetrics().density);
        title.setPadding(pad, title.getPaddingTop(), pad, title.getPaddingBottom());
        title.setOnClickListener(v -> showTitlePicker());
    }

    /**
     * The heading TextView: the toolbar's own heading view when the layout has one, and otherwise
     * the ActionBar's title found by its text.
     *
     * The layout gained a heading view so the second line could sit under the first without the two
     * sharing a row. A toolbar carrying children of its own shows no ActionBar title, so looking for
     * the title by its text finds nothing there - which is how the heading lost its caret, and with
     * it the way to jump to another day or month.
     */
    private TextView findToolbarTitle() {
        TextView own = mToolbar.findViewById(R.id.m3e_title_text);
        if (own != null) {
            return own;
        }
        CharSequence wanted = mToolbar.getTitle();
        if (wanted == null) {
            return null;
        }
        return findTextViewWithText(mToolbar, wanted.toString());
    }

    private TextView findTextViewWithText(ViewGroup parent, String text) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView && text.contentEquals(((TextView) child).getText())) {
                return (TextView) child;
            }
            if (child instanceof ViewGroup) {
                TextView found = findTextViewWithText((ViewGroup) child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Which picker the heading opens depends on the view: the month view picks a month, the day
     * and week views pick a day.
     */
    private void showTitlePicker() {
        if (mCurrentView == ViewType.MONTH) {
            showMonthPicker();
        } else {
            goToDate();
        }
    }

    /** Month picker for the month view: the app's own chip row, in a drop down under the heading. */
    private void showMonthPicker() {
        // Opens on the month the view is showing rather than always on today's month, so a view
        // paged to another month gets a picker that starts where the view is.
        Integer pickerYear = null;
        Integer pickerMonth = null;
        if (mMonthViewDisplayedMillis > 0) {
            java.time.LocalDate displayed = java.time.Instant.ofEpochMilli(mMonthViewDisplayedMillis)
                    .atZone(getZoneId()).toLocalDate();
            pickerYear = displayed.getYear();
            pickerMonth = displayed.getMonthValue();
        }
        mTitlePopup = com.android.calendar.chrome.compose.M3eMonthPickerDialog.INSTANCE.show(
                this, mToolbar, pickerYear, pickerMonth, (year, month) -> {
                    Time selected = new Time(mTimeZone);
                    selected.set(System.currentTimeMillis());
                    selected.setYear(year);
                    selected.setMonth(month - 1);
                    selected.setDay(1);
                    // Marked explicit, like the year view's month choice and the date picker's.
                    //
                    // This is the one the heading opens on the month view, and it was the last of the
                    // three ways into a month that still went without the mark. The month view holds
                    // still when it is sent to a month other than the one on screen, so that a tapped
                    // grid tail does not move the page - so this choice was treated as a tap: the
                    // drop down closed, the month did not change, and the heading did not change with
                    // it, which made the whole control look as though it did nothing. The other two
                    // were fixed first; this one is reached by tapping the heading, which is why it
                    // was still broken after they worked.
                    long extras = CalendarController.EXTRA_GOTO_TIME
                            | CalendarController.EXTRA_GOTO_DATE
                            | CalendarController.EXTRA_GOTO_EXPLICIT;
                    mController.sendEvent(this, EventType.GO_TO, selected, null, selected, -1,
                            ViewType.MONTH, extras, null, null);
                });
    }

    public void openDrawer() {
        mDrawerLayout.openDrawer(GravityCompat.START);
    }

    public void setupNavDrawer() {
        mNavigationView.setNavigationItemSelectedListener(this);
        showActionBar();
    }

    public void setupFloatingActionButton() {
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Time t = buildCreateEventTime();
                mController.sendEventRelatedEvent(
                        this, EventType.CREATE_EVENT, -1, t.toMillis(), 0, 0, 0, -1);
            }
        });
    }

    /**
     * Where the add button puts a new event.
     *
     * In the month view the answer follows the month being looked at rather than the clock: adding
     * from a month you have browsed to used to land on today, which in another month is an arbitrary
     * day somewhere in the grid. A month other than this one gets its first day; the current month
     * gets today, an hour or two ahead on the half hour, which is what "add now" should mean.
     *
     * The day and week views follow the same rule: today while it is part of the view - the day
     * view's one day, or the week the week view is on - and the first day of the view otherwise,
     * because "add" from a day or week you paged to used to land on today, which is not a day of
     * that view at all.
     */
    private Time buildCreateEventTime() {
        Time now = new Time(mTimeZone);
        now.set(System.currentTimeMillis());
        now.setSecond(0);

        if (mController != null && mController.getViewType() == ViewType.MONTH) {
            // The month on screen, which the controller tracks as the visible time.
            Time visible = new Time(mTimeZone);
            visible.set(mController.getTime());
            boolean sameMonth = visible.getYear() == now.getYear()
                    && visible.getMonth() == now.getMonth();
            if (!sameMonth) {
                Time first = new Time(mTimeZone);
                first.set(visible);
                first.setDay(1);
                // A civil hour rather than midnight, so the new event is not an all-day looking one.
                first.setHour(9);
                first.setMinute(0);
                first.setSecond(0);
                first.normalize();
                return first;
            }
        }

        if (mController != null
                && (mController.getViewType() == ViewType.DAY
                    || mController.getViewType() == ViewType.WEEK)
                && mDayWeekViewDisplayedMillis > 0) {
            // The first day of the view: the day the day view is on, or the first day of the week
            // the week view is on. When today is part of the view the event starts today, at the
            // rounded-up time below - the common case wants an event happening now. When the view
            // is paged somewhere else, the event starts on the first day of the view instead of on
            // a today that is not part of it at all.
            Time first = new Time(mTimeZone);
            first.set(mDayWeekViewDisplayedMillis);
            long viewStartJulian = Time.getJulianDay(first.toMillis(), first.getGmtOffset());
            long todayJulian = Time.getJulianDay(now.toMillis(), now.getGmtOffset());
            long viewDays = mController.getViewType() == ViewType.WEEK ? 6 : 0;
            boolean containsToday = todayJulian >= viewStartJulian
                    && todayJulian <= viewStartJulian + viewDays;
            if (!containsToday) {
                first.setHour(9);
                first.setMinute(0);
                first.setSecond(0);
                first.normalize();
                return first;
            }
        }

        // Today, at the next half hour - pushed on by one more half hour when that is under half
        // an hour away - so the event starts at least 30 and at most 60 minutes from now, on a
        // whole or a half hour. The old rule rounded up and then added a whole hour, which could
        // leave the event well over an hour away.
        Time t = new Time(mTimeZone);
        t.set(Utils.getNextCreateEventStart(now.toMillis()));
        return t;
    }

    private void showActionBar() {
        if (mActionBar == null) return;
        mActionBar.show();
    }

    private long parseViewAction(final Intent intent) {
        long timeMillis = -1;
        Uri data = intent.getData();
        if (data != null && data.isHierarchical()) {
            List<String> path = data.getPathSegments();
            if (path.size() == 2 && path.get(0).equals("events")) {
                try {
                    mViewEventId = Long.parseLong(data.getLastPathSegment());
                    if (mViewEventId != -1) {
                        mIntentEventStartMillis = intent.getLongExtra(EXTRA_EVENT_BEGIN_TIME, 0);
                        mIntentEventEndMillis = intent.getLongExtra(EXTRA_EVENT_END_TIME, 0);
                        mIntentAttendeeResponse = intent.getIntExtra(
                            ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_NONE);
                        mIntentAllDay = intent.getBooleanExtra(EXTRA_EVENT_ALL_DAY, false);
                        timeMillis = mIntentEventStartMillis;
                    }
                } catch (NumberFormatException e) {
                    // Ignore if mViewEventId can't be parsed
                }
            }
        }
        return timeMillis;
    }

    // Clear buttons used in the agenda view
    private void clearOptionsMenu() {
        if (mOptionsMenu == null) {
            return;
        }
        MenuItem cancelItem = mOptionsMenu.findItem(R.id.action_cancel);
        if (cancelItem != null) {
            cancelItem.setVisible(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // The slide for whatever is opened from here, and the reverse for when it comes back.
        // Declared on every resume because the transition used by the next launch is taken from
        // whatever this activity last set, and because overrideActivityTransition is only accepted
        // while the activity is resumed - a declaration made earlier, or from onPause while being
        // covered, is discarded and the platform default plays instead. After super.onResume, so the
        // activity really is in that state when it is asked.
        if (getIntent() != null && getIntent().getBooleanExtra(
                com.android.calendar.chrome.M3eHostActivityKt.EXTRA_FORWARD_FROM_SEQUENCE, false)) {
            // Entered from the day / three day / week sequence, so everything keeps moving the
            // same way rather than reversing halfway through it.
            com.android.calendar.chrome.M3eViewTransitionsKt.declareForwardSlide(this);
        } else {
            com.android.calendar.chrome.M3eViewTransitionsKt.declareForwardSlide(this);
            com.android.calendar.chrome.M3eViewTransitionsKt.declareReturnSlide(this);
        }

        // The settings screen can change the theme while this activity is stopped behind it, and
        // nothing would recreate it: switching to pure black in particular does not change the
        // night mode, so the platform sends no configuration change at all.
        String themeMode = Utils.getSharedPreference(this,
                GeneralPreferences.KEY_THEME_PREF, "system");
        if (!themeMode.equals(mAppliedThemeMode)) {
            recreate();
            return;
        }

        // Check if the upgrade code has ever been run. If not, force a sync just this one time.
        Utils.trySyncAndDisableUpgradeReceiver(this);

        // Must register as the first activity because this activity can modify
        // the list of event handlers in it's handle method. This affects who
        // the rest of the handlers the controller dispatches to are.
        mController.registerFirstEventHandler(HANDLER_KEY, this);
        mOnSaveInstanceStateCalled = false;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!Utils.canScheduleAlarms(this)) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            }
        }

        if (!Utils.isCalendarPermissionGranted(this, true)) {
            //If permission is not granted then just return.
            Log.d(TAG, "Manifest.permission.READ_CALENDAR is not granted");
            return;
        }

        // The observers live for the whole activity, not just while it is resumed.
        //
        // They used to be registered in onResume and unregistered in onPause, which loses exactly the
        // notification that matters here: deleting an event happens from another screen, so this
        // activity is paused when the provider notifies, and the observer is taken off just as it
        // arrives. The grid then kept the deleted event until the app was restarted.
        //
        // Unregistered first because onResume can run more than once per instance; ContentResolver
        // simply ignores a removal of something it does not have.
        mContentResolver.unregisterContentObserver(mObserver);
        mContentResolver.unregisterContentObserver(mInstancesObserver);
        mContentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI,
                true, mObserver);
        // The grids read Instances, which is a separate URI as far as the provider's notifications
        // are concerned, so it needs its own observer.
        mContentResolver.registerContentObserver(CalendarContract.Instances.CONTENT_URI,
                true, mInstancesObserver);
        if (mUpdateOnResume) {
            initFragments(mController.getTime(), mController.getViewType(), null);
            mUpdateOnResume = false;
        }
        Time t = new Time(mTimeZone);
        t.set(mController.getTime());
        mController.sendEvent(this, EventType.UPDATE_TITLE, t, t, -1, ViewType.CURRENT,
                mController.getDateFlags(), null, null);

        // Returning from the year view (or any other screen) must restore the drawer state,
        // because the year entry shares the exclusive group with the real views.
        syncNavigationCheckedItem();

        if (mControlsMenu != null) {
            mControlsMenu.setTitle(mHideControls ? mShowString : mHideString);
        }
        mPaused = false;

        if (mViewEventId != -1 && mIntentEventStartMillis != -1 && mIntentEventEndMillis != -1) {
            long currentMillis = System.currentTimeMillis();
            long selectedTime = -1;
            if (currentMillis > mIntentEventStartMillis && currentMillis < mIntentEventEndMillis) {
                selectedTime = currentMillis;
            }
            mController.sendEventRelatedEventWithExtra(this, EventType.VIEW_EVENT, mViewEventId,
                    mIntentEventStartMillis, mIntentEventEndMillis, -1, -1,
                    EventInfo.buildViewExtraLong(mIntentAttendeeResponse, mIntentAllDay),
                    selectedTime);
            mViewEventId = -1;
            mIntentEventStartMillis = -1;
            mIntentEventEndMillis = -1;
            mIntentAllDay = false;
        }
        Utils.setMidnightUpdater(mHandler, mTimeChangesUpdater, mTimeZone);
        // Make sure the today icon is up to date
        invalidateOptionsMenu();

        // Re-read the current view on the way back in.
        //
        // Creating or editing an event happens on another screen. CalendarProvider notifies
        // asynchronously, and a notification delivered while this activity was paused reaches an
        // observer that is about to be unregistered again - so the grid could still hold its
        // pre-edit contents and only catch up when the app was restarted. Asking for the current
        // time again is the cheapest way to make the view re-query, and it is a no-op when nothing
        // changed.
        if (mController != null && viewInitialised) {
            Time now = new Time(mTimeZone);
            now.set(mController.getTime());
            // Marked explicit when the year view was what brought us back.
            //
            // The month view holds still when it is sent to a month other than the one on screen, so
            // that a tapped grid tail does not move the page - and this refresh is one of those
            // sends. Without the mark it undid the jump the year view had just asked for, which is
            // why the month came back to where it had been. The mark is consumed here: it applies to
            // this return, not to every later resume.
            long extras = CalendarController.EXTRA_GOTO_DATE;
            if (mExplicitMonthJump) {
                extras |= CalendarController.EXTRA_GOTO_EXPLICIT;
                mExplicitMonthJump = false;
            }
            mController.sendEvent(this, EventType.GO_TO, now, now, -1, ViewType.CURRENT,
                    extras, null, null);
        }
        viewInitialised = true;

        // Coming back from another screen rebuilds the pane, whichever view it shows.
        //
        // Every screen opened from here - the year, the countdown, the date calculator, the agenda,
        // the three day view, the settings - is a separate activity, so this one is stopped and started
        // again, and coming back left the screen unresponsive: frozen at "waited 5000ms for
        // FocusEvent", the main thread always inside DayView drawing its hour labels, the view never
        // settling.
        //
        // Rebuilding the pane is what a view switch does, and a view switch is known to put it right,
        // so the return does the same thing once by forcing the switch to the view already shown. The
        // month view was left out of this at first, on the grounds that it has no DayView of its own
        // and had not been seen to fail that way. That was wrong twice over: returning to the month did
        // freeze, and the stack for it was inside DayView all the same, because the grid of the view
        // that had been left was still alive behind it and still being drawn.
        //
        // The view type is taken from the controller rather than from mCurrentView. The controller is
        // what resolves ViewType.CURRENT for the refresh event sent just above, while mCurrentView only
        // moves when setMainPane runs - so after a stop and a start it can name the view that was left
        // rather than the one on screen.
        if (mReturningFromAnotherScreen && mController != null) {
            mReturningFromAnotherScreen = false;
            int viewToRebuild = mController.getViewType();
            if (viewToRebuild < 0) {
                viewToRebuild = mCurrentView;
            }
            setMainPane(null, R.id.main_pane, viewToRebuild, mController.getTime(), true);
        }
        mReturningFromAnotherScreen = false;

        mCalIntentReceiver = Utils.setTimeChangesReceiver(this, mTimeChangesUpdater);
    }

    /** Set in onPause, so onResume can tell a return from another screen from the first start. */
    private boolean mReturningFromAnotherScreen = false;

    /**
     * Set when the year view asked for a particular month, so the refresh on resume does not undo it.
     *
     * See the GO_TO the year view sends and the one onResume sends: the second is a re-read of where
     * the controller already is, and it would otherwise be treated as a tap and hold the month view
     * still. Consumed by the first resume after it is set.
     */
    private boolean mExplicitMonthJump = false;

    /** True once the first onResume has run, so the forced re-read does not double the first load. */
    private boolean viewInitialised = false;


    @Override
    protected void onPause() {
        // No forward transition is declared here. overrideActivityTransition is only accepted while
        // the activity is resumed or finishing, and by onPause the calendar can already be past
        // that - the call was quietly ignored and the platform's default played instead. The screen
        // being opened declares its own slide from its onCreate, where the call always applies.
        super.onPause();

        // Read by onResume. Set here rather than checked against isFinishing so that a return from
        // any other screen - including a configuration change - rebuilds the grid. See onResume.
        mReturningFromAnotherScreen = true;

        mController.deregisterEventHandler(HANDLER_KEY);
        mPaused = true;
        mHomeTime.removeCallbacks(mHomeTimeUpdater);

        if (!Utils.isCalendarPermissionGranted(this, false)) {
            //If permission is not granted then just return.
            Log.d(TAG, "Manifest.permission.WRITE_CALENDAR is not granted");
            return;
        }

        if (isFinishing()) {
            // Stop listening for changes that would require this to be refreshed
            SharedPreferences prefs = GeneralPreferences.Companion.getSharedPreferences(this);
            prefs.unregisterOnSharedPreferenceChangeListener(this);
        }
        // FRAG_TODO save highlighted days of the week;
        //
        // The view on screen is deliberately not stored as the start view. Doing that made the
        // setting meaningless - it became "whatever was last looked at" - so a fresh install opened
        // on the day grid and kept opening there, and choosing a start view in the settings was
        // undone by leaving the app. Only the settings screen writes it now.
        Utils.resetMidnightUpdater(mHandler, mTimeChangesUpdater);

        // Cleared once it has been unregistered, and only unregistered when there is something to
        // unregister.
        //
        // onResume can return before it installs this receiver - it does exactly that when the theme
        // changed, calling recreate() and leaving - and onPause then ran regardless. Unregistering
        // something that was never registered throws IllegalArgumentException, which is what made
        // switching theme crash. The field is nulled so a second pause cannot unregister it again.
        if (mCalIntentReceiver != null) {
            Utils.clearTimeChangesReceiver(this, mCalIntentReceiver);
            mCalIntentReceiver = null;
        }
    }

    @Override
    protected void onUserLeaveHint() {
        mController.sendEvent(this, EventType.USER_HOME, null, null, -1, ViewType.CURRENT);
        super.onUserLeaveHint();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        mOnSaveInstanceStateCalled = true;
        super.onSaveInstanceState(outState);
        outState.putLong(BUNDLE_KEY_RESTORE_TIME, mController.getTime());
        outState.putInt(BUNDLE_KEY_RESTORE_VIEW, mCurrentView);
        if (mCurrentView == ViewType.EDIT) {
            outState.putLong(BUNDLE_KEY_EVENT_ID, mController.getEventId());
        } else if (mCurrentView == ViewType.AGENDA) {
            FragmentManager fm = getSupportFragmentManager();
            Fragment f = fm.findFragmentById(R.id.main_pane);
            if (f instanceof AgendaFragment) {
                outState.putLong(BUNDLE_KEY_EVENT_ID, ((AgendaFragment) f).getLastShowEventId());
            }
        }

    }

    @Override
    protected void onDestroy() {
        // The observers are registered for the whole activity, so they are released here rather than
        // in onPause - see onResume for why.
        if (Utils.isCalendarPermissionGranted(this, false)) {
            mContentResolver.unregisterContentObserver(mObserver);
            mContentResolver.unregisterContentObserver(mInstancesObserver);
        }

        super.onDestroy();

        SharedPreferences prefs = GeneralPreferences.Companion.getSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(this);

        mController.deregisterAllEventHandlers();

        CalendarController.removeInstance(this);

        // Clean up cached ics and vcs files
        cleanupCachedEventFiles();
    }

    /**
     * Cleans up the temporarily generated ics and vcs files in the cache directory
     * The files are of the format *.ics and *.vcs
     */
    private void cleanupCachedEventFiles() {
        File cacheDir = getExternalCacheDir();
        if (cacheDir == null) return;
        File[] files = cacheDir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String filename = file.getName();
            if (filename.endsWith(".ics") || filename.endsWith(".vcs")) {
                file.delete();
            }
        }
    }


    private void initFragments(long timeMillis, int viewType, Bundle icicle) {
        if (DEBUG) {
            Log.d(TAG, "Initializing to " + timeMillis + " for view " + viewType);
        }
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

        if (mShowCalendarControls) {
            Fragment miniMonthFrag = new MonthByWeekFragment(timeMillis, true);
            ft.replace(R.id.mini_month, miniMonthFrag);
            mController.registerEventHandler(R.id.mini_month, (EventHandler) miniMonthFrag);

            Fragment selectCalendarsFrag = new SelectVisibleCalendarsFragment();
            ft.replace(R.id.calendar_list, selectCalendarsFrag);
            mController.registerEventHandler(
                    R.id.calendar_list, (EventHandler) selectCalendarsFrag);
        }
        if (!mShowCalendarControls || viewType == ViewType.EDIT) {
            mMiniMonth.setVisibility(View.GONE);
            mCalendarsList.setVisibility(View.GONE);
        }

        EventInfo info = null;
        if (viewType == ViewType.EDIT) {
            mPreviousView = GeneralPreferences.Companion.getSharedPreferences(this).getInt(
                    GeneralPreferences.KEY_START_VIEW, GeneralPreferences.DEFAULT_START_VIEW);

            long eventId = -1;
            Intent intent = getIntent();
            Uri data = intent.getData();
            if (data != null) {
                try {
                    eventId = Long.parseLong(data.getLastPathSegment());
                } catch (NumberFormatException e) {
                    if (DEBUG) {
                        Log.d(TAG, "Create new event");
                    }
                }
            } else if (icicle != null && icicle.containsKey(BUNDLE_KEY_EVENT_ID)) {
                eventId = icicle.getLong(BUNDLE_KEY_EVENT_ID);
            }

            long begin = intent.getLongExtra(EXTRA_EVENT_BEGIN_TIME, -1);
            long end = intent.getLongExtra(EXTRA_EVENT_END_TIME, -1);
            info = new EventInfo();
            if (end != -1) {
                info.endTime = new Time();
                info.endTime.set(end);
            }
            if (begin != -1) {
                info.startTime = new Time();
                info.startTime.set(begin);
            }
            info.id = eventId;
            // We set the viewtype so if the user presses back when they are
            // done editing the controller knows we were in the Edit Event
            // screen. Likewise for eventId
            mController.setViewType(viewType);
            mController.setEventId(eventId);
        } else {
            mPreviousView = viewType;
        }

        setMainPane(ft, R.id.main_pane, viewType, timeMillis, true);
        ft.commit(); // this needs to be after setMainPane()

        Time t = new Time(mTimeZone);
        t.set(timeMillis);
        if (viewType == ViewType.AGENDA && icicle != null) {
            mController.sendEvent(this, EventType.GO_TO, t, null,
                    icicle.getLong(BUNDLE_KEY_EVENT_ID, -1), viewType);
        } else if (viewType != ViewType.EDIT) {
            mController.sendEvent(this, EventType.GO_TO, t, null, -1, viewType);
        }
    }

    protected void updateViewSettingsVisiblility() {
        if (mViewSettings != null) {
            // Shown in every view.
            //
            // This was limited to the month view, so the overflow had no way into the view settings
            // from the day grid, the week grid or the agenda - the screens where those settings
            // actually change what is drawn. The entry opens the same screen from anywhere.
            mViewSettings.setVisible(true);
            mViewSettings.setEnabled(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        mOptionsMenu = menu;
        getMenuInflater().inflate(R.menu.all_in_one_title_bar, menu);
        updateViewSwitchButtons();

        // Add additional options (if any).
        Integer extensionMenuRes = mExtensions.getExtensionMenuResource(menu);
        if (extensionMenuRes != null) {
            getMenuInflater().inflate(extensionMenuRes, menu);
        }

        MenuItem item = menu.findItem(R.id.action_import);
        if (item != null) {
            item.setVisible(true);
        }

        mSearchMenu = menu.findItem(R.id.action_search);
        if (mSearchMenu != null) {
            // Placed in the bar rather than the overflow, and given its icon here.
            //
            // The menu resource asks for the same thing, but an item with a collapsed action
            // view is easy for the framework to demote into the overflow, where an icon is not
            // drawn at all. Setting it in code is what the title bar's other actions do not need
            // and this one does.
            mSearchMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS
                    | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
            mSearchMenu.setIcon(R.drawable.ic_menu_search);
        }
        mSearchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        if (mSearchView != null) {
            Utils.setUpSearchView(mSearchView, this);
            mSearchView.setOnQueryTextListener(this);
            mSearchView.setOnSuggestionListener(this);
        }

        // Hide the "show/hide controls" button if this is a phone
        // or the view type is "Month" or "Agenda".

        mControlsMenu = menu.findItem(R.id.action_hide_controls);
        if (!mShowCalendarControls) {
            if (mControlsMenu != null) {
                mControlsMenu.setVisible(false);
                mControlsMenu.setEnabled(false);
            }
        } else if (mControlsMenu != null && mController != null
                && (mController.getViewType() == ViewType.MONTH ||
                mController.getViewType() == ViewType.AGENDA)) {
            mControlsMenu.setVisible(false);
            mControlsMenu.setEnabled(false);
        } else if (mControlsMenu != null) {
            mControlsMenu.setTitle(mHideControls ? mShowString : mHideString);
        }

        mViewSettings = menu.findItem(R.id.action_view_settings);
        updateViewSettingsVisiblility();


        MenuItem menuItem = menu.findItem(R.id.action_today);

        // replace the default top layer drawable of the today icon with a
        // custom drawable that shows the day of the month of today
        LayerDrawable icon = (LayerDrawable) menuItem.getIcon();
        Utils.setTodayIcon(icon, this, mTimeZone);

        // Everything in the bar drawn in the theme's own colour.
        //
        // The action icons are not tinted by the framework, so each one shows whatever colour its
        // drawable was built with - and a drawable built white is invisible on a light bar. Tinting
        // them here makes black-on-light and white-on-dark the rule for every icon in the bar,
        // including any added by an extension.
        tintTitleBarIcons(menu);

        return true;
    }

    /**
     * The colour a title bar icon is drawn in: black on a light bar, white on a dark one.
     *
     * The theme's control colour, which is exactly that pair, read here rather than relied on to be
     * applied by the framework - it is not applied to action icons or to the home-as-up arrow.
     */
    private int resolveBarIconColor() {
        android.content.res.TypedArray ta = obtainStyledAttributes(
                new int[]{android.R.attr.colorControlNormal});
        int color = ta.getColor(0, android.graphics.Color.BLACK);
        ta.recycle();
        return color;
    }

    /**
     * Gives every title bar icon the theme's control colour.
     *
     * Done in code rather than per drawable: the bar also carries icons from extensions that this
     * app does not own, and one that arrives white would be as invisible as the search icon was.
     */
    private void tintTitleBarIcons(Menu menu) {
        int color = resolveBarIconColor();
        for (int i = 0; i < menu.size(); i++) {
            android.graphics.drawable.Drawable drawable = menu.getItem(i).getIcon();
            if (drawable != null) {
                // mutate() first: the drawable is shared with the resources cache, and tinting a
                // shared instance would recolour every other user of it.
                drawable.mutate();
                drawable.setTintList(android.content.res.ColorStateList.valueOf(color));
            }
        }
    }

    private static final int REQUEST_CODE_IMPORT = 101;
    private static final int REQUEST_CODE_BATTERY = 102;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Time t = null;
        int viewType = ViewType.CURRENT;
        long extras = CalendarController.EXTRA_GOTO_TIME;
        final int itemId = item.getItemId();
        if (itemId == R.id.action_refresh) {
            mController.refreshCalendars();
            return true;
        } else if (itemId == R.id.action_today) {
            t = new Time(mTimeZone);
            t.set(System.currentTimeMillis());
            extras |= CalendarController.EXTRA_GOTO_TODAY;
            mController.sendEvent(this, EventType.GO_TO, t, null, t, -1, viewType, extras, null, null);
            return true;
        } else if (itemId == R.id.action_view_day) {
            mController.sendEvent(this, EventType.GO_TO, null, null, -1, ViewType.DAY);
            return true;
        } else if (itemId == R.id.action_view_week) {
            mController.sendEvent(this, EventType.GO_TO, null, null, -1, ViewType.WEEK);
            return true;
        } else if (itemId == R.id.action_view_month) {
            mController.sendEvent(this, EventType.GO_TO, null, null, -1, ViewType.MONTH);
            return true;
        } else if (itemId == R.id.action_goto) {
            goToDate();
        } else if (itemId == R.id.action_hide_controls) {
            mHideControls = !mHideControls;
            Utils.setSharedPreference(
                    this, GeneralPreferences.KEY_SHOW_CONTROLS, !mHideControls);
            item.setTitle(mHideControls ? mShowString : mHideString);
            if (!mHideControls) {
                mMiniMonth.setVisibility(View.VISIBLE);
                mCalendarsList.setVisibility(View.VISIBLE);
                mMiniMonthContainer.setVisibility(View.VISIBLE);
            }
            final ObjectAnimator slideAnimation = ObjectAnimator.ofInt(this, "controlsOffset",
                    mHideControls ? 0 : mControlsAnimateWidth,
                    mHideControls ? mControlsAnimateWidth : 0);
            slideAnimation.setDuration(mCalendarControlsAnimationTime);
            ObjectAnimator.setFrameDelay(0);
            slideAnimation.start();
            return true;
        } else if (itemId == R.id.action_search) {
            return false;
        } else if (itemId == R.id.action_import) {
            // The same dialog the settings screen and the other screens' overflow menus offer: a file
            // on the device, or a url to download.
            //
            // This used to be a bare document picker whose result was handed straight to
            // ImportActivity, which is why the month view's import looked different from everywhere
            // else's.
            startActivity(new Intent(this,
                    com.android.calendar.chrome.compose.ComposeImportActivity.class));
        } else if (itemId == R.id.action_m3e_calendars) {
            // Material 3 Expressive calendars manager.
            startActivity(new Intent(this,
                    com.android.calendar.selectcalendars.compose.ComposeCalendarsActivity.class));
        } else if (itemId == R.id.action_m3e_agenda) {
            // Material 3 Expressive agenda screen, opened around the day this view is on.
            startActivity(new Intent(this,
                    com.android.calendar.agenda.compose.ComposeAgendaActivity.class)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, currentViewDateMillis()));
        } else if (itemId == R.id.action_view_settings) {
            // "View details" now opens the Material 3 Expressive settings screen, which hosts
            // the month-view layout options that used to live on the old preference page.
            startActivity(new Intent(this,
                    com.android.calendar.settings.compose.ComposeSettingsActivity.class));
        } else if (itemId == R.id.action_todo) {
            // The to-do list, opened on the day this view is on so the entry lands where the user is.
            openTodo(currentViewDateMillis());
        } else {
                return mExtensions.handleItemSelected(item, this);
        }

        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_BATTERY) {
            // Whatever the system screen reported, the exemption either is in place or is not;
            // asking the PowerManager is the only answer that can be trusted, because backing
            // out of the screen also arrives here as a result.
            if (!dozeDisabled()) {
                showBatteryWarning();
            }
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (requestCode == REQUEST_CODE_IMPORT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                Intent importIntent = new Intent(this, ImportActivity.class);
                importIntent.setData(uri);
                startActivity(importIntent);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.day_menu_item) {
            if (mCurrentView != ViewType.DAY) {
                switchToView(ViewType.DAY);
            }
        } else if (itemId == R.id.week_menu_item) {
            if (mCurrentView != ViewType.WEEK) {
                switchToView(ViewType.WEEK);
            }
        } else if (itemId == R.id.threeday_menu_item) {
            // Like the year and agenda entries, the three day view is its own screen, so this
            // activity keeps the view it was showing underneath and the check mark goes back to it.
            //
            // The date this activity is on travels in the intent. A new screen otherwise starts on
            // today, so a view paged away from today opened the three day view back on today's
            // month. currentViewDateMillis() works that date out; see its comment.
            //
            // SINGLE_TOP and REORDER_TO_FRONT, so choosing the screen while it is already the one on
            // top brings the existing instance forward instead of starting a second one. Without them
            // every tap on the entry - including a tap made from inside the three day view itself,
            // where the drawer reports the entry as already selected and so closes without doing
            // anything - started another copy: the animation was that copy arriving, and it came up on
            // today because a new screen always does. The instance that was already there, with the
            // three days the user had paged to, was left underneath it.
            startActivity(new Intent(this,
                    com.android.calendar.threeday.compose.ComposeThreeDayActivity.class)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, currentViewDateMillis())
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            syncNavigationCheckedItem();
        } else if (itemId == R.id.month_menu_item) {
            if (mCurrentView != ViewType.MONTH) {
                mController.sendEvent(this, EventType.GO_TO, null, null, -1, ViewType.MONTH);
            }
        } else if (itemId == R.id.agenda_menu_item) {
            // The agenda is the Material 3 Expressive screen now. It is its own activity, so this
            // activity keeps whatever view it was showing underneath, like the year view does. It
            // opens around the day this view is on, not today.
            startActivity(new Intent(this,
                    com.android.calendar.agenda.compose.ComposeAgendaActivity.class)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, currentViewDateMillis()));
            syncNavigationCheckedItem();
        } else if (itemId == R.id.year_menu_item) {
            // The year view is its own Compose screen, so this activity stays on its current
            // view; restore the check mark the drawer just moved.
            Intent yearIntent = new Intent(this,
                    com.android.calendar.year.compose.ComposeYearActivity.class);
            // Open the year the month view is showing, not the one today happens to be in.
            //
            // mMonthViewDisplayedMillis is the first day of the month the title last reported,
            // and it is only read while the month view is the one on screen, so the value
            // belongs to this activity's month rather than to a view left behind.
            if (mCurrentView == ViewType.MONTH && mMonthViewDisplayedMillis > 0) {
                int displayedYear = Instant.ofEpochMilli(mMonthViewDisplayedMillis)
                        .atZone(getZoneId())
                        .toLocalDate()
                        .getYear();
                yearIntent.putExtra(EXTRA_INITIAL_YEAR, displayedYear);
            }
            startActivity(yearIntent);
            syncNavigationCheckedItem();
        } else if (itemId == R.id.action_date_calculator) {
            startActivity(new Intent(this,
                    com.android.calendar.datecalc.compose.ComposeDateCalculatorActivity.class));
        } else if (itemId == R.id.action_countdown) {
            // A tool screen of its own, like the date calculator: this activity stays on its current
            // view underneath, and its own drawer entry shows as checked while it is open.
            startActivity(new Intent(this,
                    com.android.calendar.countdown.compose.ComposeCountdownActivity.class));
        } else if (itemId == R.id.action_todo) {
            // A screen of its own, like the countdown above it in the drawer.
            openTodo(currentViewDateMillis());
        } else if (itemId == R.id.action_settings) {
            mController.sendEvent(this, EventType.LAUNCH_SETTINGS, null, null, 0, 0);
        } else if (itemId == R.id.action_about) {
            Intent intent = new Intent(this,
                    com.android.calendar.about.compose.ComposeAboutActivity.class);
            startActivity(intent);
        }
        mDrawerLayout.closeDrawers();
        return true;
    }

    /** Puts the navigation check mark back on the view this activity is actually showing. */
    private void syncNavigationCheckedItem() {
        if (mNavigationView == null) {
            return;
        }
        Menu menu = mNavigationView.getMenu();

        // The year and three day entries share the exclusive group with the real views, but they
        // open their own screens. Setting another item checked does NOT clear them, so clear them
        // explicitly - otherwise the drawer keeps claiming the user is in the year view.
        MenuItem yearItem = menu.findItem(R.id.year_menu_item);
        if (yearItem != null) {
            yearItem.setChecked(false);
        }
        MenuItem threeDayItem = menu.findItem(R.id.threeday_menu_item);
        if (threeDayItem != null) {
            threeDayItem.setChecked(false);
        }

        int itemId;
        switch (mCurrentView) {
            case ViewType.AGENDA:
                itemId = R.id.agenda_menu_item;
                break;
            case ViewType.WEEK:
                itemId = R.id.week_menu_item;
                break;
            case ViewType.MONTH:
                itemId = R.id.month_menu_item;
                break;
            case ViewType.DAY:
            default:
                itemId = R.id.day_menu_item;
                break;
        }
        MenuItem item = menu.findItem(itemId);
        if (item != null) {
            item.setChecked(true);
        }
    }

    /**
     * Sets the offset of the controls on the right for animating them off/on
     * screen. ProGuard strips this if it's not in proguard.flags
     *
     * @param controlsOffset The current offset in pixels
     */
    public void setControlsOffset(int controlsOffset) {
        if (mOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            mMiniMonth.setTranslationX(controlsOffset);
            mCalendarsList.setTranslationX(controlsOffset);
            mControlsParams.width = Math.max(0, mControlsAnimateWidth - controlsOffset);
            mMiniMonthContainer.setLayoutParams(mControlsParams);
        } else {
            mMiniMonth.setTranslationY(controlsOffset);
            mCalendarsList.setTranslationY(controlsOffset);
            if (mVerticalControlsParams == null) {
                mVerticalControlsParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, mControlsAnimateHeight);
            }
            mVerticalControlsParams.height = Math.max(0, mControlsAnimateHeight - controlsOffset);
            mMiniMonthContainer.setLayoutParams(mVerticalControlsParams);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals(GeneralPreferences.KEY_WEEK_START_DAY) || key.equals(GeneralPreferences.KEY_DAYS_PER_WEEK)) {
            if (mPaused) {
                mUpdateOnResume = true;
            } else {
                initFragments(mController.getTime(), mController.getViewType(), null);
            }
        }
    }

    /**
     * How far into the app a view is, for choosing which way a transition slides.
     *
     * The month is the outermost view and the one everything else is reached from, so it is the
     * top rank; agenda, day and week hang off it. The raw ViewType values would nearly do, but
     * EDIT sits above MONTH in that enum which would send the edit screen the wrong way.
     */
    private static int viewRank(int viewType) {
        switch (viewType) {
            case ViewType.AGENDA:
                return 1;
            case ViewType.DAY:
                return 2;
            case ViewType.WEEK:
                return 3;
            case ViewType.MONTH:
                return 4;
            case ViewType.EDIT:
                return 5;
            default:
                return 4;
        }
    }

    /** Tag of the month view's agenda pane. Named apart from the agenda the drawer opens. */
    private static final String SECONDARY_TAG = "m3e:secondary";

    /** Builds the fragment for a view when there is not one to reuse. */
    private interface FragmentFactory {
        Fragment create();
    }

    /**
     * The tag a view's fragment is filed under, remembering the shape of the view as well as its
     * type.
     *
     * The day view and the week view are both a DayFragment and both answer to the same tag without
     * the day count - but they are built for a different number of days and set that in
     * onCreateView, so a reused one would come back the wrong shape. Two tags, two fragments.
     */
    private static String fragmentTag(int viewType, int variant) {
        return "m3e:frag:" + viewType + ":" + variant;
    }

    /**
     * The variant part of a view's tag, which tells apart the views that share a fragment class.
     *
     * The day view and the week view are both a DayFragment, built for a different number of days;
     * everything else has one shape. Used both when looking a fragment up and when filing a new one,
     * so the two cannot disagree.
     */
    private int fragmentVariant(int viewType) {
        switch (viewType) {
            case ViewType.DAY:
                return 1;
            case ViewType.WEEK:
                return Utils.getDaysPerWeek(this);
            default:
                return 0;
        }
    }

    /** The fragment for a full-pane view, made once and then reused. */
    private Fragment findOrCreateFragment(FragmentManager fm, int viewType, int variant,
            FragmentFactory factory) {
        String tag = fragmentTag(viewType, variant);
        Fragment existing = fm.findFragmentByTag(tag);
        if (existing == null && tag.equals(mPendingMainPaneTag) && mAttachedMainFragment != null) {
            // The replace that filed this fragment has not run yet, so the tag lookup above misses
            // it - the fragment is the one that was just scheduled. Creating a second instance for
            // the same tag is what left the controller's event handler pointing at a fragment that
            // was not the one on screen: the two replace transactions raced, one instance was
            // removed right after being added, and its detached handler kept eating the GO_TO
            // events - today taps moved a view nobody saw while the toolbar title followed them.
            existing = mAttachedMainFragment;
        }
        if (existing != null) {
            return existing;
        }
        Fragment created = factory.create();
        return created;
    }

    /**
     * Every fragment currently in the main pane.
     *
     * Read from the FragmentManager rather than tracked in a field, because after a configuration
     * change the framework restores the fragments by itself and a field would not know about them.
     */
    private java.util.List<Fragment> mainPaneFragments(FragmentManager fm) {
        java.util.List<Fragment> found = new java.util.ArrayList<>();
        for (Fragment fragment : new java.util.ArrayList<>(fm.getFragments())) {
            if (fragment != null && fragment.getId() == R.id.main_pane) {
                found.add(fragment);
            }
        }
        return found;
    }

    /** The main pane's fragments with their state, for the log. */
    private String describeMainPane(FragmentManager fm) {
        StringBuilder out = new StringBuilder("[");
        for (Fragment fragment : mainPaneFragments(fm)) {
            if (out.length() > 1) {
                out.append(' ');
            }
            out.append(fragment.getTag())
                    .append(fragment.isDetached() ? "/detached" : "/attached")
                    .append(fragment.isHidden() ? "/hidden" : "/shown")
                    .append(fragment.getView() == null ? "/noview" : "/hasview");
        }
        return out.append(']').toString();
    }

    /** The fragment filed under a tag, or a new one when that tag is not in use. */
    private Fragment findOrCreateFragmentByTag(FragmentManager fm, String tag,
            FragmentFactory factory) {
        Fragment existing = fm.findFragmentByTag(tag);
        return existing != null ? existing : factory.create();
    }
    /** Set by tapping the overlay, so it can be got out of the way of what it covers. */

    /** The view type by name, so the overlay cannot be misread as it was once. */
    private static String viewName(int viewType) {
        switch (viewType) {
            case ViewType.AGENDA: return "AGENDA(1)";
            case ViewType.DAY: return "DAY(2)";
            case ViewType.WEEK: return "WEEK(3)";
            case ViewType.MONTH: return "MONTH(4)";
            case ViewType.EDIT: return "EDIT(5)";
            default: return "CURRENT(" + viewType + ")";
        }
    }

    private void setMainPane(
            FragmentTransaction ft, int viewId, int viewType, long timeMillis, boolean force) {
        if (mOnSaveInstanceStateCalled) {
            return;
        }
        if (!force && mCurrentView == viewType) {
            return;
        }

        // The view being left, captured now. mCurrentView is overwritten further down, and this
        // method is also called from the GO_TO handler, where the controller has already moved to
        // the new view type - so reading mCurrentView any later compares the new view with itself,
        // every switch looks like a step away from the month, and every one slides the same way.
        final int viewBeingLeft = mCurrentView;

        // Every view switch is animated, the month view included.
        //
        // This used to exclude any switch that had the month view on either side - "remove this
        // when transition to and from month view looks fine" - which is why stepping back to the
        // month from the day or week view simply appeared, with no movement at all to show which
        // way the user had gone.
        boolean doTransition = viewType != mCurrentView;
        FragmentManager fragmentManager = getSupportFragmentManager();
        // Check if our previous view was an Agenda view
        // TODO remove this if framework ever supports nested fragments
        if (mCurrentView == ViewType.AGENDA) {
            // If it was, we need to do some cleanup on it to prevent the
            // edit/delete buttons from coming back on a rotation.
            Fragment oldFrag = fragmentManager.findFragmentById(viewId);
            if (oldFrag instanceof AgendaFragment) {
                ((AgendaFragment) oldFrag).removeFragments(fragmentManager);
            }
        }

        if (viewType != mCurrentView) {
            // The rules for this previous view are different than the
            // controller's and are used for intercepting the back button.
            if (mCurrentView != ViewType.EDIT && mCurrentView > 0) {
                mPreviousView = mCurrentView;
            }
            mCurrentView = viewType;
            updateViewSwitchButtons();
        }
        // The back callback must stop swallowing back once the app would exit, so that Android can
        // play the predictive "back to the launcher" animation.
        updateBackCallbackEnabled();
        // Reuse the fragment that was here before, when there is one.
        //
        // This used to build a new fragment and hand it to replace() on every switch, which throws
        // away the old view hierarchy and inflates the new one from scratch. Measured on the phone,
        // one switch cost about forty views being rebuilt and each of them asking for its fonts
        // again, and the main thread spent the following seconds working through the messages that
        // produced. Reusing keeps the view alive, so coming back to a view is nearly free.
        //
        // The fragments read the date from GO_TO events rather than from the time passed in here, so
        // an existing one is told where to go by the event that follows this; the constructor
        // argument only decides the date shown the first time a fragment is built.
        Fragment frag = null;
        Fragment secFrag = null;
        FragmentManager fm = getSupportFragmentManager();
        // One line per stage inside this method. The view switch that locks the screen never reaches
        // onCreateView, so the cost is somewhere in here, and each stage has to be told apart.
        switch (viewType) {
            case ViewType.AGENDA:
                mNavigationView.getMenu().findItem(R.id.agenda_menu_item).setChecked(true);
                frag = findOrCreateFragment(fm, ViewType.AGENDA, 0,
                        () -> new AgendaFragment(timeMillis, false));
                if (mIsTabletConfig) {
                    mToolbar.setTitle(R.string.agenda_view);
                }
                break;
            case ViewType.DAY:
                mNavigationView.getMenu().findItem(R.id.day_menu_item).setChecked(true);
                frag = findOrCreateFragment(fm, ViewType.DAY, 1,
                        () -> new DayFragment(timeMillis, 1));
                if (mIsTabletConfig) {
                    mToolbar.setTitle(R.string.day_view);
                }
                break;
            case ViewType.MONTH:
                mNavigationView.getMenu().findItem(R.id.month_menu_item).setChecked(true);
                frag = findOrCreateFragment(fm, ViewType.MONTH, 0,
                        () -> new MonthByWeekFragment(timeMillis, false));
                if (mShowAgendaWithMonth) {
                    // Tagged apart from the agenda the drawer opens, so the two cannot be confused
                    // for one another - the secondary pane's copy is the one that has to be removed
                    // when the month is left.
                    secFrag = findOrCreateFragmentByTag(fm, SECONDARY_TAG,
                            () -> new AgendaFragment(timeMillis, false));
                }
                if (mIsTabletConfig) {
                    mToolbar.setTitle(R.string.month_view);
                }
                break;
            case ViewType.WEEK:
            default:
                mNavigationView.getMenu().findItem(R.id.week_menu_item).setChecked(true);
                frag = findOrCreateFragment(fm, ViewType.WEEK, Utils.getDaysPerWeek(this),
                        () -> new DayFragment(timeMillis, Utils.getDaysPerWeek(this)));
                if (mIsTabletConfig) {
                    mToolbar.setTitle(R.string.week_view);
                }
                break;
        }
        // Update the current view so that the menu can update its look according to the
        // current view.
        if (mCalendarToolbarHandler != null) {
            mCalendarToolbarHandler.setCurrentMainView(viewType);
        }

        if (!mIsTabletConfig) {
            refreshActionbarTitle(timeMillis);
        }

        // Show date only on tablet configurations in views different than Agenda
        if (!mIsTabletConfig) {
            mDateRange.setVisibility(View.GONE);
        } else if (viewType != ViewType.AGENDA) {
            mDateRange.setVisibility(View.VISIBLE);
        } else {
            mDateRange.setVisibility(View.GONE);
        }

        // Clear unnecessary buttons from the option menu when switching from the agenda view
        if (viewType != ViewType.AGENDA) {
            clearOptionsMenu();
        }

        boolean doCommit = false;
        if (ft == null) {
            doCommit = true;
            ft = fragmentManager.beginTransaction();
        }

        if (doTransition && frag != null) {
            // Sliding rather than fading: the month is the widest view and the others are reached
            // from it, so a sideways movement reads as going into one and coming back out.
            //
            // Away from the month everything moves left: the new view arrives from the right
            // (slide_left_in) while the month leaves to the left (slide_left_out). Back towards
            // the month it is the mirror: the month arrives from the left (slide_right_in) and
            // the view being left goes off to the right (slide_right_out).
            boolean towardsMonth = viewRank(viewType) > viewRank(viewBeingLeft);
            int arriveAnim = towardsMonth ? R.anim.slide_right_in : R.anim.slide_left_in;
            int leaveAnim = towardsMonth ? R.anim.slide_right_out : R.anim.slide_left_out;
            ft.setCustomAnimations(arriveAnim, leaveAnim);
        }

        // Bring the fragment in by showing it, or adding it the first time.
        //
        // replace() is what this used to do, and it destroys the fragment's view on the way out -
        // so every switch back inflated the whole grid again, which is the cost this avoids. A
        // fragment that is merely hidden keeps its view, so returning to a view that has been seen
        // before draws what is already built.
        //
        // detach() was tried for this and was wrong: it takes the view away, and the pane came back
        // empty after a few switches and stayed empty until the app was restarted. hide() only sets
        // a visibility flag, so there is no view to be rebuilt and nothing to get out of step.
        //
        // The ones being hidden go into the same transaction as the one being shown, rather than a
        // transaction of their own: a separate one could run against the framework's state at a
        // different moment than the show did.
        // The view being left goes away and the new one is built, which is what replace does.
        //
        // Keeping the other views alive was tried twice - once by detaching, once by hiding - and both
        // are wrong for the same underlying reason: a fragment that is not in its attached, visible
        // state has a saved state the framework cannot rebuild a view from.
        //
        // detach was the worse of the two. It leaves the fragment DETACHED, and the framework skips
        // creating a view for a detached fragment on restore - it also writes the container id as 0,
        // so the fragment no longer knows where it belongs. Rotating while a detached fragment was
        // saved therefore produced a fragment that could never be shown again: the pane came back
        // empty and stayed empty for the rest of the process's life. hide was subtler: the fragment
        // was restored without a view and show() only sets a visibility flag, so the same empty pane
        // appeared, though it recovered when the fragment was built again.
        //
        // replace costs a view rebuild on each switch, which is the price of the state always being
        // one the framework can reconstruct. It is the behaviour the app shipped with, and the
        // switches are what they were before this was touched.
        ft.replace(viewId, frag, fragmentTag(viewType, fragmentVariant(viewType)));
        mAttachedMainFragment = frag;
        // The tag the pane's fragment will be filed under once the transaction runs. A second
        // setMainPane for the same view before that - the force rebuild that follows the first
        // switch right away - must reuse this fragment instead of making another one for the tag.
        mPendingMainPaneTag = fragmentTag(viewType, fragmentVariant(viewType));

        // What the pane is about to contain, written down because a switch that leaves it empty has
        // to be told apart from one that leaves the wrong fragment in it.

        if (mShowAgendaWithMonth) {
            // Show/hide secondary fragment

            if (secFrag != null) {
                ft.replace(R.id.secondary_pane, secFrag, SECONDARY_TAG);
                mSecondaryPane.setVisibility(View.VISIBLE);
                mAttachedSecondaryFragment = secFrag;
            } else {
                mSecondaryPane.setVisibility(View.GONE);
                Fragment f = fragmentManager.findFragmentById(R.id.secondary_pane);
                if (f != null) {
                    ft.remove(f);
                }
                mAttachedSecondaryFragment = null;
                mController.deregisterEventHandler(R.id.secondary_pane);
            }
        }
        if (DEBUG) {
            Log.d(TAG, "Adding handler with viewId " + viewId + " and type " + viewType);
        }
        // If the key is already registered this will replace it
        mController.registerEventHandler(viewId, (EventHandler) frag);
        if (secFrag != null) {
            mController.registerEventHandler(viewId, (EventHandler) secFrag);
        }

        if (doCommit) {
            if (DEBUG) {
                Log.d(TAG, "setMainPane AllInOne=" + this + " finishing:" + this.isFinishing());
            }
            ft.commit();
        }
    }

    private void refreshActionbarTitle(long timeMillis) {
        if (mCalendarToolbarHandler != null) {
            mCalendarToolbarHandler.setTime(timeMillis);
        }
    }

    private void setTitleInActionBar(EventInfo event) {
        if (event.eventType != EventType.UPDATE_TITLE) {
            return;
        }

        final long start = event.startTime.toMillis();
        final long end;
        if (event.endTime != null) {
            end = event.endTime.toMillis();
        } else {
            end = start;
        }

        final String msg = Utils.formatDateRange(this, start, end, (int) event.extraLong);
        CharSequence oldDate = mDateRange.getText();
        mDateRange.setText(msg);
        updateSecondaryTitleFields(event.selectedTime != null ? event.selectedTime.toMillis()
                : start);
        if (!TextUtils.equals(oldDate, msg)) {
            mDateRange.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
            if (mShowWeekNum && mWeekTextView != null) {
                mWeekTextView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
            }
        }
    }

    private void updateSecondaryTitleFields(long visibleMillisSinceEpoch) {
        mShowWeekNum = Utils.getShowWeekNumber(this);
        mTimeZone = Utils.getTimeZone(this, mHomeTimeUpdater);
        if (visibleMillisSinceEpoch != -1) {
            mWeekNum = Utils.getWeekNumberFromTime(visibleMillisSinceEpoch, this);
        }

        if (mShowWeekNum && (mCurrentView == ViewType.WEEK) && mIsTabletConfig
                && mWeekTextView != null) {
            String weekString = getResources().getQuantityString(R.plurals.weekN, mWeekNum,
                    mWeekNum);
            mWeekTextView.setText(weekString);
            mWeekTextView.setVisibility(View.VISIBLE);
        } else if (visibleMillisSinceEpoch != -1 && mWeekTextView != null
                && mCurrentView == ViewType.DAY && mIsTabletConfig) {
            Time time = new Time(mTimeZone);
            time.set(visibleMillisSinceEpoch);
            int julianDay = Time.getJulianDay(visibleMillisSinceEpoch, time.getGmtOffset());
            time.set(System.currentTimeMillis());
            int todayJulianDay = Time.getJulianDay(time.toMillis(), time.getGmtOffset());
            String dayString = Utils.getDayOfWeekString(julianDay, todayJulianDay,
                    visibleMillisSinceEpoch, this);
            mWeekTextView.setText(dayString);
            mWeekTextView.setVisibility(View.VISIBLE);
        } else if (mWeekTextView != null && (!mIsTabletConfig || mCurrentView != ViewType.DAY)) {
            mWeekTextView.setVisibility(View.GONE);
        }

        if (mHomeTime != null
                && (mCurrentView == ViewType.DAY || mCurrentView == ViewType.WEEK
                        || mCurrentView == ViewType.AGENDA)
                && !TextUtils.equals(mTimeZone, Utils.getCurrentTimezone())) {
            Time time = new Time(mTimeZone);
            time.set(System.currentTimeMillis());
            long millis = time.toMillis();
            int flags = DateUtils.FORMAT_SHOW_TIME;
            if (DateFormat.is24HourFormat(this)) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
            // Formats the time as
            String timeString = (new StringBuilder(
                    Utils.formatDateRange(this, millis, millis, flags))).append(" ").append(
                    TimeZone.getTimeZone(mTimeZone).getDisplayName(
                            false, TimeZone.SHORT, Locale.getDefault())).toString();
            mHomeTime.setText(timeString);
            mHomeTime.setVisibility(View.VISIBLE);
            // Update when the minute changes
            mHomeTime.removeCallbacks(mHomeTimeUpdater);
            mHomeTime.postDelayed(
                    mHomeTimeUpdater,
                    DateUtils.MINUTE_IN_MILLIS - (millis % DateUtils.MINUTE_IN_MILLIS));
        } else if (mHomeTime != null) {
            mHomeTime.setVisibility(View.GONE);
        }
    }

    public void goToDate() {
        MaterialPickerOnPositiveButtonClickListener<Long> materialPickerOnPositiveButtonClickListener = new MaterialPickerOnPositiveButtonClickListener<>() {
            @Override
            public void onPositiveButtonClick(Long selection) {
                Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                calendar.setTime(new Date(selection));

                Time selectedTime = new Time(mTimeZone);
                selectedTime.set(System.currentTimeMillis());  // Needed for recalc function in DayView(time + gmtoff)
                selectedTime.setYear(calendar.get(Calendar.YEAR));
                selectedTime.setMonth(calendar.get(Calendar.MONTH));
                selectedTime.setDay(calendar.get(Calendar.DAY_OF_MONTH));

                // Marked explicit, like the year view's month choice.
                //
                // The month view holds still when it is sent to a month other than the one on screen,
                // so that a tapped grid tail does not move the page - and a date chosen from this
                // picker is one of those sends. Without the mark it was treated as a tap: the picker
                // closed, nothing moved, and because the month on screen never changed the heading did
                // not change either, so the picker looked as though it had done nothing at all.
                long extras = CalendarController.EXTRA_GOTO_TIME | CalendarController.EXTRA_GOTO_DATE
                        | CalendarController.EXTRA_GOTO_EXPLICIT;
                mController.sendEvent(this, EventType.GO_TO, selectedTime, null, selectedTime, -1, ViewType.CURRENT, extras, null, null);
            }
        };

        CalendarConstraints calendarConstraints = new CalendarConstraints.Builder()
                .setFirstDayOfWeek(Utils.getFirstDayOfWeekAsCalendar(this))
                .build();

        // Opens on the day the view is showing - the day view's own day, or the first day of the
        // week the week view is on - rather than always on today. A picker opened from a view that
        // is paged somewhere else has to start where the view is, or choosing "today" becomes the
        // only way to relate it to the heading it was tapped from.
        //
        // MaterialDatePicker reads its selection as a UTC day, so the value handed over is the
        // chosen local day expressed as UTC midnight; anything else lands one day off for a zone
        // ahead of UTC.
        long pickerSelection;
        if (mDayWeekViewDisplayedMillis > 0) {
            java.time.LocalDate displayed = java.time.Instant.ofEpochMilli(mDayWeekViewDisplayedMillis)
                    .atZone(getZoneId()).toLocalDate();
            pickerSelection = displayed.atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant().toEpochMilli();
        } else {
            pickerSelection = java.time.LocalDate.now(getZoneId())
                    .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
        }

        MaterialDatePicker<Long> datePickerDialog = MaterialDatePicker.Builder.datePicker()
                .setSelection(pickerSelection)
                .setCalendarConstraints(calendarConstraints)
                .setTitleText(R.string.goto_date)
                .build();

        datePickerDialog.addOnPositiveButtonClickListener(materialPickerOnPositiveButtonClickListener);
        datePickerDialog.show(getSupportFragmentManager(), "GoTo");
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.GO_TO | EventType.VIEW_EVENT | EventType.UPDATE_TITLE;
    }

    @Override
    public void handleEvent(EventInfo event) {
        long displayTime = -1;
        if (event.eventType == EventType.GO_TO) {
            if ((event.extraLong & CalendarController.EXTRA_GOTO_BACK_TO_PREVIOUS) != 0) {
                mBackToPreviousView = true;
            } else if (event.viewType != mController.getPreviousViewType()
                    && event.viewType != ViewType.EDIT) {
                // Clear the flag is change to a different view type
                mBackToPreviousView = false;
            }
            updateBackCallbackEnabled();

            // Check toMillis method for the value -1 and if yes add one hour.
            // This prevents the date "1970" from being displayed on the day of the daylight saving time changeover when you tap on the hour that is skipped.
            if (event.startTime.toMillis() == -1) {
                event.startTime.set(0, 0, 1, event.startTime.getDay(), event.startTime.getMonth(), event.startTime.getYear());
            }

            setMainPane(
                    null, R.id.main_pane, event.viewType, event.startTime.toMillis(), false);
            if (mSearchView != null) {
                mSearchView.clearFocus();
            }
            if (mShowCalendarControls) {
                int animationSize = (mOrientation == Configuration.ORIENTATION_LANDSCAPE) ?
                        mControlsAnimateWidth : mControlsAnimateHeight;
                boolean noControlsView = event.viewType == ViewType.MONTH || event.viewType == ViewType.AGENDA;
                if (mControlsMenu != null) {
                    mControlsMenu.setVisible(!noControlsView);
                    mControlsMenu.setEnabled(!noControlsView);
                }
                if (noControlsView || mHideControls) {
                    // hide minimonth and calendar frag
                    mShowSideViews = false;
                    if (!mHideControls) {
                            final ObjectAnimator slideAnimation = ObjectAnimator.ofInt(this,
                                    "controlsOffset", 0, animationSize);
                            slideAnimation.addListener(mSlideAnimationDoneListener);
                            slideAnimation.setDuration(mCalendarControlsAnimationTime);
                            ObjectAnimator.setFrameDelay(0);
                            slideAnimation.start();
                    } else {
                        mMiniMonth.setVisibility(View.GONE);
                        mCalendarsList.setVisibility(View.GONE);
                        mMiniMonthContainer.setVisibility(View.GONE);
                    }
                } else {
                    // show minimonth and calendar frag
                    mShowSideViews = true;
                    mMiniMonth.setVisibility(View.VISIBLE);
                    mCalendarsList.setVisibility(View.VISIBLE);
                    mMiniMonthContainer.setVisibility(View.VISIBLE);
                    if (!mHideControls &&
                            (mController.getPreviousViewType() == ViewType.MONTH ||
                             mController.getPreviousViewType() == ViewType.AGENDA)) {
                        final ObjectAnimator slideAnimation = ObjectAnimator.ofInt(this,
                                "controlsOffset", animationSize, 0);
                        slideAnimation.setDuration(mCalendarControlsAnimationTime);
                        ObjectAnimator.setFrameDelay(0);
                        slideAnimation.start();
                    }
                }
            }
            updateViewSettingsVisiblility();
            displayTime = event.selectedTime != null ? event.selectedTime.toMillis()
                    : event.startTime.toMillis();
            if (!mIsTabletConfig) {
                refreshActionbarTitle(displayTime);
            }
        } else if (event.eventType == EventType.VIEW_EVENT) {

            // If in Agenda view and "show_event_details_with_agenda" is "true",
            // do not create the event info fragment here, it will be created by the Agenda
            // fragment

            if (mCurrentView == ViewType.AGENDA && mShowEventDetailsWithAgenda) {
                if (event.startTime != null && event.endTime != null) {
                    // Event is all day , adjust the goto time to local time
                    if (event.isAllDay()) {
                        long start = Utils.convertAlldayUtcToLocal(
                                null, event.startTime.toMillis(), mTimeZone);
                        long end = Utils.convertAlldayUtcToLocal(
                                null, event.endTime.toMillis(), mTimeZone);
                        event.startTime.setTimezone(mTimeZone);
                        event.startTime.set(start);
                        event.endTime.setTimezone(mTimeZone);
                        event.endTime.set(end);
                    }
                    mController.sendEvent(this, EventType.GO_TO, event.startTime, event.endTime,
                            event.selectedTime, event.id, ViewType.AGENDA,
                            CalendarController.EXTRA_GOTO_TIME, null, null);
                } else if (event.selectedTime != null) {
                    mController.sendEvent(this, EventType.GO_TO, event.selectedTime,
                        event.selectedTime, event.id, ViewType.AGENDA);
                }
            } else {
                // TODO Fix the temp hack below: && mCurrentView !=
                // ViewType.AGENDA
                if (event.selectedTime != null && mCurrentView != ViewType.AGENDA) {
                    mController.sendEvent(this, EventType.GO_TO, event.selectedTime,
                            event.selectedTime, -1, ViewType.CURRENT);
                }
                // A to-do is an all-day event in a calendar of its own, which is what puts it in the
                // all-day strip of every view. Tapping it there lands here, and it opens the to-do
                // screen for its day rather than the event details: it is a checklist, not an event.
                if (openTodoForEvent(event.id)) {
                    displayTime = event.startTime.toMillis();
                    return;
                }
                // Every view now opens the Material 3 Expressive details screen. The classic
                // EventInfoActivity / EventInfoFragment pair is still reachable from elsewhere, but
                // tapping an event in the calendar no longer lands in it.
                Intent details = new Intent(this,
                        com.android.calendar.event.compose.ComposeEventDetailsActivity.class);
                details.putExtra(
                        com.android.calendar.event.compose.ComposeEventDetailsActivity
                                .EXTRA_EVENT_ID,
                        event.id);
                startActivity(details);
            }
            displayTime = event.startTime.toMillis();
        } else if (event.eventType == EventType.UPDATE_TITLE) {
            if (event.startTime != null) {
                if (mCurrentView == ViewType.MONTH) {
                    mMonthViewDisplayedMillis = event.startTime.toMillis();
                } else if (mCurrentView == ViewType.DAY || mCurrentView == ViewType.WEEK) {
                    // The day the day view is on, or the first day of the week the week view is
                    // on: both hand it over in the title event, and both need it passed on to the
                    // three day screen the same way the month view does.
                    mDayWeekViewDisplayedMillis = event.startTime.toMillis();
                }
            }
            setTitleInActionBar(event);
            if (!mIsTabletConfig) {
                refreshActionbarTitle(mController.getTime());
            }
            // The title text is replaced on every view change, and the caret rides on that same
            // view, so it is re-applied here.
            installTitlePicker();
        }
        updateSecondaryTitleFields(displayTime);
    }

    // Needs to be in proguard whitelist
    // Specified as listener via android:onClick in a layout xml
    public void handleSelectSyncedCalendarsClicked(View v) {
        mController.sendEvent(this, EventType.LAUNCH_SETTINGS, null, null, null, 0, 0,
                CalendarController.EXTRA_GOTO_TIME, null,
                null);
    }

    /**
     * Shows, in the toolbar, one button for each of the day, week and month views other than
     * the current one, when the "View buttons in the toolbar" setting is enabled
     */
    private void updateViewSwitchButtons() {
        if (mOptionsMenu == null) {
            return;
        }
        boolean enabled = Utils.getSharedPreference(this,
                GeneralPreferences.KEY_VIEW_SWITCH_BUTTONS, false);
        setViewSwitchButton(R.id.action_view_day, enabled && mCurrentView != ViewType.DAY);
        setViewSwitchButton(R.id.action_view_week, enabled && mCurrentView != ViewType.WEEK);
        setViewSwitchButton(R.id.action_view_month, enabled && mCurrentView != ViewType.MONTH);

        // On phones, Android only keeps two "if room" icons next to the overflow menu : force the
        // "Today" icon so that it is not pushed into the overflow menu by the view buttons
        MenuItem today = mOptionsMenu.findItem(R.id.action_today);
        if (today != null) {
            today.setShowAsAction(enabled
                    ? MenuItem.SHOW_AS_ACTION_ALWAYS : MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
    }

    private void setViewSwitchButton(int itemId, boolean visible) {
        MenuItem item = mOptionsMenu.findItem(itemId);
        if (item != null) {
            item.setVisible(visible);
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
    }

    /**
     * Switches to another view. Leaving the month view, the current week (today) is shown when
     * it is visible in the month, rather than the first week displayed on screen.
     */
    private void switchToView(int viewType) {
        Time start = null;
        if (mCurrentView == ViewType.MONTH && viewType != ViewType.MONTH) {
            start = todayIfVisibleInMonthView();
        }
        mController.sendEvent(this, EventType.GO_TO, start, null, -1, viewType);
    }

    /**
     * Today, if it is part of the weeks displayed by the month view ; `null` otherwise.
     * The date arithmetic is done with `java.time` ; only the result is converted to a `Time`.
     */
    private Time todayIfVisibleInMonthView() {
        if (mMonthViewDisplayedMillis <= 0) {
            return null;
        }
        ZoneId zone = getZoneId();
        LocalDate today = LocalDate.now(zone);

        // The month view starts with the week containing the 1st of the displayed month
        LocalDate firstOfMonth = Instant.ofEpochMilli(mMonthViewDisplayedMillis)
                .atZone(zone).toLocalDate().withDayOfMonth(1);
        LocalDate firstVisibleDay = firstOfMonth.with(
                TemporalAdjusters.previousOrSame(getFirstDayOfWeek()));
        LocalDate lastVisibleDay = firstVisibleDay.plusWeeks(MONTH_VIEW_WEEKS).minusDays(1);

        if (today.isBefore(firstVisibleDay) || today.isAfter(lastVisibleDay)) {
            return null;
        }
        Time now = new Time(mTimeZone);
        now.setToNow();
        return now;
    }

    /**
     * The date the view on screen is on, for the screens started from here: the three day view and
     * the agenda.
     *
     * The day and week views hand over the day - or the first day of the week - their titles are
     * on. The month view follows the rule switchToView() uses: today while it is part of the month
     * being shown, the calendar's own time otherwise, which is the month the user paged to.
     * Anything else - the agenda itself, for instance - falls back to the calendar's time, and
     * finally to now.
     */
    private long currentViewDateMillis() {
        if (mCurrentView == ViewType.MONTH) {
            Time todayInMonth = todayIfVisibleInMonthView();
            if (todayInMonth != null) {
                return todayInMonth.toMillis();
            }
        } else if ((mCurrentView == ViewType.DAY || mCurrentView == ViewType.WEEK)
                && mDayWeekViewDisplayedMillis > 0) {
            // The week view's own day is the first of the week it is showing, but a week that holds
            // today should hand today over: the screen being opened is about the day the user is
            // looking at, and with today's week that is today rather than its Monday. A week paged
            // away from today has no today in it, so its first day is the day to pass on.
            if (mCurrentView == ViewType.WEEK) {
                LocalDate first = Instant.ofEpochMilli(mDayWeekViewDisplayedMillis)
                        .atZone(getZoneId()).toLocalDate();
                LocalDate today = LocalDate.now(getZoneId());
                int days = Utils.getDaysPerWeek(this);
                if (!today.isBefore(first) && today.isBefore(first.plusDays(days))) {
                    return today.atStartOfDay(getZoneId()).toInstant().toEpochMilli();
                }
            }
            return mDayWeekViewDisplayedMillis;
        }
        long controllerTime = mController.getTime();
        return controllerTime > 0 ? controllerTime : System.currentTimeMillis();
    }

    /**
     * Opens the to-do screen on the day [millis] falls on, or on today when it is not a time.
     *
     * The screen pages day by day on its own from there, so the date is handed over rather than
     * turned into an intent that names a view type.
     */
    private void openTodo(long millis) {
        long when = millis > 0 ? millis : System.currentTimeMillis();
        java.time.LocalDate date = Instant.ofEpochMilli(when).atZone(getZoneId()).toLocalDate();
        startActivity(com.android.calendar.todo.compose.ComposeTodoActivity.Companion
                .intent(this, date));
    }

    /**
     * Opens the to-do an event carries, when it is one, and reports whether it did.
     *
     * A to-do is stored as an all-day event in a calendar of its own - that is what puts it in the
     * all-day strip of the day, three day, week and month views without those views knowing what a
     * to-do is - so a tap on one arrives here as a view-event request and has to end up on the to-do
     * screen for its day instead of on the event details.
     */
    private boolean openTodoForEvent(long eventId) {
        return com.android.calendar.todo.TodoNavigatorKt.openTodoForEvent(this, eventId);
    }

    /** Timezone of the calendar views, falling back to the device one */
    private ZoneId getZoneId() {        try {
            if (mTimeZone != null) {
                return ZoneId.of(mTimeZone);
            }
        } catch (DateTimeException e) {
            Log.w(TAG, "Unknown time zone " + mTimeZone + ", using the device one", e);
        }
        return ZoneId.systemDefault();
    }

    /** First day of the week setting, as a DayOfWeek (Utils returns Time.SUNDAY = 0, SATURDAY = 6) */
    private DayOfWeek getFirstDayOfWeek() {
        int timeDay = Utils.getFirstDayOfWeek(this);
        return timeDay == Time.SUNDAY ? DayOfWeek.SUNDAY : DayOfWeek.of(timeDay);
    }

    @Override
    public void eventsChanged() {
        mController.sendEvent(this, EventType.EVENTS_CHANGED, null, null, -1, ViewType.CURRENT);
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        mSearchMenu.collapseActionView();
        mController.sendEvent(this, EventType.SEARCH, null, null, -1, ViewType.CURRENT, 0, query,
                getComponentName());
        return true;
    }

    @Override
    public boolean onSuggestionSelect(int position) {
        return false;
    }

    @Override
    public boolean onSuggestionClick(int position) {
        mSearchMenu.collapseActionView();
        return false;
    }

    @Override
    public boolean onSearchRequested() {
        if (mSearchMenu != null) {
            mSearchMenu.expandActionView();
        }
        return false;
    }

    private class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(ContentResolver cr) {
            super(cr);
        }
    }
}
