# Call Backup

A tiny Android app with two buttons:

- **Backup call log**: writes the whole call history to a JSON file you pick (Downloads, Drive, …).
- **Restore call log**: reads such a file back into the call log. A call is skipped if the
  same number already has an entry at the same time, so restoring twice is harmless.

No dependencies: one plain `Activity` plus the framework's `org.json`. Needs the
`READ_CALL_LOG` / `WRITE_CALL_LOG` permissions, which Play only allows for default
dialer apps, so this is meant to be sideloaded.

## Build

    ./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
    ./gradlew testDebugUnitTest

Jenkins: point a "Pipeline from SCM" job at this repo. The `Jenkinsfile` provisions JDK 17
and the Android SDK into `~/.android-ci` itself (same as ../workout-tracking). Tick
`RELEASE` for a signed release APK; it uses the same `android-release-keystore`,
`android-keystore-password`, `android-key-alias` and `android-key-password` credentials.

## Backup format

    {"version": 1, "calls": [{"number": "...", "name": "...", "date": 1700000000000,
                              "duration": 42, "type": 1}]}

`date` is epoch millis, `duration` seconds, `type` is `CallLog.Calls.*_TYPE`.
