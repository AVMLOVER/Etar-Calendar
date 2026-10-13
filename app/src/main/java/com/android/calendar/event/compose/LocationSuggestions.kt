/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Location suggestions for the Material 3 event editor.
 *
 * It reuses EventLocationAdapter rather than re-deriving the queries, so the suggestions are exactly
 * the ones the classic editor offered: addresses from the device's contacts, and locations the user
 * has already typed on earlier events. Both come from the device - neither is a network geocoder.
 */
package com.android.calendar.event.compose

import android.content.Context
import com.android.calendar.event.EventLocationAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class LocationSuggestions(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Suggestions matching [query].
     *
     * EventLocationAdapter answers on a background thread through a Filter.FilterListener, so this
     * bridges that to a suspend call. The adapter instance is created per call: it is cheap, and the
     * classic editor creates one per screen too.
     */
    suspend fun forQuery(query: String): List<String> {
        if (query.length < 2) {
            return emptyList()
        }
        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                try {
                    val adapter = EventLocationAdapter(appContext)
                    // getFilter().filter(...) rather than adapter.filter(...): the latter resolves to
                    // the Filter property instead of the Filterable method.
                    adapter.filter.filter(query, android.widget.Filter.FilterListener {
                        val out = ArrayList<String>()
                        for (i in 0 until adapter.count) {
                            adapter.getItem(i)?.let { result ->
                                val text = result.toString()
                                if (text.isNotBlank() && !out.contains(text)) {
                                    out.add(text)
                                }
                            }
                        }
                        if (continuation.isActive) {
                            continuation.resume(out)
                        }
                    })
                } catch (t: Throwable) {
                    // A device without the contacts provider must not break typing a location.
                    if (continuation.isActive) {
                        continuation.resume(emptyList())
                    }
                }
            }
        }
    }
}
