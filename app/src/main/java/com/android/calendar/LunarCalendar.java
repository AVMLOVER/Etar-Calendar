/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Self-contained Chinese lunar calendar (农历) conversion.
 *
 * Deliberately free of any android.* / java.time dependency so that it can be compiled and
 * exhaustively tested on a desktop JVM. It only needs java.lang.
 *
 * The LUNAR_INFO table below was generated from ICU4J's com.ibm.icu.util.ChineseCalendar
 * (the same implementation Android ships as android.icu.util.ChineseCalendar) and the
 * decoding logic is verified day-by-day against that same reference for 1900-2100.
 *
 * Encoding of one LUNAR_INFO entry, identical to the widely used compact scheme:
 *   bit 16       : 1 -> the leap month has 30 days, 0 -> 29 days
 *   bits 15..4   : regular months 1..12, one bit each, 1 -> 30 days, 0 -> 29 days
 *   bits 3..0    : leap month number 1..12, 0 -> no leap month in that year
 */
package com.android.calendar;

public final class LunarCalendar {

    private LunarCalendar() {
    }

    public static final int MIN_YEAR = 1900;
    public static final int MAX_YEAR = 2100;

    /** Solar date that corresponds to lunar 1900-01-01. */
    private static final int BASE_SOLAR_YEAR = 1900;
    private static final int BASE_SOLAR_MONTH = 1;
    private static final int BASE_SOLAR_DAY = 31;
    private static final long BASE_EPOCH_DAY =
            daysFromCivil(BASE_SOLAR_YEAR, BASE_SOLAR_MONTH, BASE_SOLAR_DAY);

