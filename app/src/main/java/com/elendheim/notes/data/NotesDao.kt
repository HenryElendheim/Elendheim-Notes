package com.elendheim.notes.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotesDao {

    @Query("SELECT * FROM notes WHERE folderId IS NULL ORDER BY pinned DESC, updatedAt DESC")
    fun unfiledNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE folderId = :folderId ORDER BY pinned DESC, updatedAt DESC")
    fun notesInFolder(folderId: Long): Flow<List<Note>>

    @Query(
        "SELECT * FROM notes WHERE title LIKE '%' || :query || '%' " +
            "OR body LIKE '%' || :query || '%' ORDER BY pinned DESC, updatedAt DESC"
    )
    fun search(query: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun noteById(id: Long): Note?

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

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

    @Insert
    suspend fun insert(folder: Folder): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)
}
