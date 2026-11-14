# PersonalMedia Android — STR-2B JVM test compatibility fix

## Symptom

The Windows Android Studio build completed `:app:assembleDebug` but `:app:compileDebugUnitTestKotlin` failed with `Unresolved reference 'sun'` and `Unresolved reference 'HttpServer'` in `StremioJsonHttpTest.kt`.

## Fix

Replaced the JVM unit test's use of the optional `com.sun.net.httpserver.HttpServer` with a small `java.net.ServerSocket` fixture, compatible with the normal Java 17 test classpath. It checks that catalogue HTTP 307 follows to JSON and credential-bearing account POSTs cannot follow the redirect. No new dependencies or Gradle settings required.

**Only the JVM test source is changed**; production Stremio networking, add-on handling, MPV, Live TV, Guardian, saved channels, app identity, signing and libraries are unchanged.

## Apply over the STR-2B Cinemeta 307 patch

```powershell
cd "C:\Users\OseasyVM\Downloads\PersonalMedia"
Expand-Archive -Path "C:\Users\OseasyVM\Downloads\PersonalMedia_Android_STR2B_JVM_Test_Fix_Overlay.zip" -DestinationPath . -Force
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --stacktrace
```

This corrects the unit test source; **rebuild and run the tests on the Windows VM** to validate in your actual Gradle environment. The APK from the earlier failed combined command may already have been generated, but run the combined command again to get a clean pass.

Then check PersonalMedia → Stremio → Discover → Movies → Cinemeta · Popular on the phone. This patch does not itself change HTTP redirects or UI behaviour; it only repairs the failing JVM test.

## Local verification

Compiled the production HTTP/redirect policy classes and both test files using Kotlin/JVM and a temporary minimal JUnit compatibility stub. Executed two local network tests plus seven policy tests successfully. This is not a substitute for the device-side Android Studio Gradle run.
