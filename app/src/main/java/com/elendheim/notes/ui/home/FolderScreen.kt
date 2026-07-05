package com.elendheim.notes.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elendheim.notes.data.Note
import com.elendheim.notes.ui.NotesViewModel
import com.elendheim.notes.ui.components.ConfirmDialog
import com.elendheim.notes.ui.components.EmptyState
import com.elendheim.notes.ui.components.MoveToFolderSheet
import com.elendheim.notes.ui.components.NameDialog
import com.elendheim.notes.ui.components.NoteActionsSheet
import com.elendheim.notes.ui.components.NoteCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    folderId: Long,
    viewModel: NotesViewModel,
    onOpenNote: (Long) -> Unit,
    onBack: () -> Unit
) {
    val folder by viewModel.folderById(folderId).collectAsStateWithLifecycle(initialValue = null)
    val notes by viewModel.notesInFolder(folderId).collectAsStateWithLifecycle(initialValue = emptyList())
    val allFolders by viewModel.allFolders.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var menuOpen by remember { mutableStateOf(false) }
    var actionNote by remember { mutableStateOf<Note?>(null) }
    var movingNote by remember { mutableStateOf<Note?>(null) }
    var newFolderForNote by remember { mutableStateOf<Note?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { Text(folder?.name ?: "") },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Folder options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menuOpen = false
                                renaming = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete folder", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                deleting = true
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch { onOpenNote(viewModel.createNote(folderId)) }
                },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onBackground
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "New note in folder")
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
            if (notes.isEmpty()) {
                item(key = "empty") { EmptyState("This folder is empty.") }
            } else {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        onClick = { onOpenNote(note.id) },
                        onLongPress = { actionNote = note },
                        modifier = Modifier.animateItem()
                    )
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
            onSelect = { targetId ->
                viewModel.moveNote(note, targetId)
                movingNote = null
            },
            onNewFolder = {
                newFolderForNote = note
                movingNote = null
            },
            onDismiss = { movingNote = null }
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

    if (renaming) {
        NameDialog(
            title = "Rename folder",
            confirmLabel = "Rename",
            initialValue = folder?.name ?: "",
            onConfirm = { name ->
                viewModel.renameFolder(folderId, name)
                renaming = false
            },
            onDismiss = { renaming = false }
        )
    }

    if (deleting) {
        ConfirmDialog(
            title = "Delete folder",
            message = "Notes inside will move back to the main list.",
            confirmLabel = "Delete",
            onConfirm = {
                deleting = false
                viewModel.deleteFolder(folderId)
                onBack()
            },
            onDismiss = { deleting = false }
        )
    }
}
