package com.elendheim.notes

import android.app.Application
import com.elendheim.notes.data.NotesDatabase
import com.elendheim.notes.data.NotesRepository

class NotesApp : Application() {

    val repository: NotesRepository by lazy {
        NotesRepository(NotesDatabase.get(this))
    }
}
