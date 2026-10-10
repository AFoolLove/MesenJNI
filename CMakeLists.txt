# Mesen Community Edition — headless emulation core
#
# This builds the native emulator core as a shared library (`MesenCore`).
# The .NET/Avalonia UI is intentionally not part of this project.
#
# Build (Windows x64, MSVC):
#   cmake -S . -B build -A x64
#   cmake --build build --config Release
#
# Build (Linux):
#   cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
#   cmake --build build
#
# Build (Android, headless, arm64 — requires the NDK and a JDK):
#   cmake -S . -B build-android \
#     -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
#     -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 \
#     -DCMAKE_BUILD_TYPE=Release -DJAVA_HOME=/path/to/jdk
#   cmake --build build-android
#
# The library is written to bin/<platform>/<config>/ (matching the upstream
# Mesen layout), e.g. bin/win-x64/Release/MesenCore.dll.

cmake_minimum_required(VERSION 3.15)

project(MesenCore
    VERSION 2.2.1
    DESCRIPTION "Mesen Community Edition - headless emulation core"
    LANGUAGES C CXX)

if(APPLE)
    enable_language(OBJCXX)
endif()

# ---------------------------------------------------------------------------
# Global settings
# ---------------------------------------------------------------------------
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ---------------------------------------------------------------------------
# Platform identifier, used for the output directory (mirrors upstream layout)
# ---------------------------------------------------------------------------
if(WIN32)
    set(MESEN_OS "win")
elseif(APPLE)
    set(MESEN_OS "osx")
elseif(ANDROID)
    set(MESEN_OS "android")
else()
    set(MESEN_OS "linux")
endif()

if(ANDROID)
    # Use the ABI (arm64-v8a, armeabi-v7a, x86_64, x86) for the output dir.
    set(MESEN_ARCH "${ANDROID_ABI}")
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|ARM64|arm64")
    set(MESEN_ARCH "arm64")
else()
    set(MESEN_ARCH "x64")
endif()

set(MESEN_PLATFORM "${MESEN_OS}-${MESEN_ARCH}")

set(MESEN_OUTPUT_ROOT "${CMAKE_SOURCE_DIR}/bin/${MESEN_PLATFORM}" CACHE PATH
    "Directory where the MesenCore library is placed")
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "${MESEN_OUTPUT_ROOT}/$<CONFIG>")
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${MESEN_OUTPUT_ROOT}/$<CONFIG>")
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY "${MESEN_OUTPUT_ROOT}/$<CONFIG>")

# ---------------------------------------------------------------------------
# Sources
# ---------------------------------------------------------------------------
file(GLOB_RECURSE MESEN_CORE_SOURCES CONFIGURE_DEPENDS
    "${PROJECT_SOURCE_DIR}/Core/*.cpp")

file(GLOB_RECURSE MESEN_UTIL_SOURCES CONFIGURE_DEPENDS
    "${PROJECT_SOURCE_DIR}/Utilities/*.cpp"
    "${PROJECT_SOURCE_DIR}/Utilities/*.c")

file(GLOB_RECURSE MESEN_SEVENZIP_SOURCES CONFIGURE_DEPENDS
    "${PROJECT_SOURCE_DIR}/SevenZip/*.c")

file(GLOB_RECURSE MESEN_LUA_SOURCES CONFIGURE_DEPENDS
    "${PROJECT_SOURCE_DIR}/Lua/*.c")

if(WIN32)
    # Unix-domain socket sources are not available on Windows.
    list(FILTER MESEN_LUA_SOURCES EXCLUDE REGEX "/(serial|unix|unixdgram|unixstream)\\.c$")
endif()

file(GLOB_RECURSE MESEN_INTEROP_SOURCES CONFIGURE_DEPENDS
    "${PROJECT_SOURCE_DIR}/InteropDLL/*.cpp")

if(WIN32)
    file(GLOB_RECURSE MESEN_PLATFORM_SOURCES CONFIGURE_DEPENDS
        "${PROJECT_SOURCE_DIR}/Windows/*.cpp")
elseif(APPLE)
    file(GLOB_RECURSE MESEN_PLATFORM_SOURCES CONFIGURE_DEPENDS
        "${PROJECT_SOURCE_DIR}/Sdl/*.cpp"
        "${PROJECT_SOURCE_DIR}/MacOS/*.mm")
elseif(ANDROID)
    # Headless Android: no native window/audio/input layer. Input, audio and
    # rendering are provided from Java via the JNI callbacks (IKeyManager,
    # IMouseManager, IAudioDevice, IRenderingDevice).
    set(MESEN_PLATFORM_SOURCES "")
else()
    file(GLOB_RECURSE MESEN_PLATFORM_SOURCES CONFIGURE_DEPENDS
        "${PROJECT_SOURCE_DIR}/Sdl/*.cpp"
        "${PROJECT_SOURCE_DIR}/Linux/*.cpp"
        "${PROJECT_SOURCE_DIR}/Linux/libevdev/*.c")
endif()

