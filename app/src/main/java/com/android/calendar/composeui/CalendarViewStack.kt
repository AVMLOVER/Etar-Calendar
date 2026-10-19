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
 */
package com.android.calendar.composeui

/** What a request for another calendar view does to the screen that is showing. */
enum class CalendarViewStep {
    /**
     * The view opens over this screen, and this screen stays underneath it.
     *
     * This is the landing screen (it is what back must return to before the app is left) and, as it
     * happens, every tool screen: the year view, the agenda and the rest have never been peers of
     * the calendar views, and what they open goes on top of them.
     */
    PUSH,

    /**
     * This screen is finished and the view takes its place.
     *
     * This screen was itself opened over the landing one, and the stack must not grow past those
     * two: the landing screen stays underneath, and back from the new view returns to it.
     */
    REPLACE,

    /**
     * This screen is finished and the view underneath it is revealed.
     *
     * The view asked for is the screen this one was opened over, so choosing it is a step back, not
     * a second copy of it. Starting it would put the same screen in the stack twice and re-intent
     * the landing one, which is how the drawer used to lose the screen back should return to.
     */
    REVEAL,
}

/** True when the step finishes the screen that is showing. */
val CalendarViewStep.finishesCurrentScreen: Boolean
    get() = this != CalendarViewStep.PUSH

/**
 * The step to take when another calendar view is opened from the screen that is showing.
 *
 * @param isCalendarView true when the screen showing is itself one of the drawer's calendar views -
 *   the day, three day, week or month screen. Those are peers of the view being asked for, so the
 *   choice is made without adding to the stack. A tool screen - the year view, the agenda, the to-do
 *   list - is not a peer: what it opens goes on top of it and back returns to it, as it always has.
 * @param hasOpener true when the screen was opened over another one - it carries
 *   [ComposeCalendarViewActivity.EXTRA_RETURN_TO_OPENER] - and so is not the landing screen.
 * @param targetIsOpener true when the view being asked for is the screen this one was opened over.
 */
fun calendarViewStep(
    isCalendarView: Boolean,
    hasOpener: Boolean,
    targetIsOpener: Boolean,
): CalendarViewStep = when {
    // The landing screen has nothing underneath it, so it can only push the view over itself. And a
    // tool screen is never a peer of the calendar views, whatever it was opened over.
    !isCalendarView || !hasOpener -> CalendarViewStep.PUSH
    // Asking for the screen underneath is the same move as back, made from the drawer.
    targetIsOpener -> CalendarViewStep.REVEAL
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
