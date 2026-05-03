# --- Stage 1: Build Stage ---
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# 1. Cache dependencies (Optimization: only re-runs if pom.xml changes)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 2. Build the application
COPY src ./src
RUN mvn clean package -DskipTests -T 1C

# --- Stage 2: Runtime Stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 3. Security, OS Fixes & User Setup
# Consolidating into one RUN command to reduce image layers
RUN apk update && \
    apk upgrade && \
    apk add --no-cache gnutls>=3.8.13-r0 && \
    addgroup -S adaptergroup && \
    adduser -S adapteruser -G adaptergroup && \
    rm -rf /var/cache/apk/*

# 4. Configuration: Copy the ISO Schema
COPY --chown=adapteruser:adaptergroup src/main/resources/iso-schema.json .

# 5. Application: Copy compiled JAR from build stage
COPY --from=build --chown=adapteruser:adaptergroup /app/target/iso8583-adapter-core-*.jar app.jar

# 6. Environment & Permissions
USER adapteruser
EXPOSE 8080

# 7. Execution: Optimized for Containerized Environments
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-Dfile.encoding=UTF-8", \
            "-jar", "app.jar"]