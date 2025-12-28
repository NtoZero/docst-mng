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
  maven { url = uri("https://repo.spring.io/milestone") }
}

dependencies {
  // Spring AI BOM
  implementation(platform("org.springframework.ai:spring-ai-bom:1.0.0-M5"))

  // Spring Boot Starters
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-actuator")

  // Spring AI - PgVector VectorStore
  implementation("org.springframework.ai:spring-ai-pgvector-store-spring-boot-starter")

  // Spring AI - OpenAI Embedding Model (Default)
  implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter")

  // Spring AI - Ollama Embedding Model (Optional)
  implementation("org.springframework.ai:spring-ai-ollama-spring-boot-starter")

  // Database
  runtimeOnly("org.postgresql:postgresql")
  implementation("org.flywaydb:flyway-core")
  implementation("org.flywaydb:flyway-database-postgresql")

  // SQL Logging with actual parameter values (P6Spy)
  implementation("com.github.gavlyukovskiy:p6spy-spring-boot-starter:1.11.0")

  // Git
  implementation("org.eclipse.jgit:org.eclipse.jgit:7.1.0.202411261347-r")

  // Utilities
  implementation("com.vladsch.flexmark:flexmark-all:0.64.8") // Markdown parsing
  implementation("com.knuddels:jtokkit:1.0.0") // Token counting (tiktoken compatible)

  // JWT
  implementation("io.jsonwebtoken:jjwt-api:0.12.5")
  runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
  runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

  // OAuth2
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

  // Security
  implementation("org.springframework.boot:spring-boot-starter-security")

  // Bouncy Castle (Argon2 support)
  implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

  // Cache (for rate limiting)
  implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

  // Lombok
  compileOnly("org.projectlombok:lombok")
  annotationProcessor("org.projectlombok:lombok")
  testCompileOnly("org.projectlombok:lombok")
  testAnnotationProcessor("org.projectlombok:lombok")

  // Test
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testRuntimeOnly("com.h2database:h2")
}

tasks.withType<Test> {
  useJUnitPlatform()
}
