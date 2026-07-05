package com.elendheim.notes.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Palette
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elendheim.notes.data.BodyLine
import com.elendheim.notes.data.Note
import com.elendheim.notes.data.parseBody
import com.elendheim.notes.data.serializeBody
import com.elendheim.notes.data.wordCount
import com.elendheim.notes.ui.NotesViewModel
import com.elendheim.notes.ui.components.ColorPickerSheet
import com.elendheim.notes.ui.components.ConfirmDialog
import com.elendheim.notes.ui.components.MoveToFolderSheet
import com.elendheim.notes.ui.components.NameDialog
import com.elendheim.notes.ui.components.relativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private data class EditorLine(val id: Long, val checked: Boolean?, val text: String)

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun EditorScreen(
    noteId: Long,
    viewModel: NotesViewModel,
    onBack: () -> Unit
) {
    val allFolders by viewModel.allFolders.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf<Note?>(null) }
    var title by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(false) }
    var folderId by remember { mutableStateOf<Long?>(null) }
    var color by remember { mutableStateOf<String?>(null) }
    var savedAt by remember { mutableStateOf(0L) }
    var savedTick by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }

    val lines = remember { mutableStateListOf<EditorLine>() }
    var nextLineId by remember { mutableStateOf(1L) }
    fun newLineId(): Long = nextLineId++

    // Which line should grab focus, and where the cursor should land in it.
    var pendingFocus by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    var focusedLineId by remember { mutableStateOf<Long?>(null) }

    val bodyText by remember { derivedStateOf { serializeBody(lines.map { BodyLine(it.checked, it.text) }) } }
    val words by remember { derivedStateOf { wordCount(bodyText) } }
    fun contentIsBlank() = title.isBlank() && lines.all { it.text.isBlank() }

    val titleFocus = remember { FocusRequester() }

    LaunchedEffect(noteId) {
        val note = viewModel.noteById(noteId)
        if (note == null) {
            onBack()
            return@LaunchedEffect
        }
        title = note.title
        pinned = note.pinned
        folderId = note.folderId
        color = note.color
        savedAt = note.updatedAt
        lines.clear()
        lines.addAll(parseBody(note.body).map { EditorLine(newLineId(), it.checked, it.text) })
        if (lines.isEmpty()) lines.add(EditorLine(newLineId(), null, ""))
        loaded = note
    }

    fun buildNote(base: Note): Note = base.copy(
        title = title,
        body = bodyText,
        pinned = pinned,
        folderId = folderId,
        color = color,
        updatedAt = System.currentTimeMillis()
    )

    // Debounced autosave while typing, with the quiet saved tick.
    LaunchedEffect(loaded) {
        val base = loaded ?: return@LaunchedEffect
        snapshotFlow { listOf(title, bodyText, pinned, folderId, color) }
            .drop(1)
            .debounce(400)
            .collect {
                if (!deleted) {
                    val updated = buildNote(base)
                    viewModel.saveNote(updated)
                    savedAt = updated.updatedAt
                    savedTick = true
                }
            }
    }
    LaunchedEffect(savedTick, savedAt) {
        if (savedTick) {
            delay(1600)
            savedTick = false
        }
    }

    // Final save when the screen actually goes away, keyed on Unit so it can
    // never fire mid-session. Notes left completely empty are discarded.
    DisposableEffect(Unit) {
        onDispose {
            val base = loaded ?: return@onDispose
            if (deleted) return@onDispose
            runBlocking(Dispatchers.IO) {
                if (contentIsBlank()) {
                    viewModel.deleteNoteNow(base.id)
                } else {
                    viewModel.saveNoteNow(buildNote(base))
                }
            }
        }
    }

    // Line operations -------------------------------------------------------

    fun indexOfLine(id: Long) = lines.indexOfFirst { it.id == id }

    fun changeText(id: Long, text: String) {
        val i = indexOfLine(id)
        if (i < 0) return
        val line = lines[i]
        // Typing "- " at the start of a plain line turns it into a checkbox.
        if (line.checked == null && text.startsWith("- ")) {
            lines[i] = line.copy(checked = false, text = text.removePrefix("- "))
            pendingFocus = id to (text.length - 2).coerceAtLeast(0)
        } else {
            lines[i] = line.copy(text = text)
        }
    }

    fun splitLine(id: Long, parts: List<String>) {
        val i = indexOfLine(id)
        if (i < 0 || parts.size < 2) return
        val line = lines[i]
        // Enter on an empty checklist item steps out of the checklist.
        if (line.checked != null && line.text.isBlank() && parts.all { it.isBlank() }) {
            lines[i] = line.copy(checked = null, text = "")
            pendingFocus = id to 0
            return
        }
        lines[i] = line.copy(text = parts.first())
        val inherited = if (line.checked != null) false else null
        val newLines = parts.drop(1).map { EditorLine(newLineId(), inherited, it) }
        lines.addAll(i + 1, newLines)
        pendingFocus = newLines.first().id to 0
    }

    fun mergeBack(id: Long) {
        val i = indexOfLine(id)
        if (i <= 0) return
        val line = lines[i]
        val prev = lines[i - 1]
        val cursor = prev.text.length
        lines[i - 1] = prev.copy(text = prev.text + line.text)
        lines.removeAt(i)
        pendingFocus = prev.id to cursor
    }

    fun demoteToPlain(id: Long) {
        val i = indexOfLine(id)
        if (i < 0) return
        lines[i] = lines[i].copy(checked = null)
        pendingFocus = id to 0
    }

    fun toggleChecked(id: Long) {
        val i = indexOfLine(id)
        if (i < 0) return
        val line = lines[i]
        if (line.checked == null) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        lines[i] = line.copy(checked = !line.checked)
        // A fully checked list clears itself: the finished shopping run.
        val items = lines.filter { it.checked != null }
        if (items.isNotEmpty() && items.all { it.checked == true }) {
            scope.launch {
                delay(600)
                val still = lines.filter { it.checked != null }
                if (still.isNotEmpty() && still.all { it.checked == true }) {
                    lines.removeAll { it.checked == true }
                    if (lines.isEmpty()) lines.add(EditorLine(newLineId(), null, ""))
                }
            }
        }
    }

    fun toggleChecklistOnFocused() {
        val id = focusedLineId
        val i = id?.let { indexOfLine(it) } ?: -1
        if (i >= 0) {
            val line = lines[i]
            lines[i] = if (line.checked == null) line.copy(checked = false) else line.copy(checked = null)
            pendingFocus = line.id to lines[i].text.length
        } else {
            val newLine = EditorLine(newLineId(), false, "")
            lines.add(newLine)
            pendingFocus = newLine.id to 0
        }
    }

    // UI ---------------------------------------------------------------------

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (savedAt > 0) {
                            Text(
                                text = "Edited ${relativeTime(savedAt).replaceFirstChar { it.lowercase() }}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AnimatedVisibility(
                            visible = savedTick,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Saved",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .size(14.dp)
                            )
                        }
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
                    IconButton(onClick = { toggleChecklistOnFocused() }) {
                        Icon(
                            Icons.Outlined.CheckBox,
                            contentDescription = "Toggle checklist line",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                    IconButton(onClick = { picking = true }) {
                        Icon(
                            Icons.Outlined.Palette,
                            contentDescription = "Color tag",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = if (words == 1) "1 word" else "$words words",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            BasicTextField(
                value = title,
                onValueChange = { title = it.replace("\n", " ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = {
                    lines.firstOrNull()?.let { pendingFocus = it.id to it.text.length }
                }),
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
                    .focusRequester(titleFocus)
                    .padding(top = 8.dp, bottom = 16.dp)
            )

            lines.forEachIndexed { index, line ->
                key(line.id) {
                    LineEditor(
                        line = line,
                        isFirst = index == 0,
                        showPlaceholder = lines.size == 1 && line.text.isEmpty() && line.checked == null,
                        focusTo = pendingFocus?.takeIf { it.first == line.id }?.second,
                        onFocusHandled = { pendingFocus = null },
                        onFocused = { focusedLineId = line.id },
                        onChange = { changeText(line.id, it) },
                        onSplit = { splitLine(line.id, it) },
                        onMergeBack = { mergeBack(line.id) },
                        onDemote = { demoteToPlain(line.id) },
                        onToggle = { toggleChecked(line.id) }
                    )
                }
            }

            // Tapping the empty space below the text puts the cursor at the end.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        lines.lastOrNull()?.let { pendingFocus = it.id to it.text.length }
                    }
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

    if (picking) {
        ColorPickerSheet(
            current = color,
            onSelect = { chosen ->
                color = chosen
                picking = false
            },
            onDismiss = { picking = false }
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

@Composable
private fun LineEditor(
    line: EditorLine,
    isFirst: Boolean,
    showPlaceholder: Boolean,
    focusTo: Int?,
    onFocusHandled: () -> Unit,
    onFocused: () -> Unit,
    onChange: (String) -> Unit,
    onSplit: (List<String>) -> Unit,
    onMergeBack: () -> Unit,
    onDemote: () -> Unit,
    onToggle: () -> Unit
) {
    var tfv by remember { mutableStateOf(TextFieldValue(line.text)) }
    if (tfv.text != line.text) {
        tfv = tfv.copy(text = line.text, selection = TextRange(line.text.length.coerceAtMost(tfv.selection.end)))
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(focusTo) {
        if (focusTo != null) {
            tfv = tfv.copy(selection = TextRange(focusTo.coerceIn(0, line.text.length)))
            focusRequester.requestFocus()
            onFocusHandled()
        }
    }

    val checked = line.checked
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = if (checked == true) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onBackground,
        textDecoration = if (checked == true) TextDecoration.LineThrough else TextDecoration.None
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (checked != null) 4.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (checked != null) {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (checked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Checked",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
        BasicTextField(
            value = tfv,
            onValueChange = { new ->
                if (new.text.contains('\n')) {
                    onSplit(new.text.split('\n'))
                } else {
                    tfv = new
                    if (new.text != line.text) onChange(new.text)
                }
            },
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                Box {
                    if (showPlaceholder && tfv.text.isEmpty()) {
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
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        event.key == Key.Backspace &&
                        tfv.selection.start == 0 &&
                        tfv.selection.end == 0
                    ) {
                        when {
                            checked != null -> {
                                onDemote()
                                true
                            }
                            !isFirst -> {
                                onMergeBack()
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
        )
    }
}
