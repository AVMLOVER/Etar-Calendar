/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The to-do screen: one day at a time, swiped from day to day like the day view.
 *
 * A day either has a to-do or it does not. With one, its name heads the screen and its checklist is
 * below, each line tickable, draggable and swipe-deletable, with the day's note under it. Without
 * one, the day says so and the add button is offered; the add button is hidden once there is
 * something, because there is only ever one to-do per day, and the search button takes its place.
 */
package com.android.calendar.todo.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.calendar.todo.Todo
import com.android.calendar.todo.TodoItem
import com.android.calendar.todo.TodoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ws.xsoh.etar.R
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** How many days the swipe can reach in either direction from the day the screen opened on. */
private const val PAGE_COUNT = 1200
private const val PAGE_MIDDLE = PAGE_COUNT / 2

@Composable
fun M3eTodoScreen(
    dayEpoch: Long,
    refreshKey: Int,
    todayKey: Int,
    pickedDayKey: Long,
    editRequestKey: Int,
    /**
     * Open the editor as soon as the day has been read: the to-do widget's header asks for this, and
     * the day's own to-do decides whether that is an edit or a new one.
     */
    openEditorOnStart: Boolean = false,
    onDayChanged: (LocalDate) -> Unit,
    onHasTodoChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }

    // The day the screen opened on. The pager pages around it; this does not move as they are swiped
    // through, or every page would shift under the one being looked at.
    val anchor = remember { LocalDate.ofEpochDay(dayEpoch) }
    val pagerState = rememberPagerState(initialPage = PAGE_MIDDLE, pageCount = { PAGE_COUNT })

    /** Bumped after a change made here, so the day is re-read from where it is stored. */
    var revision by remember { mutableStateOf(0) }
    val reloadKey = refreshKey + revision

    var visibleDay by remember { mutableStateOf(anchor) }
    var visibleTodo by remember { mutableStateOf<Todo?>(null) }
    var visibleLoaded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Todo?>(null) }
    var pendingItemDelete by remember { mutableStateOf<Pair<Todo, TodoItem>?>(null) }

    // Search narrows the day's own checklist rather than opening a screen: the items of the day on
    // screen are all there is to look through.
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // The day on screen is the page the pager has settled on.
    LaunchedEffect(pagerState.currentPage) {
        val day = anchor.plusDays((pagerState.currentPage - PAGE_MIDDLE).toLong())
        visibleDay = day
        visibleLoaded = false
        query = ""
        searching = false
        onDayChanged(day)
    }

    // The today button: the page today falls on, measured from the day the screen opened on.
    LaunchedEffect(todayKey) {
        if (todayKey == 0) return@LaunchedEffect
        val daysAway = ChronoUnit.DAYS.between(anchor, LocalDate.now(zone)).toInt()
        val target = PAGE_MIDDLE + daysAway
        if (target in 0 until PAGE_COUNT) {
            pagerState.animateScrollToPage(target)
        }
    }

    // The title bar's edit action, which the activity offers while this day has a to-do.
    LaunchedEffect(editRequestKey) {
        if (editRequestKey == 0) return@LaunchedEffect
        visibleTodo?.let { editing = it }
    }

    // The same editor, asked for before the screen even appeared: the widget's header opens the
    // screen in this mode. Once only - cancelling the editor must not bring it straight back - and
    // only after the day has been read, because whether this is an add or an edit is the day's own
    // to-do's answer. A day with nothing on it gets a new, empty one.
    var startEditorOpened by remember { mutableStateOf(false) }
    LaunchedEffect(visibleLoaded, openEditorOnStart) {
        if (!openEditorOnStart || startEditorOpened || !visibleLoaded) return@LaunchedEffect
        startEditorOpened = true
        editing = visibleTodo ?: Todo(date = visibleDay, name = "")
    }

    // A day chosen from the heading's picker: the page that day falls on, measured from the day the
    // screen opened on - the same arithmetic the today button uses.
    LaunchedEffect(pickedDayKey) {
        if (pickedDayKey == 0L) return@LaunchedEffect
        val daysAway = ChronoUnit.DAYS.between(anchor, LocalDate.ofEpochDay(pickedDayKey)).toInt()
        val target = PAGE_MIDDLE + daysAway
        if (target in 0 until PAGE_COUNT) {
            pagerState.animateScrollToPage(target)
        }
    }

    LaunchedEffect(visibleDay, reloadKey) {
        val loaded = withContext(Dispatchers.IO) { TodoStore.forDate(context, visibleDay) }
        visibleTodo = loaded
        visibleLoaded = true
    }

    LaunchedEffect(visibleTodo != null) { onHasTodoChanged(visibleTodo != null) }

    /** Saves a changed to-do; the screen shows the change at once and the day is re-read if asked. */
    fun persist(updated: Todo, reload: Boolean = false) {
        visibleTodo = updated
        scope.launch {
            withContext(Dispatchers.IO) { TodoStore.save(context, updated) }
            if (reload) revision++
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            if (page != pagerState.currentPage) {
                // Only the day being looked at is drawn: a page either side is built as it is swiped
                // to, and its data is read then.
                Spacer(Modifier.fillMaxSize())
                return@HorizontalPager
            }
            val day = anchor.plusDays((page - PAGE_MIDDLE).toLong())
            val todo = visibleTodo
            if (!visibleLoaded) {
                Spacer(Modifier.fillMaxSize())
            } else if (todo == null) {
                EmptyDay(day = day, today = LocalDate.now(zone))
            } else {
                TodoDay(
                    todo = todo,
                    searching = searching,
                    query = query,
                    onQueryChange = { query = it },
                    onCloseSearch = { query = ""; searching = false },
                    onToggle = { item ->
                        val toggled = todo.items.map {
                            if (it.id == item.id) it.copy(done = !it.done) else it
                        }
                        // Done lines go to the end of the list, which is what "put it at the bottom"
                        // means; un-ticking brings it back among the ones to do.
                        persist(todo.copy(items = toggled.sortedBy { it.done }))
                    },
                    onMove = { from, to -> persist(todo.copy(items = todo.items.moved(from, to))) },
                    onDeleteItem = { item -> pendingItemDelete = todo to item },
                )
            }
        }

        // One to-do per day: with one already there, there is nothing for the add button to add, and
        // the search button takes its place - with no to-do there is nothing to search either.
        if (visibleLoaded && editing == null) {
            if (visibleTodo == null) {
                FloatingActionButton(
                    onClick = { editing = Todo(date = visibleDay, name = "") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 20.dp,
                            bottom = 20.dp + WindowInsets.navigationBars.asPaddingValues()
                                .calculateBottomPadding(),
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add_event),
                        contentDescription = stringResource(R.string.m3e_todo_new),
                    )
                }
            } else {
                FilledTonalIconButton(
                    onClick = {
                        if (searching) {
                            query = ""
                            searching = false
                        } else {
                            searching = true
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 20.dp,
                            bottom = 20.dp + WindowInsets.navigationBars.asPaddingValues()
                                .calculateBottomPadding(),
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_menu_search),
                        contentDescription = stringResource(R.string.search),
                    )
                }
            }
        }
    }

    val openEditor = editing
    if (openEditor != null) {
        M3eTodoEditorScreen(
            initial = openEditor,
            onCancel = { editing = null },
            onSave = { saved ->
                editing = null
                // Saving is also what puts the reminder on the clock - TodoStore.save does both -
                // and the day is re-read afterwards so the screen shows what was written.
                scope.launch {
                    withContext(Dispatchers.IO) { TodoStore.save(context, saved) }
                    revision++
                }
            },
            onDelete = { removed ->
                editing = null
                scope.launch {
                    withContext(Dispatchers.IO) { TodoStore.delete(context, removed.date) }
                    revision++
                }
            },
        )
    }

    pendingItemDelete?.let { (todo, item) ->
        AlertDialog(
            onDismissRequest = { pendingItemDelete = null },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.m3e_todo_delete_item_title)) },
            text = { Text(stringResource(R.string.m3e_todo_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val remaining = todo.items.filterNot { it.id == item.id }
                    pendingItemDelete = null
                    if (remaining.isEmpty()) {
                        // The last line is gone, so the day has no to-do any more. Keeping the title
                        // alone would leave a to-do with nothing to do - and hide the add button, so
                        // it could not be given anything either.
                        scope.launch {
                            withContext(Dispatchers.IO) { TodoStore.delete(context, todo.date) }
                            visibleTodo = null
                            revision++
                        }
                    } else {
                        persist(todo.copy(items = remaining))
                    }
                }) { Text(stringResource(R.string.m3e_event_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingItemDelete = null }) {
                    Text(stringResource(R.string.m3e_back))
                }
            },
        )
    }
}

