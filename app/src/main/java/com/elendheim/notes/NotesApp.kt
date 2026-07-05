package com.elendheim.notes

import android.app.Application
import com.elendheim.notes.data.BackupManager
import com.elendheim.notes.data.NotesDatabase
import com.elendheim.notes.data.NotesRepository
import com.elendheim.notes.data.Prefs

class NotesApp : Application() {

    val repository: NotesRepository by lazy {
        NotesRepository(NotesDatabase.get(this))
    }

    val prefs: Prefs by lazy { Prefs(this) }

    val backup: BackupManager by lazy { BackupManager(repository) }
}
