package com.elendheim.notes.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elendheim.notes.data.Note
import com.elendheim.notes.ui.NotesViewModel
import com.elendheim.notes.ui.components.ColorPickerSheet
import com.elendheim.notes.ui.components.ConfirmDialog
import com.elendheim.notes.ui.components.EmptyState
import com.elendheim.notes.ui.components.FolderActionsSheet
import com.elendheim.notes.ui.components.FolderCard
import com.elendheim.notes.ui.components.MoveToFolderSheet
import com.elendheim.notes.ui.components.NameDialog
import com.elendheim.notes.ui.components.NoteActionsSheet
import com.elendheim.notes.ui.components.NoteCard
import com.elendheim.notes.ui.components.SectionLabel
import com.elendheim.notes.ui.components.SortMenuButton
import com.elendheim.notes.ui.components.SwipeableNoteCard
import com.elendheim.notes.ui.promptUnlock
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: NotesViewModel,
    onOpenNote: (Long) -> Unit,
    onOpenFolder: (Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val allFolders by viewModel.allFolders.collectAsStateWithLifecycle()
    val notes by viewModel.unfiledNotes.collectAsStateWithLifecycle()
    val homeSort by viewModel.homeSort.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val activity = LocalContext.current as? FragmentActivity

    var searching by rememberSaveable { mutableStateOf(false) }
    var actionNote by remember { mutableStateOf<Note?>(null) }
    var movingNote by remember { mutableStateOf<Note?>(null) }
    var coloringNote by remember { mutableStateOf<Note?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var newFolderForNote by remember { mutableStateOf<Note?>(null) }
    var actionFolderId by remember { mutableStateOf<Long?>(null) }
    var renamingFolderId by remember { mutableStateOf<Long?>(null) }
    var deletingFolderId by remember { mutableStateOf<Long?>(null) }

    val focusRequester = remember { FocusRequester() }

    fun deleteWithUndo(note: Note) {
        viewModel.deleteNote(note)
        scope.launch {
            val result = snackbar.showSnackbar(
                message = "Note deleted",
                actionLabel = "Undo",
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        }
    }

    fun openFolderGated(folderId: Long, locked: Boolean) {
        if (!locked) {
            onOpenFolder(folderId)
        } else if (activity != null) {
            promptUnlock(activity, "Unlock folder") { onOpenFolder(folderId) }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { Text("Notes") },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    SortMenuButton(current = homeSort) { viewModel.setSort("home", it) }
                    IconButton(onClick = { showNewFolder = true }) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = "New folder")
                    }
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) viewModel.setQuery("")
                    }) {
                        Icon(
                            if (searching) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = if (searching) "Close search" else "Search"
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch { onOpenNote(viewModel.createNote(null)) }
                },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onBackground
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "New note")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (searching) {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text("Search all notes") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                }
            }

            if (searching && query.isNotBlank()) {
                if (searchResults.isEmpty()) {
                    item(key = "no-results") { EmptyState("Nothing found.") }
                } else {
                    items(searchResults, key = { "s${it.id}" }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { onOpenNote(note.id) },
                            onLongPress = { actionNote = note },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            } else {
                if (folders.isNotEmpty()) {
                    item(key = "folders-label") { SectionLabel("Folders") }
                    items(folders, key = { "f${it.folder.id}" }) { entry ->
                        FolderCard(
                            name = entry.folder.name,
                            noteCount = entry.noteCount,
                            locked = entry.folder.locked,
                            onClick = { openFolderGated(entry.folder.id, entry.folder.locked) },
                            onLongPress = { actionFolderId = entry.folder.id },
                            modifier = Modifier.animateItem()
                        )
                    }
                    item(key = "notes-label") {
                        Box(Modifier.padding(top = 12.dp)) { SectionLabel("Notes") }
                    }
                }
                if (notes.isEmpty() && folders.isEmpty()) {
                    item(key = "empty") {
                        EmptyState("No notes yet. Tap the plus button to write your first one.")
                    }
                } else if (notes.isEmpty()) {
                    item(key = "empty-notes") { EmptyState("No loose notes.") }
                } else {
                    items(notes, key = { "n${it.id}" }) { note ->
                        SwipeableNoteCard(
                            note = note,
                            onClick = { onOpenNote(note.id) },
                            onLongPress = { actionNote = note },
                            onDelete = { deleteWithUndo(note) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }

    actionNote?.let { note ->
        NoteActionsSheet(
            note = note,
            onPinToggle = {
                viewModel.togglePin(note)
                actionNote = null
            },
            onMove = {
                movingNote = note
                actionNote = null
            },
            onColor = {
                coloringNote = note
                actionNote = null
            },
            onDelete = {
                actionNote = null
                deleteWithUndo(note)
            },
            onDismiss = { actionNote = null }
        )
    }

    movingNote?.let { note ->
        MoveToFolderSheet(
            folders = allFolders,
            currentFolderId = note.folderId,
            onSelect = { folderId ->
                viewModel.moveNote(note, folderId)
                movingNote = null
            },
            onNewFolder = {
                newFolderForNote = note
                movingNote = null
            },
            onDismiss = { movingNote = null }
        )
    }

    coloringNote?.let { note ->
        ColorPickerSheet(
            current = note.color,
            onSelect = { chosen ->
                viewModel.setNoteColor(note, chosen)
                coloringNote = null
            },
            onDismiss = { coloringNote = null }
        )
    }

    if (showNewFolder) {
        NameDialog(
            title = "New folder",
            confirmLabel = "Create",
            onConfirm = { name ->
                viewModel.createFolder(name)
                showNewFolder = false
            },
            onDismiss = { showNewFolder = false }
        )
    }

    newFolderForNote?.let { note ->
        NameDialog(
            title = "New folder",
            confirmLabel = "Create and move",
            onConfirm = { name ->
                viewModel.createFolder(name) { id -> viewModel.moveNote(note, id) }
                newFolderForNote = null
            },
            onDismiss = { newFolderForNote = null }
        )
    }

    actionFolderId?.let { folderId ->
        val entry = folders.firstOrNull { it.folder.id == folderId }
        if (entry == null) {
            actionFolderId = null
        } else {
            FolderActionsSheet(
                folderName = entry.folder.name,
                locked = entry.folder.locked,
                onRename = {
                    renamingFolderId = folderId
                    actionFolderId = null
                },
                onToggleLock = {
                    actionFolderId = null
                    if (entry.folder.locked) {
                        if (activity != null) {
                            promptUnlock(activity, "Remove folder lock") {
                                viewModel.setFolderLocked(folderId, false)
                            }
                        }
                    } else if (activity != null && com.elendheim.notes.ui.canUseDeviceLock(activity)) {
                        viewModel.setFolderLocked(folderId, true)
                    } else {
                        scope.launch {
                            snackbar.showSnackbar("Set up a screen lock or fingerprint first")
                        }
                    }
                },
                onDelete = {
                    deletingFolderId = folderId
                    actionFolderId = null
                },
                onDismiss = { actionFolderId = null }
            )
        }
    }

    renamingFolderId?.let { folderId ->
        val current = folders.firstOrNull { it.folder.id == folderId } ?: run {
            renamingFolderId = null
            return@let
        }
        NameDialog(
            title = "Rename folder",
            confirmLabel = "Rename",
            initialValue = current.folder.name,
            onConfirm = { name ->
                viewModel.renameFolder(folderId, name)
                renamingFolderId = null
            },
            onDismiss = { renamingFolderId = null }
        )
    }

    deletingFolderId?.let { folderId ->
        ConfirmDialog(
            title = "Delete folder",
            message = "Notes inside will move back to the main list.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deleteFolder(folderId)
                deletingFolderId = null
            },
            onDismiss = { deletingFolderId = null }
        )
    }
}
