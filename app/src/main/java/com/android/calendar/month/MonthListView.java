/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.calendar.month;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.ListView;

import com.android.calendar.Utils;
import com.android.calendar.calendarcommon2.Time;

public class MonthListView extends ListView {

    private static final String TAG = "MonthListView";
    VelocityTracker mTracker;
    private static float mScale = 0;

    // These define the behavior of the fling. Below MIN_VELOCITY_FOR_FLING, do the system fling
    // behavior. Between MIN_VELOCITY_FOR_FLING and MULTIPLE_MONTH_VELOCITY_THRESHOLD, do one month
    // fling. Above MULTIPLE_MONTH_VELOCITY_THRESHOLD, do multiple month flings according to the
    // fling strength. When doing multiple month fling, the velocity is reduced by this threshold
    // to prevent moving from one month fling to 4 months and above flings.
    private static int MIN_VELOCITY_FOR_FLING = 1500;
    private static int MULTIPLE_MONTH_VELOCITY_THRESHOLD = 2000;
    private static int FLING_VELOCITY_DIVIDER = 500;
    private static int FLING_TIME = 1000;
    /**
     * How far sideways a drag has to go before it counts as a month swipe.
     *
     * In density independent pixels, and scaled with the rest of these in init(): the value is
     * compared against raw touch coordinates, so as a bare 150 it demanded about 55dp on a modern
     * display and the swipe felt like it hardly ever registered.
     */
    private static int LEFT_RIGHT_DISTANCE = 80;
    private static double LEFT_RIGHT_RATIO = 1.25;

    // disposable variable used for time calculations
    protected Time mTempTime;
    private long mDownActionTime;
    private float mLeftRightActionLeft;
    private float mLeftRightActionRight;
    private final Rect mFirstViewRect = new Rect();

    Context mListContext;

    // Updates the time zone when it changes
    private final Runnable mTimezoneUpdater = new Runnable() {
        @Override
        public void run() {
            if (mTempTime != null && mListContext != null) {
                mTempTime.setTimezone(Utils.getTimeZone(mListContext, mTimezoneUpdater));
            }
        }
    };

    public MonthListView(Context context) {
        super(context);
        init(context);
    }

