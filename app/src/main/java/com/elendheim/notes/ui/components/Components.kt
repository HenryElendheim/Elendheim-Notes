package com.elendheim.notes.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elendheim.notes.data.Note
import com.elendheim.notes.data.SortMode
import com.elendheim.notes.data.checklistProgress
import com.elendheim.notes.data.previewText
import com.elendheim.notes.ui.theme.parseTagColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun relativeTime(millis: Long): String {
    val now = System.currentTimeMillis()
    val minutes = (now - millis) / 60_000
    if (minutes < 1) return "Just now"
    if (minutes < 60) return "${minutes}m ago"
    val hours = minutes / 60
    if (hours < 24) return "${hours}h ago"
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    if (date == today.minusDays(1)) return "Yesterday"
    val pattern = if (date.year == today.year) "d MMM" else "d MMM yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val stripe = parseTagColor(note.color)
    val preview = remember(note.body) { previewText(note.body) }
    val progress = remember(note.body) { checklistProgress(note.body) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                }
            )
    ) {
        if (stripe != null) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(stripe)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (note.title.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (note.pinned) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (preview.isNotBlank()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = relativeTime(note.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (progress != null) {
                    Text(
                        text = "  ·  ${progress.first} of ${progress.second} done",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (progress.first == progress.second) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** NoteCard wrapped so a swipe from right to left deletes it. */
@Composable
fun SwipeableNoteCard(
    note: Note,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 24.dp)
                )
            }
        }
    ) {
        NoteCard(note = note, onClick = onClick, onLongPress = onLongPress)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderCard(
    name: String,
    noteCount: Int,
    locked: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (locked) Icons.Outlined.Lock else Icons.Outlined.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (noteCount == 1) "1 note" else "$noteCount notes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SortMenuButton(
    current: SortMode,
    onSelect: (SortMode) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Outlined.SwapVert, contentDescription = "Sort")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        listOf(
            SortMode.EDITED to "Last edited",
            SortMode.CREATED to "Newest first",
            SortMode.ALPHA to "A to Z"
        ).forEach { (mode, label) ->
            DropdownMenuItem(
                text = {
                    Text(
                        label,
                        color = if (mode == current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = {
                    onSelect(mode)
                    open = false
                }
            )
        }
    }
}

@Composable
fun ColorDot(color: Color?, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color ?: MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        if (color != null) Color(0xFF1A0F2E)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
            )
        }
        if (color == null && !selected) {
            Text(
                "-",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp)
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 24.dp)
    )
}
