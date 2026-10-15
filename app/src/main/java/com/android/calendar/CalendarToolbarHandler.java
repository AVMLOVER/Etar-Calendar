package com.android.calendar;

import android.view.View;
import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.android.calendar.LunarUtils;
import com.android.calendar.calendarcommon2.Time;

import java.util.Formatter;
import java.util.Locale;

import ws.xsoh.etar.R;

/**
 * Created by xsoh64 on 7/21/15.
 */
public class CalendarToolbarHandler {

    private final LayoutInflater mInflater;
    /** The heading's second line in the app's own heading view, if it is there. */
    /** The heading's first line in the app's own heading view, if it is there. */
    /** The heading's two lines, kept so either can change without losing the other. */
    private String mHeadingTitle;
    private String mHeadingSubtitle;

    /** The colour the second line is drawn in, resolved once from the theme. */
    private final StringBuilder mStringBuilder;
    private final Formatter mFormatter;
    private AppCompatActivity mContext;
    private Toolbar mToolbar;
    private int mCurrentViewType;
    // The current selected event's time, used to calculate the date and day of the week
    // for the buttons.
    /** The toolbar's heading view, filled by refreshHeading. */
    private android.widget.TextView mTitleView;
    /** Resolved once, for the second line's colour. */
    private Integer mSubtitleColor;
    private long mMilliTime;
    private String mTimeZone;
    private long mTodayJulianDay;
    private Handler mMidnightHandler = null; // Used to run a time update every midnight

    private final Runnable mTimeUpdater = new Runnable() {
        @Override
        public void run() {
            refresh(mContext);
        }
    };


    public CalendarToolbarHandler(AppCompatActivity context, Toolbar toolbar, int defaultViewType) {
        mContext = context;
        mToolbar = toolbar;
        mCurrentViewType = defaultViewType;

        mMidnightHandler = new Handler();
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mStringBuilder = new StringBuilder(50);
        mFormatter = new Formatter(mStringBuilder, Locale.getDefault());

        refresh(mContext);
    }

    // Sets the time zone and today's Julian day to be used by the adapter.
    // Also, update the change and resets the midnight update thread.
    public void refresh(Context context) {
        mTimeZone = Utils.getTimeZone(context, mTimeUpdater);
        Time time = new Time(mTimeZone);
        long now = System.currentTimeMillis();
        time.set(now);
        mTodayJulianDay = Time.getJulianDay(now, time.getGmtOffset());
        updateTitle();
        setMidnightHandler();
    }

    public void setCurrentMainView(int viewType) {
        mCurrentViewType = viewType;
        updateTitle();
    }

    // Update the date that is displayed on buttons
    // Used when the user selects a new day/week/month to watch
    public void setTime(long time) {
        mMilliTime = time;
        updateTitle();
    }

    private void updateTitle() {
        switch (mCurrentViewType) {
            case CalendarController.ViewType.DAY:
                setSubtitle(buildDayOfWeek(mCurrentViewType));
                setTitle(buildShortDate());
                break;
            case CalendarController.ViewType.WEEK:
                if (Utils.getShowWeekNumber(mContext)) {
                    setSubtitle(buildWeekNum());
                } else {
                    setSubtitle("");
                }
                setTitle(buildShortMonthYear());
                break;
            case CalendarController.ViewType.MONTH:
                setSubtitle("");
                setTitle(buildMonthYearDate());
                break;
            case CalendarController.ViewType.AGENDA:
                setSubtitle(buildDayOfWeek(mCurrentViewType));
                setTitle(buildShortDate());
                break;
        }
    }

