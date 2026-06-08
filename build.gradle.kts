plugins {
	kotlin("jvm") version "2.2.21" apply false
	kotlin("plugin.spring") version "2.2.21" apply false

	id("org.springframework.boot") version "3.5.6" apply false
	id("io.spring.dependency-management") version "1.1.7" apply false
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

allprojects {
	repositories {
		mavenCentral()
	}
}