package com.elendheim.notes.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val locked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = Folder::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("folderId")]
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long? = null,
    val title: String = "",
    val body: String = "",
    val pinned: Boolean = false,
    val color: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class FolderWithCount(
    @Embedded val folder: Folder,
    @ColumnInfo(name = "noteCount") val noteCount: Int
)
