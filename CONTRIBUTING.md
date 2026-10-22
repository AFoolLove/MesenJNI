# Contributing

This repository is a fork of [MesenCE](https://github.com/nesdev-org/MesenCE)
that keeps the emulation core and adds Java/JNI bindings. Bug reports and pull
requests for the bindings, the Java API, the tests and the documentation are
welcome. For the emulation core itself, consider whether the fix belongs upstream.

## Licensing

MesenCE is distributed under the GPL v3 (see [LICENSE](LICENSE)). Upstream asks
contributors to license their submissions under the MIT license as well, so the
core could be relicensed more permissively later; the same applies here.

## Style

C++ is formatted with `clang-format` and CI enforces it:

```
clang-format -i <file>                                          # one file
find ./ -iname '*.h' -o -iname '*.cpp' | xargs clang-format -i   # everything
```

CI runs **clang-format 20** (see `.github/workflows/clang-format-check.yml`), and
only files you touch need to be clean. Use that same version: 19 and 20 disagree
on a few hundred files in the tree, so formatting with 19 will reformat unrelated
code and make the diff noisy.

Naming conventions:

* `ExampleFunction`
* `exampleVariable`
* `_exampleMemberVariable`

Java follows the surrounding code: four spaces, braces on their own line, and a
Javadoc comment above each `public static native` declaration that is not
self-explanatory.

## Tests

The tests are plain `main()` classes under `java/`; see
[COMPILING.md](COMPILING.md) for how to build and run them. Please run the suite
before opening a pull request:

```
gradle compileJava
gradle smokeTest -PtestClass=DebugSmokeTest      # any test, by name
```

Tests that need a ROM skip themselves when `roms/` is empty, so a checkout with no
ROMs still runs the suite. If your change affects a console, say which ROM you
tested with.

## C++

* MSVC builds treat warnings as errors; resolve them before submitting.
* Avoid adding branches to hot paths (per-frame emulation code). Where practical,
  measure with `CoreRateTest` before and after.

## Commit messages

Explain why, not just what, and mention the test you used to verify the change.
