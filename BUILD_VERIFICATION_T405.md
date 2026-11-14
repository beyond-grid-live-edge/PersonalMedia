# Verification notes — T405 Live TV update

Verified in the build environment:

- Host native CMake test suite (Debug): all six tests passed.
- JNI bridge: host C++ syntax-check with JDK JNI headers passed.
- T405 Guardian, session controller and per-socket route selector: Kotlin compiler check against API-shaped Java stubs passed.
- The reference application source is not present in this Android-only ZIP. No proprietary August APK binaries have been copied.

Not verified here:

- Full Android Gradle build (no Android SDK/NDK in this execution environment), Android runtime, actual T405 Wi-Fi, Freeview demodulation, battery decoding or MPEG video decoder availability.

The user previously built and installed the existing PersonalMedia Android APK. This update must be compiled again in that same Android Studio environment.
