/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The rule that decides how the drawer moves between the calendar views.
 *
 * The all-Compose calendar keeps a stack of at most two screens: the landing screen - the one the
 * launcher opened, which is the view named under "Default view" in the settings - and the screen the
 * user is on now. The rule is needed because two different things used to go wrong.
 *
 * Every drawer choice started a screen and left the previous one under it, so the stack grew with
 * the user's browsing and back walked a trail of views instead of returning to the view the app
 * opens on. And choosing the month from any other view finished the screen first, which left the
 * month alone in the stack: back then went to the launcher instead of to the default view.
 *
 * The rule is a pure function because it is only about three facts - whether the screen that is
 * showing is one of the calendar views at all, whether it was opened over another screen, and
 * whether the view being asked for is that screen underneath - and is checked without a device in
 * CalendarViewStackTest.
 *
 * One rule holds for every kind of screen, not only the calendar views: the entry that names the
 * screen this one was opened over is the same move as back. Tapping the month in the year view's
 * drawer therefore finishes the year view and reveals the month, exactly as the back gesture does.
 * It used to be a push - the month was started *over* the year view - which left two screens stacked
 * and played the entry slide instead of the return one.
 */
package com.android.calendar.composeui

/** What a request for another calendar view does to the screen that is showing. */
enum class CalendarViewStep {
    /**
     * The view opens over this screen, and this screen stays underneath it.
     *
     * This is what a screen asks for when the view it names is not the one it was opened over: the
     * landing screen, which has nothing underneath it, and a tool screen - the year view, the agenda,
     * the date calculator - which keeps its own place under whatever it opens.
     */
    PUSH,

    /**
     * This screen is finished and the view takes its place.
     *
     * This screen was itself opened over the landing one, and the stack must not grow past those
     * two: the landing screen stays underneath, and back from the new view returns to it. It is the
     * one step that both starts the requested screen and finishes this one, and it exists only
     * between the calendar views, which are peers of one another.
     */
    REPLACE,

    /**
     * This screen is finished and the view underneath it is revealed.
     *
     * The view asked for is the screen this one was opened over, so choosing it is a step back, not
     * a second copy of it - it is the back gesture made from the menu, and plays the same return
     * slide. This holds for a tool screen exactly as it does for a calendar view: the year view's
     * month entry reveals the month it was opened from.
     */
    REVEAL,
}

/** True when the step finishes the screen that is showing. */
val CalendarViewStep.finishesCurrentScreen: Boolean
    get() = this != CalendarViewStep.PUSH

/** True when the step opens the requested screen. */
val CalendarViewStep.startsScreen: Boolean
    get() = this != CalendarViewStep.REVEAL

/**
 * The step to take when another view is opened from the screen that is showing.
 *
 * @param isCalendarView true when the screen showing is itself one of the drawer's calendar views -
 *   the day, three day, week or month screen. Those are peers of the view being asked for, so a
 *   choice that is neither a step back nor the landing screen's push replaces it.
 * @param hasOpener true when the screen was opened over another one - it carries
 *   [ComposeCalendarViewActivity.EXTRA_RETURN_TO_OPENER] - and so is not the landing screen.
 * @param targetIsOpener true when the view being asked for is the screen this one was opened over,
 *   by class; see [ComposeCalendarViewActivity.EXTRA_OPENER_CLASS].
 */
fun calendarViewStep(
    isCalendarView: Boolean,
    hasOpener: Boolean,
    targetIsOpener: Boolean,
): CalendarViewStep = when {
    // Asking for the screen underneath is the same move as back, made from the menu. Checked first,
    // and whatever kind of screen this is: a tool screen's entry for the month it was opened from is
    // a step back too, and pushing the month over the tool screen was the stacked transition.
    hasOpener && targetIsOpener -> CalendarViewStep.REVEAL
    // The landing screen has nothing underneath it, so it can only push the view over itself. And a
    // tool screen is not a peer of the calendar views: what it opens goes on top of it.
    !isCalendarView || !hasOpener -> CalendarViewStep.PUSH
    // Any other view replaces this one, so the landing screen stays the bottom of the stack.
    else -> CalendarViewStep.REPLACE
}

/**
 * The view type the landing screen should hand over to, or null when it should stay where it is.
 *
 * The landing screen is the one the launcher opened, and the "Default view" setting names what it
 * should be. That setting is only read when the app is started, so a change made in the settings
 * while the app is running would otherwise take effect only after a relaunch - the user comes back
 * to the calendar and finds the view they had just replaced still on screen. This is the comparison
 * that notices the change on the way back.
 *
 * A screen that was opened over another one is not the landing screen and must never re-route
 * itself, and a screen that already shows the configured view has nothing to do; both are what stop
 * the switch from repeating when the new screen resumes.
 *
 * @param configured the view type the settings now name as the default start view.
 * @param mine the view type the screen that is resuming shows.
 * @param hasOpener true when the screen was opened over another one.
 */
fun landingViewToShow(configured: Int, mine: Int, hasOpener: Boolean): Int? = when {
    // Only the landing screen re-routes: a screen opened over another one is a step into the
    // calendar, not the app's opening state.
    hasOpener -> null
    // Already there. This is also the check that makes the switch settle: the screen it starts has
    // the configured view as its own, so its resume finds the two equal and does nothing.
    configured == mine -> null
    else -> configured
}

/**
 * True when a screen behaves as the landing screen: its back leaves the app, and it is the state
 * the app opens in.
 *
 * A screen is the landing screen when it was not opened over another one, and also whenever it
 * shows the view the settings name as the default start view. The second half is what makes the
 * year view's month choice behave: with the month as the default, the month the year opens is where
 * the app begins, so back from it leaves the app rather than returning to the year view. A screen
 * opened over another one that shows something else is a step into the calendar, and its back steps
 * out of it.
 *
 * @param hasOpener true when the screen was opened over another one.
 * @param isDefaultView true when the screen shows the view the "Default view" setting names.
 */
fun isEffectivelyLanding(hasOpener: Boolean, isDefaultView: Boolean): Boolean =
    !hasOpener || isDefaultView
