plugins {
    kotlin("jvm")
    `java-library`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":graphscope-model"))
    api("com.google.dagger:dagger-spi:2.60.1")

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.google.dagger:dagger:2.60.1")
    testImplementation("com.google.dagger:dagger-compiler:2.60.1")
    testImplementation("com.google.testing.compile:compile-testing:0.23.0")
}

tasks.test {
    useJUnitPlatform()
}
