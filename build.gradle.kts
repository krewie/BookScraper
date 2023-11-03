plugins {
    kotlin("jvm") version "1.9.0"
}

group = "org.krewie"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jsoup:jsoup:1.16.2")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}