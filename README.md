<p align="center">
  <img src="art/logo.png" width="96" alt="Elendheim Notes logo">
</p>

# Elendheim Notes

A simple, private note app for Android. Notes are stored on your phone and nowhere else.

## What it does

- Write notes with a title and a body. Everything autosaves as you type.
- Group notes into folders when a flat list stops being enough.
- Pin the notes you keep coming back to.
- Search across every note from the home screen.
- Delete with undo, so a slip of the finger costs nothing.

## What it does not do

- No accounts, no sync, no cloud.
- No network permission at all. The app cannot send your notes anywhere, even if it wanted to.
- No analytics, no tracking, no ads.

## Design

Dark interface with a soft purple accent. Built with Jetpack Compose and Material 3, storage handled by Room (SQLite). Works on Android 8.0 (API 26) and up.

## Building

Open the project in Android Studio, or from the command line:

```
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Every push also builds an APK on GitHub Actions; grab it from the workflow run's artifacts.

## License

MIT
