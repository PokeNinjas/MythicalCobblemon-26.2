// Modified 2026-10-21: standalone source build; removed obsolete self-dependency
// and remote publishing configuration. Pinned the JUnit runtime for Gradle 9.
plugins {
    java
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.12.3")
    implementation("com.google.code.gson:gson:2.8.9")

    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")

    testImplementation("com.google.code.gson:gson:2.8.9")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.7.2")
}

group = "com.bedrockk"
version = "1.1.20"

tasks.named<Test>("test") {
    dependsOn(tasks.named("jar"))

    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

