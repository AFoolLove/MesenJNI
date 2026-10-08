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
else()
    set(MESEN_OS "linux")
endif()

if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|ARM64|arm64")
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
else()
    file(GLOB_RECURSE MESEN_PLATFORM_SOURCES CONFIGURE_DEPENDS
        "${PROJECT_SOURCE_DIR}/Sdl/*.cpp"
        "${PROJECT_SOURCE_DIR}/Linux/*.cpp"
        "${PROJECT_SOURCE_DIR}/Linux/libevdev/*.c")
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
    ${MESEN_PLATFORM_SOURCES})

# Upstream ships `MesenCore.so` / `MesenCore.dylib` without the `lib` prefix.
set_target_properties(MesenCore PROPERTIES
    OUTPUT_NAME "MesenCore"
    PREFIX "")

target_include_directories(MesenCore PRIVATE
    "${PROJECT_SOURCE_DIR}"
    "${PROJECT_SOURCE_DIR}/Core"
    "${PROJECT_SOURCE_DIR}/Utilities"
    "${PROJECT_SOURCE_DIR}/Sdl"
    "${PROJECT_SOURCE_DIR}/Linux"
    "${PROJECT_SOURCE_DIR}/MacOS"
    "${PROJECT_SOURCE_DIR}/InteropDLL")

if(MSVC)
    target_compile_definitions(MesenCore PRIVATE
        WIN32
        UNICODE
        _UNICODE
        NOMINMAX
        _CRT_SECURE_NO_WARNINGS
        _SILENCE_CXX17_CODECVT_HEADER_DEPRECATION_WARNING)
    target_compile_options(MesenCore PRIVATE /MP)
    # Match upstream: static CRT, so no VC++ redistributable is required.
    set_property(TARGET MesenCore PROPERTY
        MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
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
        ws2_32)
else()
    find_package(Threads REQUIRED)
    target_link_libraries(MesenCore PRIVATE Threads::Threads ${CMAKE_DL_LIBS})

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
