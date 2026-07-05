package com.elendheim.notes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.elendheim.notes.NotesApp
import com.elendheim.notes.data.BackupManager
import com.elendheim.notes.data.Folder
import com.elendheim.notes.data.FolderWithCount
import com.elendheim.notes.data.ImportResult
import com.elendheim.notes.data.Note
import com.elendheim.notes.data.NotesRepository
import com.elendheim.notes.data.Prefs
import com.elendheim.notes.data.SortMode
import com.elendheim.notes.data.sortNotes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(
    private val repo: NotesRepository,
    private val prefs: Prefs,
    private val backup: BackupManager
) : ViewModel() {

    val folders: StateFlow<List<FolderWithCount>> = repo.foldersWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allFolders: StateFlow<List<Folder>> = repo.folders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val homeSort: StateFlow<SortMode> = prefs.sort("home")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SortMode.EDITED)

    val unfiledNotes: StateFlow<List<Note>> =
        combine(repo.unfiledNotes(), prefs.sort("home")) { notes, mode -> sortNotes(notes, mode) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val appLock: StateFlow<Boolean?> = prefs.appLock
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query

    val searchResults: StateFlow<List<Note>> = query
        .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else repo.search(q.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Held so a deletion can be undone from the snackbar.
    private val lastDeleted = MutableStateFlow<Note?>(null)

    fun setQuery(value: String) {
        query.value = value
    }

    fun sortFor(listKey: String): Flow<SortMode> = prefs.sort(listKey)

    fun setSort(listKey: String, mode: SortMode) {
        viewModelScope.launch { prefs.setSort(listKey, mode) }
    }

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch { prefs.setAppLock(enabled) }
    }

    fun sortedNotesInFolder(folderId: Long): Flow<List<Note>> =
        combine(repo.notesInFolder(folderId), prefs.sort("f$folderId")) { notes, mode ->
            sortNotes(notes, mode)
        }

    fun folderById(folderId: Long): Flow<Folder?> = repo.folderById(folderId)

    suspend fun createNote(folderId: Long?): Long = repo.createNote(folderId)

    suspend fun noteById(id: Long): Note? = repo.noteById(id)

    fun saveNote(note: Note) {
        viewModelScope.launch { repo.updateNote(note) }
    }

    suspend fun saveNoteNow(note: Note) = repo.updateNote(note)

    suspend fun deleteNoteNow(id: Long) = repo.deleteNoteById(id)

    fun togglePin(note: Note) {
        viewModelScope.launch { repo.updateNote(note.copy(pinned = !note.pinned)) }
    }

    fun moveNote(note: Note, folderId: Long?) {
        viewModelScope.launch { repo.updateNote(note.copy(folderId = folderId)) }
    }

    fun setNoteColor(note: Note, color: String?) {
        viewModelScope.launch { repo.updateNote(note.copy(color = color)) }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            lastDeleted.value = note
            repo.deleteNote(note)
        }
    }

    fun undoDelete() {
        val note = lastDeleted.value ?: return
        lastDeleted.value = null
        viewModelScope.launch { repo.restoreNote(note) }
    }

    fun createFolder(name: String, onCreated: (Long) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch { onCreated(repo.createFolder(name)) }
    }

    fun renameFolder(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.renameFolder(id, name) }
    }

    fun setFolderLocked(id: Long, locked: Boolean) {
        viewModelScope.launch { repo.setFolderLocked(id, locked) }
    }

    fun deleteFolder(id: Long) {
        viewModelScope.launch { repo.deleteFolder(id) }
    }

    suspend fun exportJson(): String = backup.exportJson()

    suspend fun importJson(text: String): ImportResult = backup.importJson(text)

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[APPLICATION_KEY] as NotesApp
                return NotesViewModel(app.repository, app.prefs, app.backup) as T
            }
        }
    }
}
