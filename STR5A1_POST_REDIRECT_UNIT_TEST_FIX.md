# PersonalMedia Android — STR-5A.1 POST redirect test reliability fix

## Reported failure

The user's STR-5A Android Studio build compiled `:app:compileDebugKotlin` and all four native ABI variants, then failed in `:app:testDebugUnitTest` at:

```
StremioJsonHttpTest > rejectsRedirectedPostWithoutSendingBodyToNextEndpoint FAILED
    java.lang.AssertionError at StremioJsonHttpTest.kt:44
42 tests completed, 1 failed
```

This is a **unit-test failure**, not a C++ / JNI compiler failure. One reason the test can be timing-sensitive is that its fixture replied to POST before consuming the request body. Closing a socket with unread data may result in a TCP reset and a transparent JVM HTTP retry. The actual failure mechanism on the ARM64 Windows VM is not conclusively proven without the JUnit XML assertion details.

## Changed files

- `app/src/main/java/com/personalmedia/android/StremioJsonHttp.kt`: configure credential-bearing POST as fixed-length streaming, disabling transparent buffering and automatic resend by `HttpURLConnection`. Continue to reject all 301/302/303/307/308 POST redirects and never include credentials in error text.
- `app/src/test/java/com/personalmedia/android/StremioJsonHttpTest.kt`: consume the fixture's declared POST body before replying; assert that credentials were received at the original endpoint only and that `/destination` was **not** contacted.

No edits to native libtorrent/MPV, T405 Freeview, Stremio layouts, signed application identity, Gradle version, or native binaries.

## Installation using Windows PowerShell

From the existing source folder after applying STR-5A:

```powershell
cd "C:\Users\OseasyVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\OseasyVM\Downloads\PersonalMedia_STR5A1_Post_Redirect_Test_Fix_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --stacktrace
.\tools\Verify-UniversalApk.ps1
```

The patch does not uninstall the existing APK or reset Stremio logins. If the Gradle command succeeds, install the updated APK as an update, not a fresh installation.

## Verification performed here

- Compiled the real `StremioJsonHttp`, redirect policy, and JUnit fixture on a host JVM using Kotlin compiler with a minimal local JUnit assertion harness.
- Both redirect integration tests passed for 150 consecutive iterations (300 test executions total).
- The full Android Studio Gradle test suite and device validation are still for the user to run on their Windows ARM64 VM.

**Remaining STR-5 limitation:** libtorrent playback is not enabled just because the APK builds; Android-target libtorrent dependencies remain a separate STR-5B integration task.
