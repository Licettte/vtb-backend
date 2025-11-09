plugins {
    kotlin("jvm") version "2.0.20" apply false
    kotlin("plugin.spring") version "2.0.20" apply false
    kotlin("plugin.serialization") version "2.0.20" apply false
    id("org.springframework.boot") version "3.5.7" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "org.elly"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    // единый toolchain
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure(org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension::class.java) {
            jvmToolchain(21)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