    /**
     * The heading's two lines, drawn as one text so their left edges line up.
     *
     * They were two TextViews stacked in the toolbar, and their left edges were aligned while their
     * glyphs were not: a view's edge sits at the start of the line box, which is not the same as the
     * start of the first glyph. The gap between the two is the font's left side bearing, and it
     * differs with the size - 18sp for the date against 13sp for the weekday - so the heading looked
     * indented against the line beneath it in every language.
     *
     * As one text they share an origin and cannot disagree. The second line is a span at a smaller
     * size rather than a second view, so the size difference is kept without the offset.
     */
    private void refreshHeading() {
        // Both lines in one view, the second as a smaller span.
        //
        // The toolbar's own title and subtitle were used for a while. They are two views, and the two
        // did not line up: each is inset by the same amount, but the first line's glyphs begin a little
        // further in than the second's, because a view's edge is the start of its line box and not the
        // start of its first glyph - the difference is the font's left side bearing, which varies with
        // the size. One view gives the lines one origin.
        //
        // They are separate lines rather than one line with the subtitle appended, so the subtitle can
        // keep its smaller size: a size span and auto-sizing cannot both be in force - auto-sizing
        // measures the whole text and chooses one size for it - and the heading needs auto-sizing to
        // fit the Chinese date on the row it shares with the icons.
        android.widget.TextView view = titleView();
        if (view == null) {
            mToolbar.setTitle(mHeadingTitle == null ? "" : mHeadingTitle);
            if (mHeadingSubtitle != null && !mHeadingSubtitle.isEmpty()) {
                mToolbar.setSubtitle(mHeadingSubtitle);
            }
            return;
        }

        String title = mHeadingTitle == null ? "" : mHeadingTitle;
        String subtitle = mHeadingSubtitle == null ? "" : mHeadingSubtitle;
        if (subtitle.isEmpty()) {
            view.setText(title);
            return;
        }

        if (mSubtitleColor == null) {
            mSubtitleColor = com.google.android.material.color.MaterialColors.getColor(
                    mToolbar,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    0xFF808080);
        }
        android.text.SpannableString text =
                new android.text.SpannableString(title + "\n" + subtitle);
        int start = title.length() + 1;
        text.setSpan(new android.text.style.ForegroundColorSpan(mSubtitleColor),
                start, text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // Fixed sizes per line, so the date reads as the heading and the line under it as the note it is.
        //
        // Auto-sizing is off: it picks one size for the whole view, which would make the two lines
        // the same size and shrink the date to match the longer line. With the sizes fixed, fitting
        // is the layout's job - the view is capped and the subtitle is small enough to fit inside
        // that cap in every language this is used in.
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                mToolbar.getResources().getDimension(R.dimen.m3e_toolbar_title_size));
        text.setSpan(new android.text.style.AbsoluteSizeSpan(
                        (int) mToolbar.getResources().getDimension(R.dimen.m3e_toolbar_subtitle_size)),
                start, text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(text);
    }

    /** The toolbar's heading view, or null when the layout does not provide one. */
    private android.widget.TextView titleView() {
        if (mTitleView == null) {
            mTitleView = mToolbar.findViewById(R.id.m3e_title_text);
            if (mTitleView != null) {
                // Auto-sizing off: the two lines are different sizes, and the view can only scale all
                // of its text together.
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeWithDefaults(
                        mTitleView, androidx.core.widget.TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE);
            }
        }
        return mTitleView;
    }

    /** The heading's first line. */
    private void setTitle(String text) {
        mHeadingTitle = text;
        refreshHeading();
    }

    /**
     * The heading's second line.
     *
     * The ActionBar has a subtitle of its own, but it is laid out across the same row as the title,
     * so a long one takes the room the title needs. It is drawn as the heading's own second line
     * instead, at a smaller size.
     */
    private void setSubtitle(String text) {
        mHeadingSubtitle = text;
        refreshHeading();
    }


    // Sets a thread to run 1 second after midnight and update the current date
    // This is used to display correctly the date of yesterday/today/tomorrow
    private void setMidnightHandler() {
        mMidnightHandler.removeCallbacks(mTimeUpdater);
        // Set the time updater to run at 1 second after midnight
        long now = System.currentTimeMillis();
        Time time = new Time(mTimeZone);
        time.set(now);
        long runInMillis = (24 * 3600 - time.getHour() * 3600L - time.getMinute() * 60L -
                time.getSecond() + 1) * 1000;
        mMidnightHandler.postDelayed(mTimeUpdater, runInMillis);
    }

    // Builds a string with the day of the week and the word yesterday/today/tomorrow
    // before it if applicable.
    /**
     * The weekday line, with the lunar date appended when it is switched on.
     *
     * @param viewType the view asking, because the day view names the lunar month and the others do
     *                 not - see the comment on the format choice below.
     */
    private String buildDayOfWeek(int viewType) {

        Time t = new Time(mTimeZone);
        t.set(mMilliTime);
        long julianDay = Time.getJulianDay(mMilliTime, t.getGmtOffset());
        String dayOfWeek;
        mStringBuilder.setLength(0);

        if (julianDay == mTodayJulianDay) {
            dayOfWeek = mContext.getString(R.string.agenda_today,
                    buildShortWeekday());
        } else if (julianDay == mTodayJulianDay - 1) {
            dayOfWeek = mContext.getString(R.string.agenda_yesterday,
                    buildShortWeekday());
        } else if (julianDay == mTodayJulianDay + 1) {
            dayOfWeek = mContext.getString(R.string.agenda_tomorrow,
                    buildShortWeekday());
        } else {
            dayOfWeek = buildShortWeekday();
        }
        // Lunar date, when it is enabled, rides along on the same line as the weekday, e.g.
        // "今天，星期一，正月初六". Time.month is 0-based, which is what LunarUtils expects.
        //
        // The day view names the lunar month; the other screens that share this line do not. The three
        // day view draws its own lunar line per column, and the agenda is a list rather than a
        // calendar page, so a bare day number is enough there.
        if (LunarUtils.showLunar(mContext)) {
            int lunarFormat = viewType == CalendarController.ViewType.DAY
                    ? LunarUtils.FORMAT_LUNAR_MONTH_DAY | LunarUtils.FORMAT_ONE_FESTIVAL
                    : LunarUtils.FORMAT_LUNAR_SHORT | LunarUtils.FORMAT_ONE_FESTIVAL;
            String lunar = LunarUtils.get(mContext, t.getYear(), t.getMonth(), t.getDay(),
                    lunarFormat, false, null);
            if (!TextUtils.isEmpty(lunar)) {
                dayOfWeek = dayOfWeek + "，" + lunar;
            }
        }
        return dayOfWeek;
    }

    // Builds strings with different formats:
    // Full date: Month,day Year
    // Month year
    // Month day
    // Month
    // Week:  month day-day or month day - month day
    
    /**
     * The weekday, abbreviated.
     *
     * DateUtils offers only the full name, and the full name is most of the line by itself in German
     * - "Mittwoch" - before the lunar date is added to it. The pattern comes from the platform, so
     * each language abbreviates its own way: "Mi.", "Wed", "周三".
     */
    private String buildShortWeekday() {
        String pattern = android.text.format.DateFormat.getBestDateTimePattern(
                Locale.getDefault(), "EEE");
        return new java.text.SimpleDateFormat(pattern, Locale.getDefault())
                .format(new java.util.Date(mMilliTime));
    }

    private String buildFullDate() {
        mStringBuilder.setLength(0);
        String date = DateUtils.formatDateRange(mContext, mFormatter, mMilliTime, mMilliTime,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR, mTimeZone).toString();
        return date;
    }

    /**
     * The month and year, with the month abbreviated: "Okt. 2026" in German, "Oct 2026" in English.
     *
     * The week heading carries the week number underneath it, and the full month name on the same row
     * as the icons left no room for both.
     */
    private String buildShortMonthYear() {
        String pattern = android.text.format.DateFormat.getBestDateTimePattern(
                Locale.getDefault(), "MMMy");
        return new java.text.SimpleDateFormat(pattern, Locale.getDefault())
                .format(new java.util.Date(mMilliTime));
    }

    /**
     * The day's date with an abbreviated month: "14. Okt." in German, "Oct 14" in English.
     *
     * The heading shares its row with the search, today and overflow icons on a phone, and the full
     * month name did not fit: "14. Oktober 2026" was cut to "14. Oktober 2...". The abbreviation is
     * what each language uses for a date in running text, so it reads as a date rather than as a
     * truncation, and it leaves room for the weekday on the end.
     */
    private String buildShortDate() {
        String pattern = android.text.format.DateFormat.getBestDateTimePattern(
                Locale.getDefault(), "MMMd");
        return new java.text.SimpleDateFormat(pattern, Locale.getDefault())
                .format(new java.util.Date(mMilliTime));
    }

    private String buildMonthYearDate() {
        mStringBuilder.setLength(0);
        String date = DateUtils.formatDateRange(
                mContext,
                mFormatter,
                mMilliTime,
                mMilliTime,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_MONTH_DAY
                        | DateUtils.FORMAT_SHOW_YEAR, mTimeZone).toString();
        return date;
    }

    private String buildMonthDayDate() {
        mStringBuilder.setLength(0);
        String date = DateUtils.formatDateRange(mContext, mFormatter, mMilliTime, mMilliTime,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR, mTimeZone).toString();
        return date;
    }

    private String buildMonthDate() {
        mStringBuilder.setLength(0);
        String date = DateUtils.formatDateRange(
                mContext,
                mFormatter,
                mMilliTime,
                mMilliTime,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR
                        | DateUtils.FORMAT_NO_MONTH_DAY, mTimeZone).toString();
        return date;
    }

    private String buildWeekDate() {


        // Calculate the start of the week, taking into account the "first day of the week"
        // setting.

        Time t = new Time(mTimeZone);
        t.set(mMilliTime);
        int firstDayOfWeek = Utils.getFirstDayOfWeek(mContext);
        int dayOfWeek = t.getWeekDay();
        int diff = dayOfWeek - firstDayOfWeek;
        if (diff != 0) {
            if (diff < 0) {
                diff += 7;
            }
            t.setDay(t.getDay() - diff);
            t.normalize();
        }

        long weekStartTime = t.toMillis();
        // The end of the week is 6 days after the start of the week
        long weekEndTime = weekStartTime + DateUtils.WEEK_IN_MILLIS - DateUtils.DAY_IN_MILLIS;

        // If week start and end is in 2 different months, use short months names
        Time t1 = new Time(mTimeZone);
        t.set(weekEndTime);
        int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR;
        if (t.getMonth() != t1.getMonth()) {
            flags |= DateUtils.FORMAT_ABBREV_MONTH;
        }

        mStringBuilder.setLength(0);
        String date = DateUtils.formatDateRange(mContext, mFormatter, weekStartTime,
                weekEndTime, flags, mTimeZone).toString();
        return date;
    }

    private String buildWeekNum() {
        int week = Utils.getWeekNumberFromTime(mMilliTime, mContext);
        return mContext.getResources().getQuantityString(R.plurals.weekN, week, week);
    }
}
