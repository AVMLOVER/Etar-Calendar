/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The chooser's icons, checked without a device.
 *
 * painterResource only loads vectors and raster assets. The to-do entry is a <layer-list>, and passing
 * it to painterResource crashed the release build with "Only VectorDrawables and rasterized asset
 * types are supported". These cases pin which icons are which, and that the chooser loads the layer
 * list through the drawable helper rather than through painterResource.
 */
package com.android.calendar.composeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AddChooserIconsTest {

    /** Every icon the chooser passes to painterResource must be a vector or a raster asset. */
    @Test
    fun theVectorEntriesAreVectors() {
        for (name in listOf("outline_calendar_view_month", "outline_countdown")) {
            assertEquals("$name root tag", "vector", rootTag(name))
        }
    }

    /** The to-do icon is a layer list, which is exactly the type painterResource rejects. */
    @Test
    fun theTodoEntryIsALayerList() {
        assertEquals("today_icon_24 root tag", "layer-list", rootTag("today_icon_24"))
    }

    /**
     * The chooser loads the layer list through rememberDrawablePainter, never through painterResource.
     *
     * This reads the source rather than the running app: the resource type is a compile-time fact, and
     * the crash was a single wrong call, so the check is the presence of the right call and the
     * absence of the wrong one.
     */
    @Test
    fun theChooserNeverPassesTheTodoIconToPainterResource() {
        val source = sourceFile(
            "src/main/java/com/android/calendar/chrome/compose/M3eAddMenu.kt",
            "app/src/main/java/com/android/calendar/chrome/compose/M3eAddMenu.kt",
        )
        val text = source.readText()
        assertTrue(
            "the chooser must load today_icon_24 through rememberDrawablePainter",
            text.contains("rememberDrawablePainter(R.drawable.today_icon_24"),
        )
        assertFalse(
            "painterResource cannot load the layer-list today icon",
            text.contains("painterResource(R.drawable.today_icon_24"),
        )
    }

    private fun rootTag(name: String): String {
        val file = resourceFile("drawable/$name.xml")
        val text = file.readText()
        // Skip the XML declaration and any comments: the first element name is the root tag.
        val match = Regex("""<\s*([A-Za-z][\w-]*)""").find(text)
            ?: error("no root element in ${file.path}")
        return match.groupValues[1]
    }

    private fun resourceFile(relative: String): File =
        find(listOf("src/main/res/$relative", "app/src/main/res/$relative"))

    private fun sourceFile(vararg candidates: String): File = find(candidates.toList())

    private fun find(candidates: List<String>): File {
        val cwd = File("").absoluteFile
        val roots = listOf(cwd) + generateSequence(cwd) { it.parentFile }.take(3).toList()
        for (root in roots) {
            for (candidate in candidates) {
                val file = File(root, candidate)
                if (file.isFile) {
                    return file
                }
            }
        }
        error("not found: ${candidates.joinToString()} from ${cwd.path}")
    }
}
