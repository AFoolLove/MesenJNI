plugins {
    java
}

group = "mesen"
version = "2.2.1"

repositories {
    mavenCentral()
}

dependencies {
    // Used by mesen.MesenSettings / mesen.NesSettings (settings persistence).
    // Kept as a normal dependency rather than a committed jar.
    implementation("com.google.code.gson:gson:2.10")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// The Java API, the JNI-backed classes and the tests are all plain sources under
// java/; the runnable examples live under example/java. There is no src/main/java.
sourceSets {
    main {
        java {
            setSrcDirs(listOf("java", "example/java"))
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// The tests are plain main() classes rather than JUnit tests, so `gradle test` has
// nothing to collect. This runs one of them by name:
//
//   gradle smokeTest -PtestClass=DebugSmokeTest
//   gradle smokeTest -PtestClass=ConsoleSmokeTest -PnativeLibDir=bin/win-x64/Release
tasks.register<JavaExec>("smokeTest") {
    group = "verification"
    description = "Runs one of the main() based tests, named by -PtestClass"

    mainClass.set(providers.gradleProperty("testClass").orElse("SmokeTest"))
    classpath = sourceSets["main"].runtimeClasspath

    // The tests load MesenCore through System.loadLibrary, so they need the built
    // native library on java.library.path. Override with -PnativeLibDir when the
    // library lives somewhere else (another platform, another configuration).
    val nativeLibDir = providers.gradleProperty("nativeLibDir").getOrElse(
        layout.projectDirectory.dir("bin/win-x64/Release").asFile.absolutePath
    )
    jvmArgs("-Djava.library.path=$nativeLibDir")
}
