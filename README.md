# Freedom-file-manager-
Free, no-ads, no-tracker file manager. Full storage access, GPL-3.0.

# Freedom File Manager

A free, ad-free, tracker-free file manager for Android.

No ads. No analytics. No network permission. No limitations.

## Features

- Browse internal and external storage
- Tap folders to enter, Back to go up
- Open files with any installed app (via Android's share/open system)
- Create new folders
- Sorted view: folders first, then files, alphabetical
- Light/Dark theme (follows system)
- Material 3 design

## Planned

- Rename, delete, copy, move
- Search within a folder
- Breadcrumb navigation
- File details dialog
- Real per-filetype icons
- Grid view

## Screenshots

_(to be added)_

## Permissions

| Permission | Why |
|---|---|
| `MANAGE_EXTERNAL_STORAGE` | Required to browse and modify all files on the device. Without this, a file manager can only see its own sandbox. |
| `READ_EXTERNAL_STORAGE` | Fallback for Android 10 and below. |
| `WRITE_EXTERNAL_STORAGE` | Fallback for Android 9 and below. |

**This app does not request the `INTERNET` permission.** It cannot phone home, because it has no network capability at all.

## Building

Requires JDK 17+ and the Android SDK (compileSdk 34, build-tools 34.0.0).

```bash
git clone https://github.com/YOUR_USERNAME/freedom-file-manager.git
cd freedom-file-manager
./gradlew assembleDebug

