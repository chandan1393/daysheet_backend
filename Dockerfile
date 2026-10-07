# ---------- Build ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /src

# Copy pom first for Docker layer caching
COPY pom.xml .

# Download dependencies
RUN mvn -q dependency:go-offline

# Copy source code
COPY src ./src

# Build application
RUN mvn -q package -DskipTests


# ---------- Runtime ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create non-root user
RUN useradd --system --uid 10001 app

# Copy generated JAR
COPY --from=build /src/target/*.jar app.jar

# Run as non-root user
USER app

# Spring profile
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]