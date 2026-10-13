/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.calendar;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Lunar (农历) information for a given day.
 *
 * <p>Originally this class only read its data from the Qualcomm vendor provider
 * {@code content://com.qualcomm.qti.lunarinfo/...}, so it produced nothing at all on any device
 * that does not ship that provider — which is nearly all of them, including every
 * LineageOS/AOSP build. The data source has therefore been replaced by the built-in
 * {@link LunarCalendar}, which computes the Chinese lunisolar date locally from a compact
 * table covering 1900-2100.
 *
 * <p>The public API is unchanged so that the month view ({@code MonthWeekEventsView}) and the
 * week/day view ({@code DayView}) keep working without modification.
 */
public class LunarUtils {
    private static final String TAG = "LunarUtils";

    // The flags used for get the lunar info.
    public static final int FORMAT_LUNAR_LONG = 0x00001;
    public static final int FORMAT_LUNAR_SHORT = 0x00002;
    public static final int FORMAT_ONE_FESTIVAL = 0x00004;
    public static final int FORMAT_MULTI_FESTIVAL = 0x00008;
    public static final int FORMAT_ANIMAL = 0x00010;

    /**
     * Month and day, without the year: {@code 正月初一}, {@code 冬月廿一}, {@code 腊月廿二}.
     *
     * FORMAT_LUNAR_SHORT drops the month on every day but the 1st, so a date line reads as a bare
     * "二十" wherever the month is not otherwise shown. FORMAT_LUNAR_LONG carries the year too, which
     * is more than a heading needs. This is the middle ground, and it is what the day view uses.
     */
    public static final int FORMAT_LUNAR_MONTH_DAY = 0x00020;

    /** Settings key backing the "show lunar date" switch in General preferences. */
    public static final String PREF_SHOW_LUNAR = "pref_show_lunar";

    /**
     * Default of {@link #PREF_SHOW_LUNAR}: on in Chinese-script locales, off everywhere else.
     *
     * The lunar line is only meaningful to readers of Chinese, and it is also what drives the
     * year view's 初一 underlines, so non-Chinese locales start with both switched off.
     * Traditional Chinese counts as Chinese here.
     */
    public static boolean defaultShowLunar() {
        return "zh".equals(java.util.Locale.getDefault().getLanguage());
    }

    private static final String INFO_SEPARATE = " ";
    private static final String MORE_FESTIVAL_SUFFIX = "*";

    /** Upper bound of the on-demand cache; cleared wholesale when it grows past this. */
    private static final int MAX_CACHE_ENTRIES = 2048;

    private static HashMap<String, LunarInfo> sLunarInfos = new HashMap<String, LunarInfo>();

    /**
     * Whether the lunar info should be drawn. Controlled by the "show lunar date" switch in
     * General preferences. It used to be hard-wired to the zh-CN locale, which is the other
     * reason the feature never appeared.
     */
    public static boolean showLunar(Context context) {
        if (context == null) {
            return false;
        }
        return Utils.getSharedPreference(context, PREF_SHOW_LUNAR, defaultShowLunar());
    }

    /**
     * Used to clear the saved info.
     */
    public static void clearInfo() {
        Log.i(TAG, "Clear all the saved info.");
        sLunarInfos.clear();
    }

    /**
     * Used to get the lunar, festival and animal info of the date.
     *
     * <p>The value is computed on demand and cached, so there is no longer any need to pre-load
     * a date range first.
     *
     * @param month Gregorian month, <b>0-based</b>, as returned by {@code android.text.format.Time}.
     * @param format Format which info need append to the result.
     *     The format {@link #FORMAT_LUNAR_LONG} and {@link #FORMAT_LUNAR_SHORT},
     *     {@link #FORMAT_ONE_FESTIVAL} and {@link #FORMAT_MULTI_FESTIVAL} could not
     *     selected at once.
     * @param showLunarBeforeFestival If the festival is exist for the date, if need append the
     *     lunar info before the festival info.
     * @param result [out] The result will be saved in this list as your given format.
     * @return The result as string for your given format.
     */
    public static String get(Context context, int year, int month, int day, int format,
            boolean showLunarBeforeFestival, ArrayList<String> result) {
        if (context == null || format < FORMAT_LUNAR_LONG) return null;

        String key = getKey(year, month, day);
        LunarInfo info = sLunarInfos.get(key);
        if (info == null) {
            info = buildLocalInfo(year, month, day);
            if (info == null) {
                Log.d(TAG, "Couldn't get the lunar info for " + key);
                return null;
            }
            if (sLunarInfos.size() >= MAX_CACHE_ENTRIES) {
                sLunarInfos.clear();
            }
            sLunarInfos.put(key, info);
        }

        return buildInfo(info, format, showLunarBeforeFestival, result);
    }

    private static String getKey(int year, int month, int day) {
        return year + "-" + month + "-" + day;
    }

    /** Computes the lunar info locally. Returns null when the date is outside 1900-2100. */
    private static LunarInfo buildLocalInfo(int year, int month, int day) {
        LunarCalendar.Lunar lunar = LunarCalendar.fromSolar(year, month, day);
        if (lunar == null) {
            return null;
        }

        LunarInfo info = new LunarInfo();
        info._label_short = LunarCalendar.shortLabel(lunar);
        info._label_long = LunarCalendar.longLabel(lunar);
        info._label_month_day = LunarCalendar.monthDayLabel(lunar);
        info._animal = LunarCalendar.animal(lunar.year);

        String[] festivals = LunarCalendar.festivals(year, month, day, lunar, 4);
        info._festival1 = festivals.length > 0 ? festivals[0] : null;
        info._festival2 = festivals.length > 1 ? festivals[1] : null;
        info._festival3 = festivals.length > 2 ? festivals[2] : null;
        info._festival4 = festivals.length > 3 ? festivals[3] : null;
        return info;
    }

    private static String buildInfo(LunarInfo info, int format, boolean showLunarBeforeFestival,
            ArrayList<String> list) {
        if (info == null || format < FORMAT_LUNAR_LONG) return null;

        StringBuilder result = new StringBuilder();

        if (showLunarBeforeFestival || TextUtils.isEmpty(info._festival1)) {
            // The format should not support long and short at one time.
            if ((format & FORMAT_LUNAR_LONG) == FORMAT_LUNAR_LONG) {
                appendInfo(result, info._label_long, list);
            } else if ((format & FORMAT_LUNAR_MONTH_DAY) == FORMAT_LUNAR_MONTH_DAY) {
                appendInfo(result, info._label_month_day, list);
            } else if ((format & FORMAT_LUNAR_SHORT) == FORMAT_LUNAR_SHORT) {
                appendInfo(result, info._label_short, list);
            }
        }

        // The format should not support only one festival and multiple festivals.
        if ((format & FORMAT_ONE_FESTIVAL) == FORMAT_ONE_FESTIVAL) {
            String festival = info._festival1;
            if (!TextUtils.isEmpty(info._festival2)) {
                festival = festival + MORE_FESTIVAL_SUFFIX;
            }
            appendInfo(result, festival, list);
        } else if ((format & FORMAT_MULTI_FESTIVAL) == FORMAT_MULTI_FESTIVAL) {
            appendInfo(result, info._festival1, list);
            appendInfo(result, info._festival2, list);
            appendInfo(result, info._festival3, list);
            appendInfo(result, info._festival4, list);
        }

        if ((format & FORMAT_ANIMAL) == FORMAT_ANIMAL) {
            appendInfo(result, info._animal, list);
        }

        return result.toString();
    }

    private static void appendInfo(StringBuilder builder, String info, ArrayList<String> list) {
        if (builder == null || TextUtils.isEmpty(info)) return;

        String prefix = builder.length() > 0 ? INFO_SEPARATE : "";
        builder.append(prefix).append(info);

        if (list != null) list.add(info);
    }

    /**
     * Kept for API compatibility with {@code CalendarViewAdapter}. Pre-loading is no longer
     * needed because {@link #get} computes and caches on demand, so the background work is a
     * no-op. It no longer touches the Qualcomm content provider.
     */
    public static class LunarInfoLoader extends AsyncTaskLoader<Void> {

        public LunarInfoLoader(Context context) {
            super(context);
        }

        public void load(int year, int month, int day) {
            reset();
            startLoading();
            forceLoad();
        }

        public void load(int year, int month) {
            reset();
            startLoading();
            forceLoad();
        }

        public void load(int from_year, int from_month, int from_day,
                int to_year, int to_month, int to_day) {
            reset();
            startLoading();
            forceLoad();
        }

        @Override
        public Void loadInBackground() {
            return null;
        }
    }

    private static class LunarInfo {
        public String _label_long;
        public String _label_short;
        public String _label_month_day;
        public String _animal;
        public String _festival1;
        public String _festival2;
        public String _festival3;
        public String _festival4;
    }
}
