package com.elendheim.notes.data

import org.json.JSONArray
import org.json.JSONObject

data class ImportResult(val notesAdded: Int, val notesSkipped: Int, val foldersAdded: Int)

// Everything in one plain JSON file the owner keeps wherever they like.
class BackupManager(private val repo: NotesRepository) {

    suspend fun exportJson(): String {
        val root = JSONObject()
        root.put("format", "elendheim-notes")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val folderArray = JSONArray()
        for (folder in repo.allFolders()) {
            folderArray.put(
                JSONObject()
                    .put("id", folder.id)
                    .put("name", folder.name)
                    .put("locked", folder.locked)
                    .put("createdAt", folder.createdAt)
            )
        }
        root.put("folders", folderArray)

        val noteArray = JSONArray()
        for (note in repo.allNotes()) {
            noteArray.put(
                JSONObject()
                    .put("folderId", note.folderId ?: JSONObject.NULL)
                    .put("title", note.title)
                    .put("body", note.body)
                    .put("pinned", note.pinned)
                    .put("color", note.color ?: JSONObject.NULL)
                    .put("createdAt", note.createdAt)
                    .put("updatedAt", note.updatedAt)
            )
        }
        root.put("notes", noteArray)
        return root.toString(2)
    }

    suspend fun importJson(text: String): ImportResult {
        val root = JSONObject(text)
        require(root.optString("format") == "elendheim-notes") { "Not an Elendheim Notes backup" }

        // Reuse folders that already exist under the same name.
        val existing = repo.allFolders().associateBy({ it.name.lowercase() }, { it.id }).toMutableMap()
        val idMap = mutableMapOf<Long, Long>()
        var foldersAdded = 0

        val folderArray = root.optJSONArray("folders") ?: JSONArray()
        for (i in 0 until folderArray.length()) {
            val f = folderArray.getJSONObject(i)
            val oldId = f.getLong("id")
            val name = f.getString("name")
            val known = existing[name.lowercase()]
            if (known != null) {
                idMap[oldId] = known
            } else {
                val newId = repo.createFolder(name)
                if (f.optBoolean("locked", false)) repo.setFolderLocked(newId, true)
                existing[name.lowercase()] = newId
                idMap[oldId] = newId
                foldersAdded++
            }
        }

        var added = 0
        var skipped = 0
        val noteArray = root.optJSONArray("notes") ?: JSONArray()
        for (i in 0 until noteArray.length()) {
            val n = noteArray.getJSONObject(i)
            val title = n.optString("title")
            val body = n.optString("body")
            val createdAt = n.optLong("createdAt", System.currentTimeMillis())
            if (repo.noteExists(title, body, createdAt)) {
                skipped++
                continue
            }
            val oldFolderId = if (n.isNull("folderId")) null else n.getLong("folderId")
            repo.insertNote(
                Note(
                    folderId = oldFolderId?.let { idMap[it] },
                    title = title,
                    body = body,
                    pinned = n.optBoolean("pinned", false),
                    color = if (n.isNull("color")) null else n.optString("color"),
                    createdAt = createdAt,
                    updatedAt = n.optLong("updatedAt", createdAt)
                )
            )
            added++
        }
        return ImportResult(added, skipped, foldersAdded)
    }
}
