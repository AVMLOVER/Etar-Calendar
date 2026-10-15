/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Reading a single row of CalendarContract when its own row uri does not answer.
 *
 * The provider does not always expose a row through `<collection>/<id>`. A calendar belonging to a
 * local account - the offline kind this app creates - is listed correctly through the collection uri
 * but returns no rows when asked for by id, and the same happens to the events stored on it. The
 * query does not fail; it comes back empty, so the caller sees "not found" and the user is told the
 * thing they are looking at does not exist.
 *
 * Asking the collection uri with an id selection instead returns the row. These helpers are that
 * second attempt, so every single-row read can be written the same way rather than each one growing
 * its own fallback.
 */
package com.android.calendar.event.compose

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri

/**
 * Runs [body] over the row with this id, trying the row uri first and the collection uri with an id
 * selection second.
 *
 * Returns null when neither has the row.
 */
inline fun <T> ContentResolver.queryRowById(
    collection: Uri,
    id: Long,
    projection: Array<String>,
    body: (Cursor) -> T,
): T? {
    query(ContentUris.withAppendedId(collection, id), projection, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            return body(c)
        }
    }
    query(collection, projection, android.provider.BaseColumns._ID + "=?", arrayOf(id.toString()), null)
        ?.use { c ->
            if (c.moveToFirst()) {
                return body(c)
            }
        }
    return null
}
