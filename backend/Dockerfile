# ---- Stage 1: build the jar (JDK + Maven wrapper) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy only the Maven files first, so the dependency layer is cached
# until pom.xml changes.
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
# GitHub runners and fresh VPS builders have no Maven Wrapper cache. Retry
# transient Maven Central failures without hiding the wrapper download URL.
RUN for attempt in 1 2 3 4 5; do \
      if MVNW_VERBOSE=true ./mvnw -q dependency:go-offline; then exit 0; fi; \
      echo "Maven dependency prefetch failed (attempt ${attempt}/5); retrying" >&2; \
      sleep $((attempt * 5)); \
    done; \
    exit 1

# Now copy the sources and build the executable jar (tests run in CI, not here).
COPY src src
RUN for attempt in 1 2 3 4 5; do \
      if MVNW_VERBOSE=true ./mvnw -q package -DskipTests; then exit 0; fi; \
      echo "Maven package failed (attempt ${attempt}/5); retrying" >&2; \
      sleep $((attempt * 5)); \
    done; \
    exit 1

# ---- Stage 2: small runtime image (JRE only) ----
FROM eclipse-temurin:21-jre-alpine

# Run as a non-root system user instead of root.
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

# Import inbox and production logs (named volumes mounted here keep these owners)
RUN mkdir -p /data/inbox /app/logs && chown -R app:app /data /app/logs

# Copy only the finished jar out of the build stage.
COPY --from=build /app/target/oliveconnect-*.jar app.jar

USER app
EXPOSE 8080

# Use up to 70% of the container memory for the heap.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
