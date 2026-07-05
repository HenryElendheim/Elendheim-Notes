package com.elendheim.notes.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.elendheim.notes.data.Note
import com.elendheim.notes.data.NotesDatabase
import com.elendheim.notes.ui.theme.NotesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            NotesTheme {
                var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
                var loaded by remember { mutableStateOf(false) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    notes = withContext(Dispatchers.IO) {
                        NotesDatabase.get(this@WidgetConfigActivity).notesDao().allUnlocked()
                    }
                    loaded = true
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Text(
                        text = "What should this widget show?",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(24.dp)
                    )
                    LazyColumn {
                        item {
                            ChoiceRow(title = "Pinned notes", caption = "Your pinned notes as a list") {
                                choose(appWidgetId, SHOW_PINNED)
                            }
                        }
                        items(notes, key = { it.id }) { note ->
                            ChoiceRow(
                                title = note.title.ifBlank { "Untitled" },
                                caption = "Show this note"
                            ) {
                                choose(appWidgetId, note.id.toString())
                            }
                        }
                        if (loaded && notes.isEmpty()) {
                            item {
                                Text(
                                    text = "No notes yet. The widget will show pinned notes.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun choose(appWidgetId: Int, value: String) {
        getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).edit()
            .putString(widgetChoiceKey(appWidgetId), value)
            .apply()
        updateNoteWidgets(this)
        lifecycleScope.launch {
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
            finish()
        }
    }
}

@androidx.compose.runtime.Composable
private fun ChoiceRow(title: String, caption: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
