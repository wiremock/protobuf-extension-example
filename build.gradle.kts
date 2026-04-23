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
    implementation("org.wiremock:wiremock:3.13.2")
    implementation("com.google.protobuf:protobuf-java:3.25.8")
    implementation("com.google.protobuf:protobuf-java-util:3.25.8")

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

tasks.test {
    useJUnitPlatform()
}
