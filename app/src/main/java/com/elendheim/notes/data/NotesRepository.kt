package com.elendheim.notes.data

import kotlinx.coroutines.flow.Flow

class NotesRepository(private val db: NotesDatabase) {

    private val notes = db.notesDao()
    private val folders = db.foldersDao()

    fun unfiledNotes(): Flow<List<Note>> = notes.unfiledNotes()
    fun notesInFolder(folderId: Long): Flow<List<Note>> = notes.notesInFolder(folderId)
    fun search(query: String): Flow<List<Note>> = notes.search(query)
    suspend fun noteById(id: Long): Note? = notes.noteById(id)

    suspend fun createNote(folderId: Long?): Long = notes.insert(Note(folderId = folderId))
    suspend fun updateNote(note: Note) = notes.update(note)
    suspend fun deleteNote(note: Note) = notes.delete(note)
    suspend fun deleteNoteById(id: Long) = notes.deleteById(id)

    suspend fun restoreNote(note: Note) {
        notes.insert(note)
    }

    fun foldersWithCounts(): Flow<List<FolderWithCount>> = folders.foldersWithCounts()
    fun folders(): Flow<List<Folder>> = folders.folders()
    fun folderById(id: Long): Flow<Folder?> = folders.folderById(id)

    suspend fun createFolder(name: String): Long = folders.insert(Folder(name = name.trim()))
    suspend fun renameFolder(id: Long, name: String) = folders.rename(id, name.trim())
    suspend fun deleteFolder(id: Long) = folders.delete(id)
}
