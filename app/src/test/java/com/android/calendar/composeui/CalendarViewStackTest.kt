/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The navigation rules of the all-Compose calendar, checked without a device.
 *
 * Two rules live here. The drawer's stacking rule is what keeps the calendar at most two screens
 * deep, what makes back from the month view land on the default view rather than on the launcher,
 * and what makes a menu entry for the screen underneath behave exactly like the back gesture - on a
 * tool screen as much as on a calendar view. The landing rule is what makes a change to the "Default
 * view" setting take effect on the way back from the settings screen instead of after a relaunch.
 * Both are only about a handful of facts, so the cases that matter are checked here rather than only
 * by rotating a phone.
 */
package com.android.calendar.composeui

import com.android.calendar.CalendarController.ViewType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ws.xsoh.etar.R

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
    fun aToolScreenPushesAScreenThatIsNotItsOpener() {
        assertEquals(
            CalendarViewStep.PUSH,
            calendarViewStep(isCalendarView = false, hasOpener = true, targetIsOpener = false),
        )
    }

    /**
     * The reported case: the year view was opened from the month view, and its menu has an entry for
     * the month. Choosing it is the back gesture made from the menu - finish the year view and reveal
     * the month it was opened over - not a second month pushed on top of the year view, which played
     * the entry slide and left the two screens stacked.
     */
    @Test
    fun aToolScreenRequestingItsOpenerRevealsIt() {
        val step = calendarViewStep(
            isCalendarView = false,
            hasOpener = true,
            targetIsOpener = true,
        )
        assertEquals(CalendarViewStep.REVEAL, step)
        // Exactly what back does: finish, start nothing.
        assertTrue(step.finishesCurrentScreen)
        assertFalse(step.startsScreen)
    }

    /**
     * A tool screen that was not opened over anything - or was opened by a caller that did not name
     * the class - has no entry that is a step back: the view it names starts over it.
     */
    @Test
    fun aToolScreenWithoutANamedOpenerNeverReveals() {
        assertEquals(
            CalendarViewStep.PUSH,
            calendarViewStep(isCalendarView = false, hasOpener = false, targetIsOpener = true),
        )
    }

    /**
     * No menu choice on a tool screen both starts a screen and finishes the one that is showing:
     * such a step plays two slides at once, which is the overlap the year view's month entry was
     * reported as. Every tool-screen request is a push (start, no finish) or a reveal (finish, no
     * start).
     */
    @Test
    fun noToolScreenStepBothStartsAndFinishes() {
        for (hasOpener in listOf(false, true)) {
            for (targetIsOpener in listOf(false, true)) {
                val step = calendarViewStep(false, hasOpener, targetIsOpener)
                assertFalse(
                    "step $step for hasOpener=$hasOpener targetIsOpener=$targetIsOpener " +
                        "starts a screen and finishes the current one",
                    step.startsScreen && step.finishesCurrentScreen,
                )
            }
        }
    }

    /**
     * The only step that does both is the peer replacement between calendar views, and it is what
     * keeps the calendar's stack at most two deep. It is never chosen for a tool screen.
     */
    @Test
    fun onlyAPeerReplacementStartsAndFinishes() {
        for (isCalendarView in listOf(false, true)) {
            for (hasOpener in listOf(false, true)) {
                for (targetIsOpener in listOf(false, true)) {
                    val step = calendarViewStep(isCalendarView, hasOpener, targetIsOpener)
                    if (step.startsScreen && step.finishesCurrentScreen) {
                        assertEquals(CalendarViewStep.REPLACE, step)
                        assertTrue(isCalendarView)
                        assertTrue(hasOpener)
                        assertFalse(targetIsOpener)
                    }
                }
            }
        }
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

    /**
     * The drawer's decision as one table: every kind of screen against every kind of target.
     *
     * The reported defect was that the year view, the agenda and the other tool screens pushed the
     * month over themselves instead of revealing the month they were opened from. These rows pin
     * both halves of that decision - the branch and the pair its finish uses - so a screen can only
     * ever return to its opener with the return pair.
     */
    @Test
    fun theDrawerDecisionTable() {
        data class Row(
            val screen: String,
            val isCalendarView: Boolean,
            val hasOpener: Boolean,
            val target: String,
            val targetIsOpener: Boolean,
            val expectedStep: CalendarViewStep,
            val expectedFinish: SlideOperation?,
        )

        val rows = listOf(
            // The landing screen has nothing underneath it, so every entry is a push.
            Row("landing month", true, false, "day", false, CalendarViewStep.PUSH, null),
            Row("landing month", true, false, "month", true, CalendarViewStep.PUSH, null),
            // A calendar view opened over the month: its month entry is a return, any other view
            // takes its place.
            Row(
                "day over month", true, true, "month (opener)", true,
                CalendarViewStep.REVEAL, SlideOperation.FINISH_RETURN,
            ),
            Row(
                "day over month", true, true, "week", false,
                CalendarViewStep.REPLACE, SlideOperation.FINISH_FORWARD,
            ),
            // A tool screen opened over the month: its month entry is the same return, and any
            // other entry opens on top of it.
            Row(
                "year over month", false, true, "month (opener)", true,
                CalendarViewStep.REVEAL, SlideOperation.FINISH_RETURN,
            ),
            Row("year over month", false, true, "agenda", false, CalendarViewStep.PUSH, null),
            // A tool screen with nothing underneath it can never reveal: every entry is a push.
            Row("year, no opener", false, false, "month", false, CalendarViewStep.PUSH, null),
            Row("year, no opener", false, false, "month (not named)", true, CalendarViewStep.PUSH, null),
        )

        for (row in rows) {
            val step = calendarViewStep(row.isCalendarView, row.hasOpener, row.targetIsOpener)
            assertEquals("${row.screen} -> ${row.target}: step", row.expectedStep, step)
            if (step.finishesCurrentScreen) {
                val operation = finishOperation(handsOver = step == CalendarViewStep.REPLACE)
                assertEquals("${row.screen} -> ${row.target}: pair", row.expectedFinish, operation)
            } else {
                assertNull("${row.screen} -> ${row.target}: no finish", row.expectedFinish)
            }
        }
    }

    /**
     * Every step back to the opener - calendar view or tool screen - uses the return pair, which is
     * the reverse of a start. The reported drawer defect was a leftward pair on exactly this path.
     */
    @Test
    fun everyReturnToTheOpenerUsesTheReturnPair() {
        for (isCalendarView in listOf(false, true)) {
            val step = calendarViewStep(isCalendarView, hasOpener = true, targetIsOpener = true)
            assertEquals(CalendarViewStep.REVEAL, step)
            val operation = finishOperation(handsOver = step == CalendarViewStep.REPLACE)
            assertEquals(SlideOperation.FINISH_RETURN, operation)
            val pair = animationPairFor(operation)
            assertEquals(R.anim.slide_right_in, pair.enterAnim)
            assertEquals(R.anim.slide_right_out, pair.exitAnim)
            assertNotEquals(animationPairFor(SlideOperation.START), pair)
        }
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

    /**
     * A tool screen opened from the calendar - the to-do editor and the countdown list - is a step
     * into the app: with the opener extras back finishes it and reveals the calendar underneath.
     */
    @Test
    fun aToolScreenOpenedFromTheCalendarFinishesOnBack() {
        assertFalse(isEffectivelyLanding(hasOpener = true, isDefaultView = false))
    }

    /**
     * The same tool screen without the opener extras would be the landing screen, and back would
     * take the whole task to the launcher. That is the difference the chooser's intents had to add.
     */
    @Test
    fun aToolScreenWithoutTheOpenerExtrasWouldLeaveTheAppOnBack() {
        assertTrue(isEffectivelyLanding(hasOpener = false, isDefaultView = false))
    }
}
