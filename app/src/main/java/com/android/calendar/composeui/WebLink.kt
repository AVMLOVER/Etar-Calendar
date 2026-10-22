/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Turns a stored event URL into a web URL the browser can open.
 *
 * The stored value is whatever the calendar put there: often a full https URL, but just as often a
 * bare host such as "example.com/x" or "www.example.com". The old check required a scheme before
 * launching, so those values were silently refused - the tap appeared to do nothing. Only genuinely
 * non-web schemes are refused now, and a missing scheme is supplied as https.
 */
package com.android.calendar.composeui

import java.net.URI

/**
 * The URL to hand to ACTION_VIEW, or null when [raw] is empty or not a web address.
 *
 * Rules: surrounding whitespace and newlines are trimmed; a missing scheme gets `https://` (so
 * `www.` and bare hosts work); `http`/`https` are kept in any case; any other scheme (`mailto:`,
 * `tel:`, `ftp:`, …) is refused. A `host:port` is not mistaken for a scheme, because a scheme name
 * never contains a dot.
 */
fun normaliseWebUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        return null
    }
    val colon = trimmed.indexOf(':')
    val scheme = if (colon > 0) {
        trimmed.substring(0, colon).takeIf { candidate ->
            candidate.isNotEmpty() &&
                !candidate.contains('.') &&
                candidate.all { it.isLetterOrDigit() || it == '+' || it == '-' }
        }?.lowercase()
    } else {
        null
    }
    val candidate = when (scheme) {
        null -> "https://$trimmed"
        "http", "https" -> trimmed
        else -> return null
    }
    return try {
        val uri = URI(candidate)
        val validScheme = uri.scheme?.lowercase()
        val host = uri.host
        if ((validScheme == "http" || validScheme == "https") && !host.isNullOrEmpty()) {
            candidate
        } else {
            null
        }
    } catch (t: Throwable) {
        null
    }
}
