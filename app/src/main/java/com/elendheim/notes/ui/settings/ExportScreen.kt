package com.elendheim.notes.ui.settings

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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

/** Writes a file into the public Downloads folder without any picker UI. */
private fun saveIntoDownloads(context: Context, fileName: String, mime: String, content: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        error("this phone has no file dialog and no direct Downloads access")
    }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: error("could not create a file in Downloads")
    resolver.openOutputStream(uri)?.use { stream ->
        stream.write(content.toByteArray(Charsets.UTF_8))
        stream.flush()
    } ?: error("could not write to Downloads")
}

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

    // Phones without a documents app cannot show the save dialog. Fall back
    // to writing straight into Downloads, no picker involved.
    fun saveToDownloads(fileName: String, mime: String, buildContent: suspend () -> String) {
        scope.launch {
            val result = runCatching {
                val content = buildContent()
                withContext(Dispatchers.IO) {
                    saveIntoDownloads(context, fileName, mime, content)
                }
            }
            snackbar.showSnackbar(
                if (result.isSuccess) "Saved to Downloads as $fileName"
                else "Export failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
            )
        }
    }

    fun exportWithFallback(
        launcher: () -> Unit,
        fileName: String,
        mime: String,
        buildContent: suspend () -> String
    ) {
        runCatching(launcher).onFailure {
            saveToDownloads(fileName, mime, buildContent)
        }
    }

    fun shareAsText() {
        scope.launch {
            runCatching {
                val content = viewModel.exportText(selected)
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, content)
                }
                context.startActivity(android.content.Intent.createChooser(send, "Share notes"))
            }.onFailure {
                snackbar.showSnackbar("Sharing failed: ${it.message ?: "unknown error"}")
            }
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
                    onClick = {
                        exportWithFallback(
                            launcher = { textLauncher.launch("elendheim-notes.txt") },
                            fileName = "elendheim-notes.txt",
                            mime = "text/plain"
                        ) { viewModel.exportText(selected) }
                    },
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
                    onClick = {
                        exportWithFallback(
                            launcher = { backupLauncher.launch("elendheim-notes-backup.json") },
                            fileName = "elendheim-notes-backup.json",
                            mime = "application/json"
                        ) { viewModel.exportJson(selected) }
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save as backup file (importable)")
                }
                TextButton(
                    onClick = { shareAsText() },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share as text instead")
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
