package com.elendheim.notes.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NotesDao {

    @Query("SELECT * FROM notes WHERE folderId IS NULL ORDER BY pinned DESC, updatedAt DESC")
    fun unfiledNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE folderId = :folderId ORDER BY pinned DESC, updatedAt DESC")
    fun notesInFolder(folderId: Long): Flow<List<Note>>

    // Locked folders keep their notes out of search results.
    @Query(
        "SELECT notes.* FROM notes LEFT JOIN folders ON notes.folderId = folders.id " +
            "WHERE (notes.title LIKE '%' || :query || '%' OR notes.body LIKE '%' || :query || '%') " +
            "AND (folders.locked IS NULL OR folders.locked = 0) " +
            "ORDER BY notes.pinned DESC, notes.updatedAt DESC"
    )
    fun search(query: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun noteById(id: Long): Note?

    @Query(
        "SELECT notes.* FROM notes LEFT JOIN folders ON notes.folderId = folders.id " +
            "WHERE notes.pinned = 1 AND (folders.locked IS NULL OR folders.locked = 0) " +
            "ORDER BY notes.updatedAt DESC LIMIT :limit"
    )
    suspend fun pinnedUnlocked(limit: Int): List<Note>

    @Query(
        "SELECT notes.* FROM notes LEFT JOIN folders ON notes.folderId = folders.id " +
            "WHERE folders.locked IS NULL OR folders.locked = 0 " +
            "ORDER BY notes.updatedAt DESC"
    )
    suspend fun allUnlocked(): List<Note>

    @Query("SELECT * FROM notes ORDER BY createdAt")
    suspend fun allNotes(): List<Note>

    @Query("SELECT COUNT(*) FROM notes WHERE title = :title AND body = :body AND createdAt = :createdAt")
    suspend fun countMatching(title: String, body: String, createdAt: Long): Int

    @Insert
    suspend fun insert(note: Note): Long

    // Upsert, not update: a save must land even if the row went missing,
    // otherwise edits can be silently dropped.
    @Upsert
    suspend fun upsert(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface FoldersDao {

    @Query(
        "SELECT folders.*, COUNT(notes.id) AS noteCount FROM folders " +
            "LEFT JOIN notes ON notes.folderId = folders.id " +
            "GROUP BY folders.id ORDER BY folders.name COLLATE NOCASE"
    )
    fun foldersWithCounts(): Flow<List<FolderWithCount>>

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun folders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :id")
    fun folderById(id: Long): Flow<Folder?>

    @Query("SELECT * FROM folders ORDER BY createdAt")
    suspend fun allFolders(): List<Folder>

    @Insert
    suspend fun insert(folder: Folder): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE folders SET locked = :locked WHERE id = :id")
    suspend fun setLocked(id: Long, locked: Boolean)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)
}
