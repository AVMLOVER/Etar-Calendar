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
    private ContentResolver mContentResolver;
    private int mPreviousView;
    private int mCurrentView;
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
                mController.sendEvent(this, EventType.GO_TO, time, time, -1,
                        explicitView ? requestedView : ViewType.CURRENT);
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
        mIsMultipane = Utils.getConfigBool(this, R.bool.multiple_pane_config);
        mIsTabletConfig = Utils.getConfigBool(this, R.bool.tablet_config);
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

        mToolbar.setNavigationOnClickListener(v -> AllInOneActivity.this.openDrawer());
        mActionBar = getSupportActionBar();
        if (mActionBar == null) return;
        mActionBar.setDisplayHomeAsUpEnabled(true);
        mActionBar.setHomeButtonEnabled(true);

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
        title.setOnClickListener(v -> showTitlePicker());
    }

    /** The ActionBar's title view: the TextView in the toolbar whose text is the current title. */
    private TextView findToolbarTitle() {
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
        mTitlePopup = com.android.calendar.chrome.compose.M3eMonthPickerDialog.INSTANCE.show(
                this, mToolbar, (year, month) -> {
                    Time selected = new Time(mTimeZone);
                    selected.set(System.currentTimeMillis());
                    selected.setYear(year);
                    selected.setMonth(month - 1);
                    selected.setDay(1);
                    long extras = CalendarController.EXTRA_GOTO_TIME
                            | CalendarController.EXTRA_GOTO_DATE;
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
     * Where the add button puts a new event.     *
     * In the month view the answer follows the month being looked at rather than the clock: adding
     * from a month you have browsed to used to land on today, which in another month is an arbitrary
     * day somewhere in the grid. A month other than this one gets its first day; the current month
     * gets today, an hour or two ahead on the half hour, which is what "add now" should mean.
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

        // Round up to the next half hour, then an hour on: a slot beginning shortly rather than one
        // that has already started.
        Time t = new Time(mTimeZone);
        t.set(now);
        if (t.getMinute() >= 30) {
            t.setHour(t.getHour() + 1);
            t.setMinute(0);
        } else if (t.getMinute() > 0) {
            t.setMinute(30);
        }
        t.setHour(t.getHour() + 1);
        t.normalize();
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
            mController.sendEvent(this, EventType.GO_TO, now, now, -1, ViewType.CURRENT,
                    CalendarController.EXTRA_GOTO_DATE, null, null);
        }
        viewInitialised = true;

        mCalIntentReceiver = Utils.setTimeChangesReceiver(this, mTimeChangesUpdater);
    }

    /** True once the first onResume has run, so the forced re-read does not double the first load. */
    private boolean viewInitialised = false;


    @Override
    protected void onPause() {
        super.onPause();

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
        if (mController.getViewType() != ViewType.EDIT) {
            Utils.setDefaultView(this, mController.getViewType());
        }
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
            boolean viewSettingsVisible = mController.getViewType() == ViewType.MONTH;
            mViewSettings.setVisible(viewSettingsVisible);
            mViewSettings.setEnabled(viewSettingsVisible);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        mOptionsMenu = menu;
        getMenuInflater().inflate(R.menu.all_in_one_title_bar, menu);

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

        return true;
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
            // Material 3 Expressive agenda screen.
            startActivity(new Intent(this,
                    com.android.calendar.agenda.compose.ComposeAgendaActivity.class));
        } else if (itemId == R.id.action_view_settings) {
            // "View details" now opens the Material 3 Expressive settings screen, which hosts
            // the month-view layout options that used to live on the old preference page.
            startActivity(new Intent(this,
                    com.android.calendar.settings.compose.ComposeSettingsActivity.class));
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
                mController.sendEvent(this, EventType.GO_TO, null, null, -1, ViewType.DAY);
            }
        } else if (itemId == R.id.week_menu_item) {
            if (mCurrentView != ViewType.WEEK) {
                mController.sendEvent(this, EventType.GO_TO, null, null, -1, ViewType.WEEK);
            }
        } else if (itemId == R.id.threeday_menu_item) {
            // Like the year and agenda entries, the three day view is its own screen, so this
            // activity keeps the view it was showing underneath and the check mark goes back to it.
            startActivity(new Intent(this,
                    com.android.calendar.threeday.compose.ComposeThreeDayActivity.class));
            syncNavigationCheckedItem();
        } else if (itemId == R.id.month_menu_item) {
            if (mCurrentView != ViewType.MONTH) {
                mController.sendEvent(this, EventType.GO_TO, null, null, -1, ViewType.MONTH);
            }
        } else if (itemId == R.id.agenda_menu_item) {
            // The agenda is the Material 3 Expressive screen now. It is its own activity, so this
            // activity keeps whatever view it was showing underneath, like the year view does.
            startActivity(new Intent(this,
                    com.android.calendar.agenda.compose.ComposeAgendaActivity.class));
            syncNavigationCheckedItem();
        } else if (itemId == R.id.year_menu_item) {
            // The year view is its own Compose screen, so this activity stays on its current
            // view; restore the check mark the drawer just moved.
            startActivity(new Intent(this,
                    com.android.calendar.year.compose.ComposeYearActivity.class));
            syncNavigationCheckedItem();
        } else if (itemId == R.id.action_date_calculator) {
            startActivity(new Intent(this,
                    com.android.calendar.datecalc.compose.ComposeDateCalculatorActivity.class));
        } else if (itemId == R.id.action_countdown) {
            // A tool screen of its own, like the date calculator: this activity stays on its current
            // view underneath, and its own drawer entry shows as checked while it is open.
            startActivity(new Intent(this,
                    com.android.calendar.countdown.compose.ComposeCountdownActivity.class));
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

    private void setMainPane(
            FragmentTransaction ft, int viewId, int viewType, long timeMillis, boolean force) {
        if (mOnSaveInstanceStateCalled) {
            return;
        }
        if (!force && mCurrentView == viewType) {
            return;
        }

        // Remove this when transition to and from month view looks fine.
        boolean doTransition = viewType != ViewType.MONTH && mCurrentView != ViewType.MONTH;
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
        }
        // The back callback must stop swallowing back once the app would exit, so that Android can
        // play the predictive "back to the launcher" animation.
        updateBackCallbackEnabled();
        // Create new fragment
        Fragment frag = null;
        Fragment secFrag = null;
        switch (viewType) {
            case ViewType.AGENDA:
                mNavigationView.getMenu().findItem(R.id.agenda_menu_item).setChecked(true);
                frag = new AgendaFragment(timeMillis, false);
                if (mIsTabletConfig) {
                    mToolbar.setTitle(R.string.agenda_view);
                }
                break;
            case ViewType.DAY:
                mNavigationView.getMenu().findItem(R.id.day_menu_item).setChecked(true);
                frag = new DayFragment(timeMillis, 1);
                if (mIsTabletConfig) {
                    mToolbar.setTitle(R.string.day_view);
                }
                break;
            case ViewType.MONTH:
                mNavigationView.getMenu().findItem(R.id.month_menu_item).setChecked(true);
                frag = new MonthByWeekFragment(timeMillis, false);
                if (mShowAgendaWithMonth) {
                    secFrag = new AgendaFragment(timeMillis, false);
                }
                if (mIsTabletConfig) {
                    mToolbar.setTitle(R.string.month_view);
                }
                break;
            case ViewType.WEEK:
            default:
                mNavigationView.getMenu().findItem(R.id.week_menu_item).setChecked(true);
                frag = new DayFragment(timeMillis, Utils.getDaysPerWeek(this));
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

        if (doTransition) {
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        }

        ft.replace(viewId, frag);
        if (mShowAgendaWithMonth) {

            // Show/hide secondary fragment

            if (secFrag != null) {
                ft.replace(R.id.secondary_pane, secFrag);
                mSecondaryPane.setVisibility(View.VISIBLE);
            } else {
                mSecondaryPane.setVisibility(View.GONE);
                Fragment f = fragmentManager.findFragmentById(R.id.secondary_pane);
                if (f != null) {
                    ft.remove(f);
                }
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

                long extras = CalendarController.EXTRA_GOTO_TIME | CalendarController.EXTRA_GOTO_DATE;
                mController.sendEvent(this, EventType.GO_TO, selectedTime, null, selectedTime, -1, ViewType.CURRENT, extras, null, null);
            }
        };

        CalendarConstraints calendarConstraints = new CalendarConstraints.Builder()
                .setFirstDayOfWeek(Utils.getFirstDayOfWeekAsCalendar(this))
                .build();

        MaterialDatePicker<Long> datePickerDialog = MaterialDatePicker.Builder.datePicker()
                .setSelection(Calendar.getInstance().getTimeInMillis())
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
