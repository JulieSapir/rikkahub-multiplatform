val configureSimple = tasks.register<Exec>("configureSimple") {
    inputs.dir("native/simple")
    outputs.file(layout.buildDirectory.file("native/simple/CMakeCache.txt"))
    commandLine(
        "cmake", "-S", "native/simple", "-B", layout.buildDirectory.dir("native/simple").get().asFile,
        "-DCMAKE_BUILD_TYPE=Release", "-DBUILD_SQLITE3=OFF", "-DBUILD_TEST_EXAMPLE=OFF",
        "-DSIMPLE_WITH_JIEBA=OFF",
    )
}

val buildSimple = tasks.register<Exec>("buildSimple") {
    dependsOn(configureSimple)
    inputs.dir("native/simple")
    outputs.file(layout.buildDirectory.file("native/simple/src/libsimple.so"))
    commandLine(
        "cmake",
        "--build",
        layout.buildDirectory.dir("native/simple").get().asFile,
        "--target",
        "simple",
        "--parallel",
        "2"
    )
}

val bundleSimple = tasks.register<Copy>("bundleSimple") {
    dependsOn(buildSimple)
    from(layout.buildDirectory.file("native/simple/src/libsimple.so")) { into("native") }
    from("native/simple/LICENSE") { into("META-INF/licenses/simple") }
    from("native/simple/UPSTREAM.md") { into("META-INF/licenses/simple") }
    from("native/simple/contrib/CMakeRC-LICENSE.txt") { into("META-INF/licenses/cmrc") }
    from("native/simple/contrib/sqlite3/LICENSE") { into("META-INF/licenses/sqlite") }
    into(layout.buildDirectory.dir("generated/sqlite"))
}

tasks.named("processResources") {
    dependsOn(bundleSimple)
}
