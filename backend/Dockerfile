FROM gradle:8.7-jdk21 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/docst-backend-0.1.0.jar app.jar
EXPOSE 8342
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
