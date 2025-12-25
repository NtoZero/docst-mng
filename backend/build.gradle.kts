plugins {
  id("org.springframework.boot") version "3.5.8"
  id("io.spring.dependency-management") version "1.1.7"
  java
}

group = "com.docmesh"
version = "0.1.0"

data class DependencyVersions(val java: Int = 21)
val versions = DependencyVersions()

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(versions.java)
  }
}

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-validation")
}

tasks.withType<Test> {
  useJUnitPlatform()
}
