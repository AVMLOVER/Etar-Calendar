/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The month widget's cell ids, in reading order.
 *
 * The grid is a fixed six weeks of seven cells (see month_widget.xml), so every cell - its number,
 * today's circle, its two event bars and the cell itself for the tap - is a view the provider can
 * address by position. Keeping the ids in one generated-looking table is what makes that readable.
 */
package com.android.calendar.widget

import ws.xsoh.etar.R

internal object MonthWidgetIds {

    /** The six week rows, so a month that spans five of them can hide the last. */
    val rows = intArrayOf(R.id.month_widget_row0, R.id.month_widget_row1, R.id.month_widget_row2, R.id.month_widget_row3, R.id.month_widget_row4, R.id.month_widget_row5)

    /** cells, in reading order. */
    val cells = intArrayOf(
        R.id.month_widget_cell_0, R.id.month_widget_cell_1, R.id.month_widget_cell_2, R.id.month_widget_cell_3, R.id.month_widget_cell_4, R.id.month_widget_cell_5, R.id.month_widget_cell_6, R.id.month_widget_cell_7, R.id.month_widget_cell_8, R.id.month_widget_cell_9, R.id.month_widget_cell_10, R.id.month_widget_cell_11, R.id.month_widget_cell_12, R.id.month_widget_cell_13, R.id.month_widget_cell_14, R.id.month_widget_cell_15, R.id.month_widget_cell_16, R.id.month_widget_cell_17, R.id.month_widget_cell_18, R.id.month_widget_cell_19, R.id.month_widget_cell_20, R.id.month_widget_cell_21, R.id.month_widget_cell_22, R.id.month_widget_cell_23, R.id.month_widget_cell_24, R.id.month_widget_cell_25, R.id.month_widget_cell_26, R.id.month_widget_cell_27, R.id.month_widget_cell_28, R.id.month_widget_cell_29, R.id.month_widget_cell_30, R.id.month_widget_cell_31, R.id.month_widget_cell_32, R.id.month_widget_cell_33, R.id.month_widget_cell_34, R.id.month_widget_cell_35, R.id.month_widget_cell_36, R.id.month_widget_cell_37, R.id.month_widget_cell_38, R.id.month_widget_cell_39, R.id.month_widget_cell_40, R.id.month_widget_cell_41,
    )

    /** days, in reading order. */
    val days = intArrayOf(
        R.id.month_widget_day_0, R.id.month_widget_day_1, R.id.month_widget_day_2, R.id.month_widget_day_3, R.id.month_widget_day_4, R.id.month_widget_day_5, R.id.month_widget_day_6, R.id.month_widget_day_7, R.id.month_widget_day_8, R.id.month_widget_day_9, R.id.month_widget_day_10, R.id.month_widget_day_11, R.id.month_widget_day_12, R.id.month_widget_day_13, R.id.month_widget_day_14, R.id.month_widget_day_15, R.id.month_widget_day_16, R.id.month_widget_day_17, R.id.month_widget_day_18, R.id.month_widget_day_19, R.id.month_widget_day_20, R.id.month_widget_day_21, R.id.month_widget_day_22, R.id.month_widget_day_23, R.id.month_widget_day_24, R.id.month_widget_day_25, R.id.month_widget_day_26, R.id.month_widget_day_27, R.id.month_widget_day_28, R.id.month_widget_day_29, R.id.month_widget_day_30, R.id.month_widget_day_31, R.id.month_widget_day_32, R.id.month_widget_day_33, R.id.month_widget_day_34, R.id.month_widget_day_35, R.id.month_widget_day_36, R.id.month_widget_day_37, R.id.month_widget_day_38, R.id.month_widget_day_39, R.id.month_widget_day_40, R.id.month_widget_day_41,
    )

