# ---------- Build ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /src

COPY pom.xml .

RUN mvn -q dependency:go-offline

COPY src ./src

RUN mvn -q package -DskipTests


# ---------- Runtime ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create non-root user
RUN useradd --system --uid 10001 app

# Create application storage directory
RUN mkdir -p /var/lib/daysheet \
    && chown -R app:app /var/lib/daysheet

# Copy application
COPY --from=build /src/target/*.jar app.jar

# Run as non-root user
USER app

ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]