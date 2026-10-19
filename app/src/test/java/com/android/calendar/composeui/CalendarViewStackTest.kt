/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The navigation rules of the all-Compose calendar, checked without a device.
 *
 * Two rules live here. The drawer's stacking rule is what keeps the calendar at most two screens
 * deep and what makes back from the month view land on the default view rather than on the launcher.
 * The landing rule is what makes a change to the "Default view" setting take effect on the way back
 * from the settings screen instead of after a relaunch. Both are only about a handful of facts, so
 * the cases that matter are checked here rather than only by rotating a phone.
 */
package com.android.calendar.composeui

import com.android.calendar.CalendarController.ViewType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarViewStackTest {

    /** The landing screen: the launcher opened it, so the view asked for opens over it and it stays. */
    @Test
    fun theLandingScreenPushesTheViewOverItself() {
        assertEquals(
            CalendarViewStep.PUSH,
            calendarViewStep(isCalendarView = true, hasOpener = false, targetIsOpener = false),
        )
    }

    /** A view opened over the landing one is replaced, so the stack does not grow past two. */
    @Test
    fun aViewOpenedOverTheLandingOneIsReplaced() {
        assertEquals(
            CalendarViewStep.REPLACE,
            calendarViewStep(isCalendarView = true, hasOpener = true, targetIsOpener = false),
        )
    }

    /** Asking for the screen underneath is a step back to it, not a second copy of it. */
    @Test
    fun choosingTheViewUnderneathRevealsIt() {
        assertEquals(
            CalendarViewStep.REVEAL,
            calendarViewStep(isCalendarView = true, hasOpener = true, targetIsOpener = true),
        )
    }

    /**
     * The landing screen cannot be its own opener, so that fact must not change its step: the day
     * view it asks for is started over it rather than revealed from under it.
     */
    @Test
    fun theLandingScreenNeverRevealsItself() {
        assertEquals(
            CalendarViewStep.PUSH,
            calendarViewStep(isCalendarView = true, hasOpener = false, targetIsOpener = true),
        )
    }

    /** A tool screen - the year view, the agenda - keeps whatever it opens on top of itself. */
    @Test
    fun aToolScreenPushesWhateverItOpens() {
        assertEquals(
            CalendarViewStep.PUSH,
            calendarViewStep(isCalendarView = false, hasOpener = true, targetIsOpener = false),
        )
        assertEquals(
            CalendarViewStep.PUSH,
            calendarViewStep(isCalendarView = false, hasOpener = false, targetIsOpener = true),
        )
    }

    /**
     * The decision the drawer makes, stated on its own: only a replace and a reveal finish the
     * screen that is showing. Finishing the landing screen on a push is what left the month alone in
     * the stack and sent back to the launcher.
     */
    @Test
    fun onlyReplaceAndRevealFinishTheScreenThatIsShowing() {
        assertFalse(
            calendarViewStep(isCalendarView = true, hasOpener = false, targetIsOpener = false)
                .finishesCurrentScreen,
        )
        assertTrue(
            calendarViewStep(isCalendarView = true, hasOpener = true, targetIsOpener = false)
                .finishesCurrentScreen,
        )
        assertTrue(
            calendarViewStep(isCalendarView = true, hasOpener = true, targetIsOpener = true)
                .finishesCurrentScreen,
        )
    }

    /** A landing screen hands over when the "Default view" setting no longer names its own view. */
    @Test
    fun aLandingScreenHandsOverWhenTheDefaultViewChanged() {
        assertEquals(
            ViewType.MONTH,
            landingViewToShow(configured = ViewType.MONTH, mine = ViewType.DAY, hasOpener = false),
        )
        assertEquals(
            ViewType.AGENDA,
            landingViewToShow(configured = ViewType.AGENDA, mine = ViewType.WEEK, hasOpener = false),
        )
    }

    /** A landing screen that already shows the configured view does nothing. */
    @Test
    fun aLandingScreenAlreadyShowingTheDefaultViewStays() {
        assertNull(
            landingViewToShow(configured = ViewType.MONTH, mine = ViewType.MONTH, hasOpener = false),
        )
    }

    /** Only the landing screen re-routes: a screen opened over another one is left where it is. */
    @Test
    fun aScreenOpenedOverAnotherOneNeverReroutes() {
        assertNull(
            landingViewToShow(configured = ViewType.AGENDA, mine = ViewType.DAY, hasOpener = true),
        )
    }

    /**
     * The screen the switch starts must find the configuration satisfied when it resumes, or the two
     * screens would keep handing over to each other.
     */
    @Test
    fun theScreenStartedByTheSwitchDoesNotRerouteAgain() {
        val target = ViewType.WEEK
        assertEquals(target, landingViewToShow(ViewType.WEEK, ViewType.DAY, hasOpener = false))
        assertNull(landingViewToShow(ViewType.WEEK, target, hasOpener = false))
    }

    /** A screen with nothing underneath it is the landing screen, whatever view it shows. */
    @Test
    fun aScreenWithNothingUnderneathIsLanding() {
        assertTrue(isEffectivelyLanding(hasOpener = false, isDefaultView = false))
        assertTrue(isEffectivelyLanding(hasOpener = false, isDefaultView = true))
    }

    /**
     * The screen showing the configured default view is the landing screen even when it was opened
     * over another one: the month a year view opens is where the app begins, so back from it leaves
     * the app instead of returning to the year view.
     */
    @Test
    fun theDefaultViewIsLandingEvenWhenItWasOpenedOverAnotherScreen() {
        assertTrue(isEffectivelyLanding(hasOpener = true, isDefaultView = true))
    }

    /** A screen opened over another one that shows something else is a step into the calendar. */
    @Test
    fun aScreenOpenedOverAnotherOneThatIsNotTheDefaultViewIsNotLanding() {
        assertFalse(isEffectivelyLanding(hasOpener = true, isDefaultView = false))
    }
}
