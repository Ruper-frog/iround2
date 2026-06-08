plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-amqp:3.2.5")
    implementation("org.springframework.amqp:spring-rabbit:3.2.8")
    implementation("org.springframework:spring-context:6.2.11")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.22.0")
}