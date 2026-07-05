package com.elendheim.notes.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elendheim.notes.data.Note
import com.elendheim.notes.ui.NotesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: NotesViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var folderNames by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching {
            val all = viewModel.allNotesForExport()
            folderNames = viewModel.allFoldersForExport().associateBy({ it.id }, { it.name })
            notes = all
            selected = all.map { it.id }.toSet()
        }
        loaded = true
    }

    // Writes the given content to the chosen file. Nothing in here is allowed
    // to escape as a crash; the worst outcome is an error snackbar.
    fun writeTo(uri: Uri?, buildContent: suspend () -> String) {
        if (uri == null) return
        scope.launch {
            val result = runCatching {
                val content = buildContent()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                        stream.write(content.toByteArray(Charsets.UTF_8))
                        stream.flush()
                    } ?: error("could not open the file")
                }
            }
            snackbar.showSnackbar(
                if (result.isSuccess) "Exported ${selected.size} ${if (selected.size == 1) "note" else "notes"}"
                else "Export failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
            )
        }
    }

    val textLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> writeTo(uri) { viewModel.exportText(selected) } }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> writeTo(uri) { viewModel.exportJson(selected) } }

    fun safeLaunch(block: () -> Unit) {
        runCatching(block).onFailure {
            scope.launch { snackbar.showSnackbar("No file picker available on this phone") }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Export notes") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { safeLaunch { textLauncher.launch("elendheim-notes.txt") } },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Save as text file")
                }
                OutlinedButton(
                    onClick = { safeLaunch { backupLauncher.launch("elendheim-notes-backup.json") } },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save as backup file (importable)")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selected.size} of ${notes.size} selected",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { selected = notes.map { it.id }.toSet() }) {
                    Text("Select all")
                }
                TextButton(onClick = { selected = emptySet() }) {
                    Text("Deselect all")
                }
            }

            if (loaded && notes.isEmpty()) {
                Text(
                    text = "Nothing to export yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }

            LazyColumn {
                items(notes, key = { it.id }) { note ->
                    val checked = note.id in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (checked) selected - note.id else selected + note.id
                            }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { wanted ->
                                selected = if (wanted) selected + note.id else selected - note.id
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                checkmarkColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = note.title.ifBlank { "Untitled" },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val folder = note.folderId?.let { folderNames[it] }
                            if (folder != null) {
                                Text(
                                    text = folder,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
