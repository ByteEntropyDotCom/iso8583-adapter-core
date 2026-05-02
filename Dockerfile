# --- Stage 1: Build Stage ---
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy only pom.xml first to leverage Docker layer caching for dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build the application
COPY src ./src
RUN mvn clean package -DskipTests

# --- Stage 2: Runtime Stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root user for security
RUN addgroup -S adaptergroup && adduser -S adapteruser -G adaptergroup
USER adapteruser

# Copy the executable jar from the build stage
# Note: Ensure the 'finalName' in your pom.xml matches or use a wildcard
COPY --from=build /app/target/iso8583-adapter-core-*.jar app.jar

# Standard ISO 8583 port (example)
EXPOSE 8080

# Use optimized JVM settings for container environments
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", "app.jar"]