    public MonthListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public MonthListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context c) {
        mListContext = c;
        // No overscroll bounce.
        //
        // The grid is bounded - the adapter covers 1970 to 2037 - so at either end the list used to
        // rubber-band past its own edge. Scrolled up that drags the weeks over the status bar, which
        // is not something a calendar should do; a clean stop at the boundary is what the content
        // actually is.
        setOverScrollMode(OVER_SCROLL_NEVER);
        mTracker  = VelocityTracker.obtain();
        mTempTime = new Time(Utils.getTimeZone(c,mTimezoneUpdater));
        if (mScale == 0) {
            mScale = c.getResources().getDisplayMetrics().density;
            if (mScale != 1) {
                MIN_VELOCITY_FOR_FLING *= mScale;
                MULTIPLE_MONTH_VELOCITY_THRESHOLD *= mScale;
                FLING_VELOCITY_DIVIDER *= mScale;
                LEFT_RIGHT_DISTANCE *= mScale;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return processEvent(ev) || super.onTouchEvent(ev);
    }

    /**
     * Stops the grid being dragged past its own first row.
     *
     * overscroll mode is not enough on its own: the custom fling below drives the list with
     * smoothScrollBy, and a negative offset there scrolls past the top regardless of the mode - which
     * is what pulled the weeks up over the status bar. A calendar has nothing above its first row, so
     * the position is clamped instead.
     */
    @Override
    public void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
        if (scrollY < 0) {
            setSelectionFromTop(0, 0);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return processEvent(ev) || super.onInterceptTouchEvent(ev);
    }

    private boolean processEvent (MotionEvent ev) {
        switch (ev.getAction() & MotionEvent.ACTION_MASK) {
            // Since doFling sends a cancel, make sure not to process it.
            case MotionEvent.ACTION_CANCEL:
                return false;
            // Start tracking movement velocity
            case MotionEvent.ACTION_DOWN:
                mTracker.clear();
                mDownActionTime = SystemClock.uptimeMillis();
                mLeftRightActionLeft =  ev.getX();
                mLeftRightActionRight = ev.getY();
                break;
            // Accumulate velocity and do a custom fling when above threshold
            // and look for left/right swipes as well.
            case MotionEvent.ACTION_UP:
                // Check for the fling.
                mTracker.addMovement(ev);
                mTracker.computeCurrentVelocity(1000);    // in pixels per second
                float vel =  mTracker.getYVelocity ();
                if (Math.abs(vel) > MIN_VELOCITY_FOR_FLING) {
                    doFling(vel);
                    return true;
                }
                // Check for the left/right swipe.
                float leftRightSwipe = mLeftRightActionLeft - ev.getX();
                float leftRightRatio = Math.abs(leftRightSwipe) / Math.abs(mLeftRightActionRight - ev.getY());
                if( Math.abs(leftRightSwipe) > LEFT_RIGHT_DISTANCE && leftRightRatio > LEFT_RIGHT_RATIO ) {
                    if( leftRightSwipe > 0 ) {
                        doLeftRight(1);
                    } else {
                        doLeftRight(-1);
                    }
                    return true;
                }
                break;
            default:
                 mTracker.addMovement(ev);
                 break;
        }
        return false;
    }

    // Do a "snap to start of month" fling
    private void doFling(float velocityY) {

        // Stop the list-view movement and take over
        MotionEvent cancelEvent = MotionEvent.obtain(mDownActionTime,  SystemClock.uptimeMillis(),
                MotionEvent.ACTION_CANCEL, 0, 0, 0);
        onTouchEvent(cancelEvent);

        // Below the threshold, fling one month. Above the threshold , fling
        // according to the speed of the fling.
        int monthsToJump;
        if (Math.abs(velocityY) < MULTIPLE_MONTH_VELOCITY_THRESHOLD) {
            if (velocityY < 0) {
                monthsToJump = 1;
            } else {
                // value here is zero and not -1 since by the time the fling is
                // detected the list moved back one month.
                monthsToJump = 0;
            }
        } else {
            if (velocityY < 0) {
                monthsToJump = 1 - (int) ((velocityY + MULTIPLE_MONTH_VELOCITY_THRESHOLD)
                        / FLING_VELOCITY_DIVIDER);
            } else {
                monthsToJump = -(int) ((velocityY - MULTIPLE_MONTH_VELOCITY_THRESHOLD)
                        / FLING_VELOCITY_DIVIDER);
            }
        }

        // Get the day at the top right corner
        int day = getUpperRightJulianDay();
        // Get the day of the first day of the next/previous month
        // (according to scroll direction)
        mTempTime.setJulianDay(day);
        mTempTime.setDay(1);
        mTempTime.setMonth(mTempTime.getMonth() + monthsToJump);
        long timeInMillis = mTempTime.normalize();
        // Since each view is 7 days, round the target day up to make sure the
        // scroll will be  at least one view.
        int scrollToDay = Time.getJulianDay(timeInMillis, mTempTime.getGmtOffset())
                + ((monthsToJump > 0) ? 6 : 0);

        // Since all views have the same height, scroll by pixels instead of
        // "to position".
        // Compensate for the top view offset from the top.
        View firstView = getChildAt(0);
        int firstViewHeight = firstView.getHeight();
        // Get visible part length
        firstView.getLocalVisibleRect(mFirstViewRect);
        int topViewVisiblePart = mFirstViewRect.bottom - mFirstViewRect.top;
        int viewsToFling = (scrollToDay - day) / 7 - ((monthsToJump <= 0) ? 1 : 0);
        int offset = (viewsToFling > 0) ? -(firstViewHeight - topViewVisiblePart
                + SimpleDayPickerFragment.LIST_TOP_OFFSET) : (topViewVisiblePart
                - SimpleDayPickerFragment.LIST_TOP_OFFSET);
        // Fling
        smoothScrollBy(viewsToFling * firstViewHeight + offset, FLING_TIME);
    }

    // Do a "snap to start of month" fling
    // Left = +1
    // Right = -1
    private void doLeftRight(int monthsToJump) {

        // Stop the list-view movement and take over
        MotionEvent cancelEvent = MotionEvent.obtain(mDownActionTime,  SystemClock.uptimeMillis(),
                MotionEvent.ACTION_CANCEL, 0, 0, 0);
        onTouchEvent(cancelEvent);

        // Get the day at the top right corner
        int day = getUpperRightJulianDay();
        if (day < 0) {
            return;
        }

        // Target: the first day of the month one step away.
        //
        // Done with Calendar rather than by adding to Time.getMonth(): that value is zero based, so
        // adding one to December (11) gives 12, and normalize() then rolls the *year* forward while
        // the month it lands on is January of the year after what the caller asked for. Stepping from
        // December therefore used to land two years on, and the same arithmetic drifted for any other
        // month boundary.
        mTempTime.setJulianDay(day + 6);
        java.util.Calendar target = java.util.Calendar.getInstance(
                java.util.TimeZone.getTimeZone(mTempTime.getTimezone()));
        target.clear();
        target.set(mTempTime.getYear(), mTempTime.getMonth(), 1);
        target.add(java.util.Calendar.MONTH, monthsToJump);

        mTempTime.set(target.getTimeInMillis());
        mTempTime.setDay(1);
        mTempTime.normalize();
        // The day of the first of that month, plus its hour, so the comparison below sits on the same
        // footing as the "day" it is subtracted from.
        int targetJulianDay = Time.getJulianDay(mTempTime.toMillis(), mTempTime.getGmtOffset());
        // Since each view is 7 days, round the target day up to make sure the
        // scroll will be  at least one view.
        int scrollToDay = targetJulianDay + ((monthsToJump > 0) ? 6 : 0);

        // Since all views have the same height, scroll by pixels instead of
        // "to position".
        // Compensate for the top view offset from the top.
        View firstView = getChildAt(0);
        if (firstView == null || firstView.getHeight() == 0) {
            return;
        }
        int firstViewHeight = firstView.getHeight();
        // Get visible part length
        firstView.getLocalVisibleRect(mFirstViewRect);
        int topViewVisiblePart = mFirstViewRect.bottom - mFirstViewRect.top;
        int viewsToFling = (scrollToDay - day) / 7 - ((monthsToJump <= 0) ? 1 : 0);
        int offset = (viewsToFling > 0) ? -(firstViewHeight - topViewVisiblePart
                + SimpleDayPickerFragment.LIST_TOP_OFFSET) : (topViewVisiblePart
                - SimpleDayPickerFragment.LIST_TOP_OFFSET);
        // Fling
        smoothScrollBy(viewsToFling * firstViewHeight + offset, FLING_TIME);
    }

    // Returns the julian day of the day in the upper right corner
    private int getUpperRightJulianDay() {
        SimpleWeekView child = (SimpleWeekView) getChildAt(0);
        if (child == null) {
            return -1;
        }
        return child.getFirstJulianDay() + SimpleDayPickerFragment.DAYS_PER_WEEK - 1;
    }
}
