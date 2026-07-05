package com.elendheim.notes

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elendheim.notes.ui.AppLockState
import com.elendheim.notes.ui.AppNav
import com.elendheim.notes.ui.LockScreen
import com.elendheim.notes.ui.NotesViewModel
import com.elendheim.notes.ui.promptUnlock
import com.elendheim.notes.ui.theme.NotesTheme
import com.elendheim.notes.widget.updateNoteWidgets

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val requestedNote = intent?.getLongExtra(EXTRA_NOTE_ID, -1L) ?: -1L
        val startNoteId = if (requestedNote > 0) requestedNote else null
        val startNewNote = intent?.getBooleanExtra(EXTRA_NEW_NOTE, false) == true

        setContent {
            NotesTheme {
                Root(activity = this, startNoteId = startNoteId, startNewNote = startNewNote)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        AppLockState.lastAuth = System.currentTimeMillis()
        updateNoteWidgets(this)
    }

    companion object {
        const val EXTRA_NOTE_ID = "noteId"
        const val EXTRA_NEW_NOTE = "newNote"
    }
}

@Composable
private fun Root(
    activity: FragmentActivity,
    startNoteId: Long?,
    startNewNote: Boolean
) {
    val viewModel: NotesViewModel = viewModel(factory = NotesViewModel.Factory)
    val lockEnabled by viewModel.appLock.collectAsStateWithLifecycle()
    var locked by remember { mutableStateOf(false) }

    // Lock when the app comes back after more than the grace period away.
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (lockEnabled == true &&
            System.currentTimeMillis() - AppLockState.lastAuth > AppLockState.GRACE_MS
        ) {
            locked = true
        }
    }
    LaunchedEffect(lockEnabled) {
        if (lockEnabled == true && AppLockState.lastAuth == 0L) locked = true
        if (lockEnabled == false) locked = false
    }

    if (locked) {
        LaunchedEffect(Unit) {
            promptUnlock(activity, "Unlock Elendheim Notes") {
                AppLockState.lastAuth = System.currentTimeMillis()
                locked = false
            }
        }
        LockScreen(
            onUnlockRequest = {
                promptUnlock(activity, "Unlock Elendheim Notes") {
                    AppLockState.lastAuth = System.currentTimeMillis()
                    locked = false
                }
            }
        )
    } else if (lockEnabled != null) {
        AppNav(
            viewModel = viewModel,
            startNoteId = startNoteId,
            startNewNote = startNewNote
        )
    }
}
