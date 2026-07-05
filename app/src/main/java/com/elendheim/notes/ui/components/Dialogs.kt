package com.elendheim.notes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elendheim.notes.data.Folder
import com.elendheim.notes.data.Note
import com.elendheim.notes.ui.theme.NoteTagColors
import com.elendheim.notes.ui.theme.parseTagColor

@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    initialValue: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by rememberSaveable { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (value.isNotBlank()) onConfirm(value.trim()) }
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteActionsSheet(
    note: Note,
    onPinToggle: () -> Unit,
    onMove: () -> Unit,
    onColor: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = note.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            SheetAction(
                icon = Icons.Outlined.PushPin,
                label = if (note.pinned) "Unpin" else "Pin",
                onClick = onPinToggle
            )
            SheetAction(
                icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                label = "Move to folder",
                onClick = onMove
            )
            SheetAction(
                icon = Icons.Outlined.Palette,
                label = "Color tag",
                onClick = onColor
            )
            SheetAction(
                icon = Icons.Outlined.Delete,
                label = "Delete",
                onClick = onDelete,
                danger = true
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    current: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Color tag",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ColorDot(color = null, selected = current == null, onClick = { onSelect(null) })
                NoteTagColors.forEach { (hex, _) ->
                    ColorDot(
                        color = parseTagColor(hex),
                        selected = current == hex,
                        onClick = { onSelect(hex) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderActionsSheet(
    folderName: String,
    locked: Boolean,
    onRename: () -> Unit,
    onToggleLock: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = folderName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            SheetAction(
                icon = Icons.Outlined.DriveFileRenameOutline,
                label = "Rename",
                onClick = onRename
            )
            SheetAction(
                icon = if (locked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                label = if (locked) "Remove lock" else "Lock folder",
                onClick = onToggleLock
            )
            SheetAction(
                icon = Icons.Outlined.Delete,
                label = "Delete folder",
                onClick = onDelete,
                danger = true
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToFolderSheet(
    folders: List<Folder>,
    currentFolderId: Long?,
    onSelect: (Long?) -> Unit,
    onNewFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = "Move to",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            LazyColumn {
                if (currentFolderId != null) {
                    item {
                        SheetAction(
                            icon = Icons.Outlined.FolderOff,
                            label = "No folder",
                            onClick = { onSelect(null) }
                        )
                    }
                }
                items(folders.filter { it.id != currentFolderId }, key = { it.id }) { folder ->
                    SheetAction(
                        icon = Icons.Outlined.Folder,
                        label = folder.name,
                        onClick = { onSelect(folder.id) }
                    )
                }
                item {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        TextButton(onClick = onNewFolder) { Text("New folder") }
                    }
                }
            }
        }
    }
}
