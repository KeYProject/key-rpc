plugins {
    id("org.jetbrains.dokka")
    `java-library`
}

repositories {
    mavenCentral()
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}

dependencies {
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.jspecify:jspecify:1.0.1")
    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")

    testCompileOnly("org.projectlombok:lombok:1.18.48")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.48")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

sourceSets.main.get().java.srcDir("src/gen/java")