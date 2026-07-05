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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elendheim.notes.ui.NotesViewModel
import com.elendheim.notes.ui.AppLockState
import com.elendheim.notes.ui.canUseDeviceLock
import com.elendheim.notes.ui.promptUnlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: NotesViewModel,
    onOpenExport: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val appLock by viewModel.appLock.collectAsStateWithLifecycle()
    val lockAvailable = remember { canUseDeviceLock(context) }

    fun runImport(uri: Uri?) {
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.readBytes().toString(Charsets.UTF_8)
                        } ?: error("Could not read the file")
                    }
                    viewModel.importJson(text)
                }
                val imported = result.getOrNull()
                snackbar.showSnackbar(
                    when {
                        imported == null ->
                            "Import failed: ${result.exceptionOrNull()?.message}"
                        imported.notesAdded == 0 && imported.notesSkipped > 0 ->
                            "Nothing new to import"
                        else ->
                            "Imported ${imported.notesAdded} notes" +
                                (if (imported.foldersAdded > 0) " and ${imported.foldersAdded} folders" else "") +
                                (if (imported.notesSkipped > 0) ", skipped ${imported.notesSkipped} duplicates" else "")
                    }
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> runImport(uri) }

    // Fallback for phones without a documents app: the generic content picker,
    // which any file manager can answer.
    val importFallbackLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> runImport(uri) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SectionHeader("Security")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Require unlock to open", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (lockAvailable) "Fingerprint, face, or your screen lock"
                        else "Set up a screen lock or fingerprint on your phone first",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = appLock == true,
                    enabled = lockAvailable,
                    onCheckedChange = { wanted ->
                        if (!wanted && activity != null) {
                            promptUnlock(activity, "Confirm to turn off the lock") {
                                viewModel.setAppLock(false)
                            }
                        } else {
                            viewModel.setAppLock(wanted)
                            AppLockState.lastAuth = System.currentTimeMillis()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            SectionHeader("Backup")
            SettingsAction(
                title = "Export notes",
                caption = "Pick which notes to save as a text file or an importable backup"
            ) {
                onOpenExport()
            }
            SettingsAction(
                title = "Import notes",
                caption = "Adds notes from a backup file; duplicates are skipped"
            ) {
                runCatching {
                    importLauncher.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream")
                    )
                }.recoverCatching {
                    importFallbackLauncher.launch("*/*")
                }.onFailure {
                    scope.launch { snackbar.showSnackbar("No file picker available on this phone") }
                }
            }

            SectionHeader("About")
            Text(
                text = "Elendheim Notes 2.3",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                text = "Everything stays on this phone. The app has no network permission, " +
                    "so your notes cannot leave the device. Open source under the MIT license.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsAction(title: String, caption: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
