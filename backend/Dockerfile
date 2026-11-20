# --- Build stage: compile the jar (tests run separately, they need Docker for PostgreSQL) ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

# --- Runtime stage: only the Java runtime and the jar, running as an unprivileged user ---
FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --no-create-home pageturn
WORKDIR /app
COPY --from=build /src/target/pageturn-backend.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
