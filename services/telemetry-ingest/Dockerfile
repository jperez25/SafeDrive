# Built from the repo root after: ./mvnw -pl services/telemetry-ingest -am package -DskipTests
FROM amazoncorretto:25
WORKDIR /app
COPY services/telemetry-ingest/target/telemetry-ingest.jar app.jar
USER nobody
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
