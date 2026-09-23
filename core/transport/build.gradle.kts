plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

dependencies {
    implementation(project(":core:model"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}
