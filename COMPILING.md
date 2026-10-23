## Building the headless core (CMake)

This project builds the native emulator core as a shared library (`MesenCore`).
The .NET/Avalonia UI is intentionally not part of this project.

The same build also produces the JNI bindings, so the library can be driven from
Java or Android; see [JAVA_API.md](JAVA_API.md).

Requirements:

* CMake 3.15 or newer
* A C++17 compiler (MSVC 2022, Clang, or GCC)
* Linux/macOS additionally require SDL2
* Android additionally requires the NDK and a JDK

### Windows (x64)

Use CMake 3.21 or newer (the CMake bundled with Visual Studio 2022 works):

```
cmake -S . -B build -G "Visual Studio 17 2022" -A x64
cmake --build build --config Release
```

The output library is written to `bin/win-x64/Release/MesenCore.dll`.

### Linux

Install SDL2, a C++17 compiler, and CMake, then run:

```
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build
```

The output library is `bin/linux-x64/Release/MesenCore.so`.

### macOS

Install SDL2 (e.g. via Homebrew) and CMake, then run:

```
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build
```

The output library is `bin/osx-<arch>/Release/MesenCore.dylib`.

### Android (headless)

Requires the Android NDK and a JDK:

```
cmake -S . -B build-android \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 \
  -DCMAKE_BUILD_TYPE=Release -DJAVA_HOME=/path/to/jdk
cmake --build build-android
```

The output library is `bin/android-arm64-v8a/Release/libMesenCore.so`.
`ANDROID_ABI` also accepts `armeabi-v7a`, `x86` and `x86_64`.

There is no native window, audio or input layer on Android: rendering, audio and
input all come from Java through the callback interfaces. Verified against NDK
28.2.13676358.

### Running the Java tests

The Java side is a Gradle project in `Java/`: the API and its JNI bindings live in
`Java/src/main/java`, the tests in `Java/src/test/java` and are plain `main()`
classes rather than JUnit tests. They need JDK 17 or newer and Gson (only
`mesen.MesenSettings` uses it). No jar is
committed: take `com.google.code.gson:gson:2.10` from Maven Central, either
through a Gradle project or by downloading it:

```
javac -encoding UTF-8 -cp "<path>/gson-2.10.jar" -d build/classes \
      Java/src/main/java/mesen/*.java Java/src/main/java/mesen/interfaces/*.java Java/src/main/java/mesen/debugger/*.java \
      Java/src/test/java/*.java example/java-swing/src/main/java/*.java

java -Djava.library.path=bin/win-x64/Release \
     -cp "build/classes;<path>/gson-2.10.jar" DebugSmokeTest
```

If you put the Java side in your own Gradle project, the dependency is
`implementation("com.google.code.gson:gson:2.10")` and the source directories are
`Java/src/main/java` plus `example/java-swing`.

Test ROMs go in `roms/` and the emulator's home folder is `./home`; both are
ignored by git, and tests that need a missing ROM skip (exit 0) rather than fail.
See [JAVA_API.md](JAVA_API.md) for the full list.

