package com.elendheim.notes.data

// A note body is plain text. Lines starting with the markers below are
// checklist items; everything else is ordinary text. The markers are readable
// in exports and in any other editor.
private const val UNCHECKED = "- [ ] "
private const val CHECKED = "- [x] "

data class BodyLine(val checked: Boolean?, val text: String)

fun parseBody(body: String): List<BodyLine> =
    body.split("\n").map { raw ->
        when {
            raw.startsWith(UNCHECKED) -> BodyLine(false, raw.removePrefix(UNCHECKED))
            raw.startsWith(CHECKED) -> BodyLine(true, raw.removePrefix(CHECKED))
            else -> BodyLine(null, raw)
        }
    }

fun serializeBody(lines: List<BodyLine>): String =
    lines.joinToString("\n") { line ->
        when (line.checked) {
            null -> line.text
            false -> UNCHECKED + line.text
            true -> CHECKED + line.text
        }
    }

/** Pair of checked count to total checklist items, or null if no checklist. */
fun checklistProgress(body: String): Pair<Int, Int>? {
    val items = parseBody(body).filter { it.checked != null }
    if (items.isEmpty()) return null
    return items.count { it.checked == true } to items.size
}

/** Body text with checklist markers stripped, for previews. */
fun previewText(body: String): String =
    parseBody(body).joinToString("\n") { it.text }.trim()

fun wordCount(body: String): Int =
    previewText(body).split(Regex("\\s+")).count { it.isNotBlank() }
