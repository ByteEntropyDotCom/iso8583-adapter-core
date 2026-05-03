# --- Stage 1: Build Stage ---
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# 1. Cache dependencies (Optimization: only re-runs if pom.xml changes)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 2. Build the application
COPY src ./src
# Parallel threads (-T 1C) speed up compilation on multi-core systems.
RUN mvn clean package -DskipTests -T 1C

# --- Stage 2: Runtime Stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 3. Security & OS Fixes
# Update package index and upgrade gnutls to resolve CVE-2026-33845
RUN apk update && \
    apk add --no-cache gnutls>=3.8.13-r0 && \
    addgroup -S adaptergroup && adduser -S adapteruser -G adaptergroup

# 4. Configuration: Copy the ISO Schema
# The hybrid Registry loader finds this in the working directory
COPY --chown=adapteruser:adaptergroup src/main/resources/iso-schema.json .

# 5. Application: Copy the compiled JAR from the build stage
# Note: Using a wildcard to match the versioned JAR and naming it app.jar
COPY --from=build --chown=adapteruser:adaptergroup /app/target/iso8583-adapter-core-*.jar app.jar

# 6. Environment & Permissions
USER adapteruser

# ISO 8583 Engine default port
EXPOSE 8080

# 7. Execution: ENTRYPOINT with optimized JVM flags for containers
# - UseContainerSupport: Ensures JVM respects Docker memory limits
# - MaxRAMPercentage: Allocates 75% of container memory to the JVM heap
# - ExitOnOutOfMemoryError: Forces container restart on heap exhaustion
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-Dfile.encoding=UTF-8", \
            "-jar", "app.jar"]