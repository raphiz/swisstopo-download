plugins {
    kotlin("jvm") version "2.0.0"
    application
}

application {
    mainClass.set("io.github.raphiz.swisstopodownload.AppKt")
}

group = "io.github.raphiz.swisstopodownload"
version = System.getenv("APP_VERSION") ?: "dirty"

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation(platform("org.http4k:http4k-bom:5.23.0.0"))
    implementation("org.http4k:http4k-core")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.set(listOf("-Xjsr305=strict"))
    }
}

tasks.named<JavaExec>("run").configure {
    jvmArgs("-XX:TieredStopAtLevel=1")
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
