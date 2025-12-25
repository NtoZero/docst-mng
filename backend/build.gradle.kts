plugins {
  id("org.springframework.boot") version "3.5.8"
  id("io.spring.dependency-management") version "1.1.7"
  java
}

group = "com.docst"
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
  // Spring Boot Starters
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-actuator")

  // Database
  runtimeOnly("org.postgresql:postgresql")
  implementation("org.flywaydb:flyway-core")
  implementation("org.flywaydb:flyway-database-postgresql")

  // Git
  implementation("org.eclipse.jgit:org.eclipse.jgit:7.1.0.202411261347-r")

  // Utilities
  implementation("com.vladsch.flexmark:flexmark-all:0.64.8") // Markdown parsing

  // Test
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testRuntimeOnly("com.h2database:h2")
}

tasks.withType<Test> {
  useJUnitPlatform()
}