# ---------------------------------------------------------------------------
# JNI bindings (merged into MesenCore so adapters share the same DLL/heap)
# ---------------------------------------------------------------------------
option(MESEN_BUILD_JNI "Build the JNI bindings (requires a JDK)" ON)
set(MESEN_JNI_SOURCES "")
if(MESEN_BUILD_JNI)
    # Every .cpp under jni/ belongs to the bindings (MesenJni.cpp, DebugJni.cpp, ...).
    file(GLOB MESEN_JNI_SOURCE_FILES CONFIGURE_DEPENDS "${PROJECT_SOURCE_DIR}/jni/*.cpp")
    if(ANDROID)
        # Android: jni.h comes from the NDK sysroot (already on the include
        # path), and the JNI symbols are provided by the ART runtime. No
        # find_package(JNI) (it would pick the host JDK's win32 jni_md.h) and
        # no libjvm to link.
        set(MESEN_JNI_SOURCES ${MESEN_JNI_SOURCE_FILES})
        set(JNI_LIBRARIES "")
        set(JNI_INCLUDE_DIRS "")
    else()
        find_package(JNI)
        if(JNI_FOUND)
            set(MESEN_JNI_SOURCES ${MESEN_JNI_SOURCE_FILES})
        else()
            message(STATUS "JNI not found - skipping JNI bindings")
        endif()
    endif()
endif()

# ---------------------------------------------------------------------------
# Target
# ---------------------------------------------------------------------------
add_library(MesenCore SHARED
    ${MESEN_CORE_SOURCES}
    ${MESEN_UTIL_SOURCES}
    ${MESEN_SEVENZIP_SOURCES}
    ${MESEN_LUA_SOURCES}
    ${MESEN_INTEROP_SOURCES}
    ${MESEN_PLATFORM_SOURCES}
    ${MESEN_JNI_SOURCES})

# Upstream ships `MesenCore.so` / `MesenCore.dylib` without the `lib` prefix
# on desktop platforms, but Android's System.loadLibrary() needs the standard
# `lib` prefix (libMesenCore.so).
set_target_properties(MesenCore PROPERTIES
    OUTPUT_NAME "MesenCore")
if(NOT ANDROID)
    set_target_properties(MesenCore PROPERTIES PREFIX "")
endif()

target_include_directories(MesenCore PRIVATE
    "${PROJECT_SOURCE_DIR}"
    "${PROJECT_SOURCE_DIR}/Core"
    "${PROJECT_SOURCE_DIR}/Utilities"
    "${PROJECT_SOURCE_DIR}/Sdl"
    "${PROJECT_SOURCE_DIR}/Linux"
    "${PROJECT_SOURCE_DIR}/MacOS"
    "${PROJECT_SOURCE_DIR}/InteropDLL"
    "${PROJECT_SOURCE_DIR}/jni"
    ${JNI_INCLUDE_DIRS})

if(MSVC)
    target_compile_definitions(MesenCore PRIVATE
        WIN32
        UNICODE
        _UNICODE
        NOMINMAX
        _CRT_SECURE_NO_WARNINGS
        _SILENCE_CXX17_CODECVT_HEADER_DEPRECATION_WARNING)
    target_compile_options(MesenCore PRIVATE /MP /utf-8)
    # Match upstream: static CRT, so no VC++ redistributable is required.
    set_property(TARGET MesenCore PROPERTY
        MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()

if(CMAKE_CXX_COMPILER_ID MATCHES "Clang")
    # MesenApi.h declares the C API with C linkage, but several config getters
    # return C++ structs by value; clang warns (-Wreturn-type-c-linkage).
    target_compile_options(MesenCore PRIVATE -Wno-return-type-c-linkage)
endif()

# ---------------------------------------------------------------------------
# Link libraries per platform
# ---------------------------------------------------------------------------
if(WIN32)
    target_link_libraries(MesenCore PRIVATE
        dinput8
        Xinput9_1_0
        d3d11
        d3dcompiler
        dxguid
        winmm
        comctl32
        kernel32
        user32
        gdi32
        winspool
        comdlg32
        advapi32
        shell32
        ole32
        oleaut32
        uuid
        odbc32
        odbccp32
        dsound
        dxgi
        ws2_32
        ${JNI_LIBRARIES})
else()
    find_package(Threads REQUIRED)
    target_link_libraries(MesenCore PRIVATE Threads::Threads ${CMAKE_DL_LIBS} ${JNI_LIBRARIES})

    if(APPLE)
        find_library(FOUNDATION_LIB Foundation REQUIRED)
        find_library(COCOA_LIB Cocoa REQUIRED)
        find_library(GAMECONTROLLER_LIB GameController REQUIRED)
        find_library(COREHAPTICS_LIB CoreHaptics REQUIRED)
        target_link_libraries(MesenCore PRIVATE
            ${FOUNDATION_LIB}
            ${COCOA_LIB}
            ${GAMECONTROLLER_LIB}
            ${COREHAPTICS_LIB})
    elseif(CMAKE_CXX_COMPILER_ID STREQUAL "GNU" AND CMAKE_CXX_COMPILER_VERSION VERSION_LESS 9.1)
        # std::filesystem lived in a separate library before GCC 9.
        target_link_libraries(MesenCore PRIVATE stdc++fs)
    endif()
endif()

