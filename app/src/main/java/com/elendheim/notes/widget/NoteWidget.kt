package com.elendheim.notes.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.elendheim.notes.MainActivity
import com.elendheim.notes.data.Note
import com.elendheim.notes.data.NotesDatabase
import com.elendheim.notes.data.parseBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val WidgetBg = ColorProvider(Color(0xFF15151A))
private val WidgetText = ColorProvider(Color(0xFFECECF1))
private val WidgetDim = ColorProvider(Color(0xFF9C9CA8))
private val WidgetAccent = ColorProvider(Color(0xFFB79CED))

const val WIDGET_PREFS = "widget_prefs"
const val SHOW_PINNED = "pinned"

fun widgetChoiceKey(appWidgetId: Int) = "widget_$appWidgetId"

fun updateNoteWidgets(context: Context) {
    val appContext = context.applicationContext
    CoroutineScope(Dispatchers.IO).launch {
        runCatching { NoteWidget().updateAll(appContext) }
    }
}

class NoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NoteWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { prefs.remove(widgetChoiceKey(it)) }
        prefs.apply()
    }
}

class NoteWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val choice = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            .getString(widgetChoiceKey(appWidgetId), SHOW_PINNED) ?: SHOW_PINNED

        val dao = NotesDatabase.get(context).notesDao()
        val pinned: List<Note>
        val single: Note?
        if (choice == SHOW_PINNED) {
            pinned = dao.pinnedUnlocked(6)
            single = null
        } else {
            pinned = emptyList()
            single = choice.toLongOrNull()?.let { dao.noteById(it) }
        }

        provideContent {
            WidgetContent(single, pinned)
        }
    }
}

@Composable
private fun WidgetContent(single: Note?, pinned: List<Note>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBg)
            .padding(14.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = single?.title?.ifBlank { "Untitled" } ?: "Pinned notes",
                style = TextStyle(
                    color = WidgetAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(openAppAction(single?.id))
            )
            Text(
                text = "+",
                style = TextStyle(
                    color = WidgetAccent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = GlanceModifier
                    .padding(horizontal = 8.dp)
                    .clickable(newNoteAction())
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        if (single != null) {
            val lines = parseBody(single.body).take(8)
            Column(modifier = GlanceModifier.fillMaxWidth().clickable(openAppAction(single.id))) {
                lines.forEach { line ->
                    if (line.text.isNotBlank()) {
                        Text(
                            text = line.text,
                            maxLines = 1,
                            style = TextStyle(
                                color = if (line.checked == true) WidgetDim else WidgetText,
                                fontSize = 13.sp,
                                textDecoration = if (line.checked == true) TextDecoration.LineThrough
                                else TextDecoration.None
                            )
                        )
                    }
                }
                if (lines.all { it.text.isBlank() }) {
                    Text(
                        text = "Empty note",
                        style = TextStyle(color = WidgetDim, fontSize = 13.sp)
                    )
                }
            }
        } else if (pinned.isEmpty()) {
            Text(
                text = "Pin a note and it will show up here",
                style = TextStyle(color = WidgetDim, fontSize = 13.sp)
            )
        } else {
            pinned.forEach { note ->
                Text(
                    text = note.title.ifBlank { "Untitled" },
                    maxLines = 1,
                    style = TextStyle(color = WidgetText, fontSize = 13.sp),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable(openAppAction(note.id))
                )
            }
        }
    }
}

private fun openAppAction(noteId: Long?) =
    actionStartActivity(
        Intent().setClassName(
            "com.elendheim.notes",
            "com.elendheim.notes.MainActivity"
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (noteId != null) putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
        }
    )

private fun newNoteAction() =
    actionStartActivity(
        Intent().setClassName(
            "com.elendheim.notes",
            "com.elendheim.notes.MainActivity"
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_NEW_NOTE, true)
        }
    )
