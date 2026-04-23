plugins {
    java
    id("com.google.protobuf") version "0.9.5"
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

group = "org.wiremock"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.wiremock:wiremock:3.13.2")
    implementation("com.google.protobuf:protobuf-java:3.25.8")
    implementation("com.google.protobuf:protobuf-java-util:3.25.8")

    testImplementation("org.wiremock:wiremock:3.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.25.3")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.8"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.generateDescriptorSet = true
            task.descriptorSetOptions.includeImports = true
        }
        ofSourceSet("main").forEach { task ->
            task.descriptorSetOptions.path =
                "${project.layout.buildDirectory.get()}/resources/main/protobuf/descriptor.desc"
        }
    }
}

tasks.shadowJar {
    relocate("com.google.protobuf", "shadow.com.google.protobuf")
    relocate("google.protobuf", "shadow.google.protobuf")
    relocate("com.google.gson", "shadow.com.google.gson")
    relocate("com.google.code.gson", "shadow.com.google.code.gson")
    relocate("com.google.common", "shadow.com.google.common")
    relocate("com.google.thirdparty", "shadow.com.google.thirdparty")
    relocate("javax.annotation", "shadow.javax.annotation")
    relocate("com.google.errorprone", "shadow.com.google.errorprone")
    relocate("com.google.j2objc", "shadow.com.google.j2objc")
    relocate("org.checkerframework", "shadow.org.checkerframework")
    relocate("org.jspecify", "shadow.org.jspecify")
    relocate("com.google.guava", "shadow.com.google.guava")
}

tasks.test {
    useJUnitPlatform()
}
