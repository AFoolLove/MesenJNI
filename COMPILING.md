## Building the headless core (CMake)

This project builds the native emulator core as a shared library (`MesenCore`).
The .NET/Avalonia UI is intentionally not part of this project.

Requirements:

* CMake 3.15 or newer
* A C++17 compiler (MSVC 2022, Clang, or GCC)
* Linux/macOS additionally require SDL2

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
