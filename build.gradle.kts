plugins {
    kotlin("jvm") version "2.0.21" apply false
    id("org.jetbrains.intellij") version "1.17.4" apply false
}

allprojects {
    group = "org.bloomreach.inspections"
    version = property("version").toString()

    repositories {
        mavenCentral()
        maven("https://repository.onehippo.com/maven2")
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        val implementation by configurations
        val testImplementation by configurations

        implementation(kotlin("stdlib"))
        testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
        testImplementation("io.kotest:kotest-runner-junit5:5.7.2")
        testImplementation("io.kotest:kotest-assertions-core:5.7.2")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = listOf("-Xjsr305=strict")
        }
    }
}
