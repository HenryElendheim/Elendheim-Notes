package com.elendheim.notes.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elendheim.notes.data.Note
import com.elendheim.notes.ui.NotesViewModel
import com.elendheim.notes.ui.components.ConfirmDialog
import com.elendheim.notes.ui.components.MoveToFolderSheet
import com.elendheim.notes.ui.components.NameDialog
import com.elendheim.notes.ui.components.relativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun EditorScreen(
    noteId: Long,
    viewModel: NotesViewModel,
    onBack: () -> Unit
) {
    val allFolders by viewModel.allFolders.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    var loaded by remember { mutableStateOf<Note?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(false) }
    var folderId by remember { mutableStateOf<Long?>(null) }
    var savedAt by remember { mutableStateOf(0L) }
    var moving by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }

    LaunchedEffect(noteId) {
        viewModel.noteById(noteId)?.let { note ->
            title = note.title
            body = note.body
            pinned = note.pinned
            folderId = note.folderId
            savedAt = note.updatedAt
            loaded = note
        } ?: onBack()
    }

    fun buildNote(base: Note): Note = base.copy(
        title = title,
        body = body,
        pinned = pinned,
        folderId = folderId,
        updatedAt = System.currentTimeMillis()
    )

    // Debounced autosave while typing.
    LaunchedEffect(loaded) {
        val base = loaded ?: return@LaunchedEffect
        snapshotFlow { listOf(title, body, pinned, folderId) }
            .drop(1)
            .debounce(400)
            .collect {
                if (!deleted) {
                    val updated = buildNote(base)
                    viewModel.saveNote(updated)
                    savedAt = updated.updatedAt
                }
            }
    }

    // Final save when the screen actually goes away. Keyed on Unit so it can
    // never fire mid-session (a DisposableEffect keyed on the note used to run
    // its cleanup the moment the note finished loading, deleting fresh notes).
    // All the vars read here are snapshot state, so the values are current.
    DisposableEffect(Unit) {
        onDispose {
            val base = loaded ?: return@onDispose
            if (deleted) return@onDispose
            runBlocking(Dispatchers.IO) {
                if (title.isBlank() && body.isBlank()) {
                    viewModel.deleteNoteNow(base.id)
                } else {
                    viewModel.saveNoteNow(
                        base.copy(
                            title = title,
                            body = body,
                            pinned = pinned,
                            folderId = folderId,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (savedAt > 0) {
                        Text(
                            text = "Edited ${relativeTime(savedAt).replaceFirstChar { it.lowercase() }}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        pinned = !pinned
                    }) {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = if (pinned) "Unpin" else "Pin",
                            tint = if (pinned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { moving = true }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.DriveFileMove,
                            contentDescription = "Move to folder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete note",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = MaterialTheme.typography.headlineLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box {
                        if (title.isEmpty()) {
                            Text(
                                "Title",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp)
            )
            BasicTextField(
                value = body,
                onValueChange = { body = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box {
                        if (body.isEmpty()) {
                            Text(
                                "Start writing",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp)
            )
        }
    }

    if (moving) {
        MoveToFolderSheet(
            folders = allFolders,
            currentFolderId = folderId,
            onSelect = { target ->
                folderId = target
                moving = false
            },
            onNewFolder = {
                newFolder = true
                moving = false
            },
            onDismiss = { moving = false }
        )
    }

    if (newFolder) {
        NameDialog(
            title = "New folder",
            confirmLabel = "Create and move",
            onConfirm = { name ->
                viewModel.createFolder(name) { id -> folderId = id }
                newFolder = false
            },
            onDismiss = { newFolder = false }
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete note",
            message = "This note will be removed for good.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                deleted = true
                loaded?.let { viewModel.deleteNote(buildNote(it)) }
                onBack()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
