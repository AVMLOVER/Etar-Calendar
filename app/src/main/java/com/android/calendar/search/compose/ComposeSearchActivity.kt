/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Event search, in the Material 3 Expressive chrome.
 *
 * This replaces the classic SearchActivity for the screens that use the M3E host. It reuses the
 * agenda screen with a search term rather than introducing a second kind of result list, so a search
 * result looks and behaves exactly like the same event does in the agenda - including its delete
 * confirmation and its countdown link.
 */
package com.android.calendar.search.compose

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.calendar.agenda.compose.AgendaEvent
import com.android.calendar.agenda.compose.M3eAgendaScreen
import com.android.calendar.chrome.M3eHostActivity
import ws.xsoh.etar.R

class ComposeSearchActivity : M3eHostActivity() {

    override val checkedMenuItemId: Int = 0
    override val currentScreenName: String = "SEARCH"
    override val exitOnBack: Boolean = false

    /** The heading is the screen's name; there is no period for a picker to open. */
    override val titleOpensPicker: Boolean = false

    /** Nothing to go "today" to and nowhere to jump to: this screen is the search. */
    override val showTodayAction: Boolean = false
    override val showGotoAction: Boolean = false

    /**
     * No search entry in the overflow: the field is on the screen itself.
     */
    override val showSearchAction: Boolean = false

    private var query by mutableStateOf("")

    /** Bumped by the title bar's refresh action, which makes the result list run its search again. */
    private var refreshKey by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A query can arrive from the calendar's title bar field, which sends ACTION_SEARCH.
        query = intent.getStringExtra(android.app.SearchManager.QUERY).orEmpty()
        setPickerTitle(getString(R.string.search))
        setM3eContent {
            M3eAgendaScreen(
                onOpenEvent = { event -> openEvent(event) },
                onDeleteEvent = { event -> deleteEvent(event) },
                searchQuery = query,
                onSearchQueryChange = { updated -> query = updated.orEmpty() },
                refreshKey = refreshKey,
            )
        }
    }

    /** Re-reads the results now, as changing the query does, when the title bar's refresh is used. */
    override fun onRefresh() {
        refreshKey++
    }

    /**
     * A second search reuses this screen, which is what singleTop and this override are for.
     *
     * The query arrives as an ACTION_SEARCH extra, the form the system's search routing and the
     * calendar's title bar field both use.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(android.app.SearchManager.QUERY)?.let { query = it }
    }

    /** Opens the event the way the agenda does: through the M3E details screen. */
    private fun openEvent(event: AgendaEvent) {
        startActivity(
            Intent(this, com.android.calendar.event.compose.ComposeEventDetailsActivity::class.java)
                .putExtra(
                    com.android.calendar.event.compose.ComposeEventDetailsActivity.EXTRA_EVENT_ID,
                    event.id,
                )
        )
    }

    /**
     * Deletes through the same repository the agenda uses, so a result row behaves exactly like the
     * same event does in the agenda.
     */
    private fun deleteEvent(event: AgendaEvent) {
        val deleted = com.android.calendar.agenda.compose.AgendaRepository(applicationContext)
            .delete(this, event.id)
        if (!deleted) {
            android.widget.Toast.makeText(
                this,
                R.string.m3e_event_delete_failed,
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