    /** circles, in reading order. */
    val circles = intArrayOf(
        R.id.month_widget_circle_0, R.id.month_widget_circle_1, R.id.month_widget_circle_2, R.id.month_widget_circle_3, R.id.month_widget_circle_4, R.id.month_widget_circle_5, R.id.month_widget_circle_6, R.id.month_widget_circle_7, R.id.month_widget_circle_8, R.id.month_widget_circle_9, R.id.month_widget_circle_10, R.id.month_widget_circle_11, R.id.month_widget_circle_12, R.id.month_widget_circle_13, R.id.month_widget_circle_14, R.id.month_widget_circle_15, R.id.month_widget_circle_16, R.id.month_widget_circle_17, R.id.month_widget_circle_18, R.id.month_widget_circle_19, R.id.month_widget_circle_20, R.id.month_widget_circle_21, R.id.month_widget_circle_22, R.id.month_widget_circle_23, R.id.month_widget_circle_24, R.id.month_widget_circle_25, R.id.month_widget_circle_26, R.id.month_widget_circle_27, R.id.month_widget_circle_28, R.id.month_widget_circle_29, R.id.month_widget_circle_30, R.id.month_widget_circle_31, R.id.month_widget_circle_32, R.id.month_widget_circle_33, R.id.month_widget_circle_34, R.id.month_widget_circle_35, R.id.month_widget_circle_36, R.id.month_widget_circle_37, R.id.month_widget_circle_38, R.id.month_widget_circle_39, R.id.month_widget_circle_40, R.id.month_widget_circle_41,
    )

    /** barFirst, in reading order. */
    val barFirst = intArrayOf(
        R.id.month_widget_bar0_0, R.id.month_widget_bar0_1, R.id.month_widget_bar0_2, R.id.month_widget_bar0_3, R.id.month_widget_bar0_4, R.id.month_widget_bar0_5, R.id.month_widget_bar0_6, R.id.month_widget_bar0_7, R.id.month_widget_bar0_8, R.id.month_widget_bar0_9, R.id.month_widget_bar0_10, R.id.month_widget_bar0_11, R.id.month_widget_bar0_12, R.id.month_widget_bar0_13, R.id.month_widget_bar0_14, R.id.month_widget_bar0_15, R.id.month_widget_bar0_16, R.id.month_widget_bar0_17, R.id.month_widget_bar0_18, R.id.month_widget_bar0_19, R.id.month_widget_bar0_20, R.id.month_widget_bar0_21, R.id.month_widget_bar0_22, R.id.month_widget_bar0_23, R.id.month_widget_bar0_24, R.id.month_widget_bar0_25, R.id.month_widget_bar0_26, R.id.month_widget_bar0_27, R.id.month_widget_bar0_28, R.id.month_widget_bar0_29, R.id.month_widget_bar0_30, R.id.month_widget_bar0_31, R.id.month_widget_bar0_32, R.id.month_widget_bar0_33, R.id.month_widget_bar0_34, R.id.month_widget_bar0_35, R.id.month_widget_bar0_36, R.id.month_widget_bar0_37, R.id.month_widget_bar0_38, R.id.month_widget_bar0_39, R.id.month_widget_bar0_40, R.id.month_widget_bar0_41,
    )

    /** barSecond, in reading order. */
    val barSecond = intArrayOf(
        R.id.month_widget_bar1_0, R.id.month_widget_bar1_1, R.id.month_widget_bar1_2, R.id.month_widget_bar1_3, R.id.month_widget_bar1_4, R.id.month_widget_bar1_5, R.id.month_widget_bar1_6, R.id.month_widget_bar1_7, R.id.month_widget_bar1_8, R.id.month_widget_bar1_9, R.id.month_widget_bar1_10, R.id.month_widget_bar1_11, R.id.month_widget_bar1_12, R.id.month_widget_bar1_13, R.id.month_widget_bar1_14, R.id.month_widget_bar1_15, R.id.month_widget_bar1_16, R.id.month_widget_bar1_17, R.id.month_widget_bar1_18, R.id.month_widget_bar1_19, R.id.month_widget_bar1_20, R.id.month_widget_bar1_21, R.id.month_widget_bar1_22, R.id.month_widget_bar1_23, R.id.month_widget_bar1_24, R.id.month_widget_bar1_25, R.id.month_widget_bar1_26, R.id.month_widget_bar1_27, R.id.month_widget_bar1_28, R.id.month_widget_bar1_29, R.id.month_widget_bar1_30, R.id.month_widget_bar1_31, R.id.month_widget_bar1_32, R.id.month_widget_bar1_33, R.id.month_widget_bar1_34, R.id.month_widget_bar1_35, R.id.month_widget_bar1_36, R.id.month_widget_bar1_37, R.id.month_widget_bar1_38, R.id.month_widget_bar1_39, R.id.month_widget_bar1_40, R.id.month_widget_bar1_41,
    )
}
