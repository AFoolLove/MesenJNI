# Mesen Community Edition

Mesen is a multi-system emulator for Windows, Linux, and macOS. It supports NES, SNES, Game Boy (GB/SGB/GBC), Game Boy Advance, PC Engine, SMS/Game Gear, and WonderSwan (WS/WSC).

This is a community-managed fork, created to maintain and expand this emulator into the future.

## Java / JNI bindings

This fork builds the headless core as a **single shared library with JNI bindings**,
so a Java or Android host can drive the emulator directly — one `.dll`/`.so`, no
second native library and no .NET UI.

* [JAVA_API.md](JAVA_API.md) — API overview, build instructions (including Android),
  the test suite, known core quirks and per-console verification status
* [example/java-swing/](example/java-swing/) — runnable desktop player (Swing)
* [example/android/](example/android/) — minimal Android integration

```java
MesenCore.initDll();
MesenCore.initializeEmu(homeFolder, true, false, false, false);
MesenCore.loadRom("/path/to/game.nes", null);
MesenCore.resume();
```

## Relationship to upstream

This repository is a fork of
[nesdev-org/MesenCE](https://github.com/nesdev-org/MesenCE) that keeps only the
emulation core and adds the Java/JNI bindings. It does **not** contain the
.NET/Avalonia UI, so there are no ready-made emulator binaries here — build the
library as described in [COMPILING.md](COMPILING.md).

For the upstream emulator with its GUI and release builds for Windows, Linux and
macOS, see the [upstream releases page](https://github.com/nesdev-org/MesenCE/releases).

## Compiling

See [COMPILING.md](COMPILING.md)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md)

## License

Mesen is available under the GPL V3 license.  Full text here: <http://www.gnu.org/licenses/gpl-3.0.en.html>

Copyright (C) 2014-2026 Sour, 2026 contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