    private static final int[] LUNAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0,  // 1900-1907
            0x09ad0, 0x055d2, 0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540,  // 1908-1915
            0x0d6a0, 0x0ada2, 0x095b0, 0x14977, 0x04970, 0x0a4b0, 0x0b4b5, 0x06a50,  // 1916-1923
            0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, 0x06566, 0x0d4a0,  // 1924-1931
            0x0ea50, 0x16a95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,  // 1932-1939
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2,  // 1940-1947
            0x0a950, 0x0b557, 0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573,  // 1948-1955
            0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, 0x0aea6, 0x0ab50, 0x04b60, 0x0aae4,  // 1956-1963
            0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, 0x096d0, 0x04dd5,  // 1964-1971
            0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,  // 1972-1979
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46,  // 1980-1987
            0x0ab60, 0x09570, 0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58,  // 1988-1995
            0x05ac0, 0x0ab60, 0x096d5, 0x092e0, 0x0c960, 0x0d954, 0x0d4a0, 0x0da50,  // 1996-2003
            0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, 0x0a950, 0x0b4a0,  // 2004-2011
            0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,  // 2012-2019
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260,  // 2020-2027
            0x0ea65, 0x0d530, 0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0,  // 2028-2035
            0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, 0x0b5a0, 0x056d0, 0x055b2, 0x049b0,  // 2036-2043
            0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, 0x14b63, 0x09370,  // 2044-2051
            0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06aa0, 0x1a6c4, 0x0aae0,  // 2052-2059
            0x092e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0,  // 2060-2067
            0x0a6d0, 0x055d4, 0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50,  // 2068-2075
            0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, 0x0b273, 0x06930, 0x07337, 0x06aa0,  // 2076-2083
            0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, 0x0e968, 0x0d520,  // 2084-2091
            0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252,  // 2092-2099
            0x0d530,  // 2100-2100
    };

    private static final String[] MONTH_NAMES = {
            "正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊"
    };

    private static final String[] DAY_NAMES = {
            "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
            "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
            "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    };

    private static final String[] STEMS = {
            "甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"
    };

    private static final String[] BRANCHES = {
            "子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"
    };

    private static final String[] ANIMALS = {
            "鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪"
    };

    /** Lunar festivals, keyed by "month-day". Only regular (non-leap) months carry them. */
    private static final String[][] LUNAR_FESTIVALS = {
            {"1-1", "春节"},
            {"1-15", "元宵节"},
            {"2-2", "龙抬头"},
            {"5-5", "端午节"},
            {"7-7", "七夕"},
            {"7-15", "中元节"},
            {"8-15", "中秋节"},
            {"9-9", "重阳节"},
            {"12-8", "腊八节"},
            {"12-23", "小年"},
    };

    /** Fixed Gregorian festivals, keyed by "month-day" (month is 1-based). */
    private static final String[][] SOLAR_FESTIVALS = {
            {"1-1", "元旦"},
            {"2-14", "情人节"},
            {"3-8", "妇女节"},
            {"3-12", "植树节"},
            {"5-1", "劳动节"},
            {"6-1", "儿童节"},
            {"9-10", "教师节"},
            {"10-1", "国庆节"},
            {"12-25", "圣诞节"},
    };

    /** A resolved lunar date. */
    public static final class Lunar {
        /** Lunar year, e.g. 2025. */
        public int year;
        /** Lunar month 1..12. */
        public int month;
        /** Day of the lunar month, 1..30. */
        public int day;
        /** True when {@link #month} refers to the leap month of {@link #year}. */
        public boolean leap;
        /** Number of days in this lunar month, 29 or 30. */
        public int monthDays;

        @Override
        public String toString() {
            return year + "-" + month + "-" + day + (leap ? "(leap)" : "");
        }
    }

    /**
     * Converts a Gregorian date to its lunar counterpart.
     *
     * @param month0 Gregorian month, <b>0-based</b> (matches {@code android.text.format.Time}).
     * @return the lunar date, or {@code null} when outside {@link #MIN_YEAR}..{@link #MAX_YEAR}.
     */
    public static Lunar fromSolar(int year, int month0, int day) {
        Lunar lunar = new Lunar();
        if (!fromSolar(year, month0, day, lunar)) {
            return null;
        }
        return lunar;
    }

    /** Same as {@link #fromSolar(int, int, int)} but writes into {@code out}. */
    public static boolean fromSolar(int year, int month0, int day, Lunar out) {
        if (out == null) {
            return false;
        }
        long offset = daysFromCivil(year, month0 + 1, day) - BASE_EPOCH_DAY;
        if (offset < 0) {
            return false;
        }

        int lunarYear = lunarYearAt(offset);
        offset -= YEAR_START[lunarYear - MIN_YEAR];
        if (offset < 0 || offset >= yearDays(lunarYear)) {
            return false;
        }

        int index = 0;
        int length;
        while (true) {
            length = monthLengthAt(lunarYear, index);
            if (offset < length) {
                break;
            }
            offset -= length;
            index++;
        }

        out.year = lunarYear;
        out.month = monthAt(lunarYear, index);
        out.leap = isLeapAt(lunarYear, index);
        out.day = (int) offset + 1;
        out.monthDays = length;
        return true;
    }

    /**
     * The short label used inside a day cell: the day name, or the month name on the 1st.
     * Examples: {@code 初二}, {@code 八月}, {@code 闰四月}.
     */
    public static String shortLabel(Lunar lunar) {
        if (lunar == null || lunar.day < 1 || lunar.day > 30) {
            return "";
        }
        if (lunar.day == 1) {
            StringBuilder sb = new StringBuilder();
            if (lunar.leap) {
                sb.append('闰');
            }
            sb.append(MONTH_NAMES[lunar.month - 1]).append('月');
            return sb.toString();
        }
        return DAY_NAMES[lunar.day - 1];
    }

    /**
     * Month and day, without the year: {@code 正月初一}, {@code 冬月廿一}, {@code 腊月廿二}.
     *
     * The short label drops the month on every day but the 1st, which reads as a bare "二十" in a
     * place where the month is not otherwise on screen. The long label carries the year as well,
     * which is more than a date line needs. This is the middle ground.
     */
    public static String monthDayLabel(Lunar lunar) {
        if (lunar == null || lunar.day < 1 || lunar.day > 30) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (lunar.leap) {
            sb.append('闰');
        }
        sb.append(MONTH_NAMES[lunar.month - 1]).append('月');
        sb.append(DAY_NAMES[lunar.day - 1]);
        return sb.toString();
    }

    /** The long label, e.g. {@code 农历乙巳年八月初二}. */
    public static String longLabel(Lunar lunar) {        if (lunar == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder("农历");
        sb.append(ganZhi(lunar.year)).append('年');
        if (lunar.leap) {
            sb.append('闰');
        }
        sb.append(MONTH_NAMES[lunar.month - 1]).append('月');
        sb.append(DAY_NAMES[lunar.day - 1]);
        return sb.toString();
    }

    /** Heavenly-stem / earthly-branch name of a lunar year, e.g. {@code 乙巳}. */
    public static String ganZhi(int lunarYear) {
        int stem = Math.floorMod(lunarYear - 4, 10);
        int branch = Math.floorMod(lunarYear - 4, 12);
        return STEMS[stem] + BRANCHES[branch];
    }

    /** Chinese zodiac animal of a lunar year, e.g. {@code 蛇}. */
    public static String animal(int lunarYear) {
        return ANIMALS[Math.floorMod(lunarYear - 4, 12)];
    }

    /**
     * Festivals for a Gregorian date, lunar ones first. At most {@code max} entries.
     *
     * @param month0 Gregorian month, 0-based.
     */
    public static String[] festivals(int year, int month0, int day, Lunar lunar, int max) {
        String[] found = new String[Math.min(max, 4)];
        int n = 0;

        if (lunar != null && !lunar.leap) {
            String key = lunar.month + "-" + lunar.day;
            for (String[] f : LUNAR_FESTIVALS) {
                if (f[0].equals(key) && n < found.length) {
                    found[n++] = f[1];
                }
            }
            // New Year's Eve is the last day of the last (regular) lunar month.
            if (lunar.month == 12 && lunar.day == lunar.monthDays && n < found.length) {
                found[n++] = "除夕";
            }
        }

        String solarKey = (month0 + 1) + "-" + day;
        for (String[] f : SOLAR_FESTIVALS) {
            if (f[0].equals(solarKey) && n < found.length) {
                found[n++] = f[1];
            }
        }

        String[] out = new String[n];
        System.arraycopy(found, 0, out, 0, n);
        return out;
    }

    // ------------------------------------------------------------------------------------
    // table decoding
    // ------------------------------------------------------------------------------------

    private static int info(int lunarYear) {
        return LUNAR_INFO[lunarYear - MIN_YEAR];
    }

    /** Leap month number of the year, or 0 when there is none. */
    public static int leapMonth(int lunarYear) {
        return info(lunarYear) & 0xf;
    }

    /**
     * Cumulative day offset of each lunar year's first month, indexed by {@code year - MIN_YEAR}.
     *
     * Declared after LUNAR_INFO because it is built from it. Without this the year lookup had to
     * walk forward from 1900 one year at a time - up to 125 iterations per call, which is what
     * made the year view slow, since it converts hundreds of days per screen.
     */
    private static final long[] YEAR_START = buildYearStarts();

    private static long[] buildYearStarts() {
        // One entry per lunar year MIN_YEAR..MAX_YEAR inclusive; the array must not run past the
        // end of LUNAR_INFO, which has exactly the same number of entries.
        int count = MAX_YEAR - MIN_YEAR + 1;
        long[] starts = new long[count];
        long sum = 0;
        for (int i = 0; i < count; i++) {
            starts[i] = sum;
            sum += yearDays(MIN_YEAR + i);
        }
        return starts;
    }

    /** Largest lunar year whose first day is at or before the given offset (~8 steps). */
    private static int lunarYearAt(long offset) {
        int lo = 0;
        int hi = YEAR_START.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (YEAR_START[mid] <= offset) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return MIN_YEAR + lo;
    }

    /** {@code out[dayOfYear]}: no lunar month starts on that day. */
    public static final byte MARK_NONE = 0;
    /** {@code out[dayOfYear]}: a regular lunar month starts there. */
    public static final byte MARK_MONTH_START = 1;
    /** {@code out[dayOfYear]}: 正月初一, the lunar new year day. */
    public static final byte MARK_NEW_YEAR = 2;

    /**
     * Marks every lunar month start that falls inside a Gregorian year.
     *
     * {@code out} is indexed by day of year (0 = January 1st). Leap months are left unmarked,
     * which is how the rest of the app treats them.
     *
     * This is the fast path for the year view: instead of converting all 365 days it walks the
     * ~13 months of the two lunar years that overlap the Gregorian year.
     */
    public static void monthStartMarks(int gregorianYear, byte[] out) {
        java.util.Arrays.fill(out, MARK_NONE);

        long jan1 = daysFromCivil(gregorianYear, 1, 1) - BASE_EPOCH_DAY;
        long dec31 = daysFromCivil(gregorianYear, 12, 31) - BASE_EPOCH_DAY;

        int year = lunarYearAt(jan1);
        int index = 0;
        long cursor = YEAR_START[year - MIN_YEAR];
        int guard = 0;

        while (cursor <= dec31 && guard++ < 40) {
            if (cursor >= jan1 && !isLeapAt(year, index)) {
                int dayOfYear = (int) (cursor - jan1);
                if (dayOfYear >= 0 && dayOfYear < out.length) {
                    out[dayOfYear] = monthAt(year, index) == 1
                            ? MARK_NEW_YEAR
                            : MARK_MONTH_START;
                }
            }
            cursor += monthLengthAt(year, index);
            index++;
            if (index >= monthCount(year)) {
                index = 0;
                year++;
                if (year > MAX_YEAR) {
                    break;
                }
            }
        }
    }

    /** Number of days of the regular month {@code month} (1..12) in the given lunar year. */
    public static int regularMonthDays(int lunarYear, int month) {
        return ((info(lunarYear) >> (16 - month)) & 1) != 0 ? 30 : 29;
    }

    /** Number of days of the leap month of the year, 29 or 30. */
    public static int leapMonthDays(int lunarYear) {
        return ((info(lunarYear) & 0x10000) != 0) ? 30 : 29;
    }

    /** Total number of days in a lunar year, 353..385. */
    public static int yearDays(int lunarYear) {
        int sum = 0;
        for (int m = 1; m <= 12; m++) {
            sum += regularMonthDays(lunarYear, m);
        }
        if (leapMonth(lunarYear) != 0) {
            sum += leapMonthDays(lunarYear);
        }
        return sum;
    }

    /** Number of months in a lunar year, 12 or 13. */
    private static int monthCount(int lunarYear) {
        return leapMonth(lunarYear) != 0 ? 13 : 12;
    }

    /** Lunar month number at a 0-based position in the year (leap month repeated). */
    private static int monthAt(int lunarYear, int index) {
        int leap = leapMonth(lunarYear);
        if (leap == 0) {
            return index + 1;
        }
        if (index < leap) {
            return index + 1;
        }
        if (index == leap) {
            return leap;
        }
        return index;
    }

    private static boolean isLeapAt(int lunarYear, int index) {
        int leap = leapMonth(lunarYear);
        return leap != 0 && index == leap;
    }

    private static int monthLengthAt(int lunarYear, int index) {
        if (index < 0 || index >= monthCount(lunarYear)) {
            return 29;
        }
        return isLeapAt(lunarYear, index)
                ? leapMonthDays(lunarYear)
                : regularMonthDays(lunarYear, monthAt(lunarYear, index));
    }

    // ------------------------------------------------------------------------------------
    // proleptic Gregorian day number (Howard Hinnant's days_from_civil)
    // ------------------------------------------------------------------------------------

    /** Days since 1970-01-01 for the given proleptic Gregorian date. {@code month} is 1-based. */
    static long daysFromCivil(int year, int month, int day) {
        long y = year - (month <= 2 ? 1 : 0);
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153 * (month + (month > 2 ? -3 : 9)) + 2) / 5 + day - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }
}