/** A day with nothing on it. */
@Composable
private fun EmptyDay(day: LocalDate, today: LocalDate) {
    val text = when (day) {
        today -> stringResource(R.string.m3e_todo_empty_today)
        today.minusDays(1) -> stringResource(R.string.m3e_todo_empty_yesterday)
        today.plusDays(1) -> stringResource(R.string.m3e_todo_empty_tomorrow)
        else -> stringResource(R.string.m3e_todo_empty)
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}

/** One day's to-do: its name at the top, then its checklist, then the day's note. */
@Composable
private fun TodoDay(
    todo: Todo,
    searching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onToggle: (TodoItem) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDeleteItem: (TodoItem) -> Unit,
) {
    val term = query.trim()
    val shown = if (term.isEmpty()) {
        todo.items
    } else {
        todo.items.filter { it.text.contains(term, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = todo.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (todo.items.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            R.string.m3e_todo_progress, todo.doneCount, todo.items.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (searching) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.search)) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_menu_search),
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = onCloseSearch) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = stringResource(R.string.m3e_back),
                                )
                            }
                        },
                    )
                }
            }
        }

        item {
            TodoItemColumn(
                items = shown,
                showCheckboxes = true,
                onToggle = onToggle,
                onMove = onMove,
                onDeleteRequest = onDeleteItem,
            )
        }

        // The note, under the checklist, on the theme's own colour so it reads as part of the day
        // rather than another line to tick.
        if (todo.note.isNotBlank()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        text = todo.note,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

/** The rows of the list, in the order given, with one row moved from [from] to [to]. */
private fun List<TodoItem>.moved(from: Int, to: Int): List<TodoItem> {
    if (from == to || from !in indices || to !in indices) return this
    val list = toMutableList()
    val item = list.removeAt(from)
    list.add(to, item)
    return list
}
