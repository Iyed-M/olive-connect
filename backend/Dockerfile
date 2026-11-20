# ---- Stage 1: build the jar (JDK + Maven wrapper) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy only the Maven files first, so the dependency layer is cached
# until pom.xml changes.
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
RUN ./mvnw -q dependency:go-offline

# Now copy the sources and build the executable jar (tests run in CI, not here).
COPY src src
RUN ./mvnw -q package -DskipTests

# ---- Stage 2: small runtime image (JRE only) ----
FROM eclipse-temurin:21-jre-alpine

# Run as a non-root system user instead of root.
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

# Import inbox (a named volume mounted here keeps this owner)
RUN mkdir -p /data/inbox && chown -R app:app /data

# Copy only the finished jar out of the build stage.
COPY --from=build /app/target/oliveconnect-*.jar app.jar

USER app
EXPOSE 8080

# Use up to 70% of the container memory for the heap.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
