# Built from the repo root after: ./mvnw -pl services/trip-processor -am package -DskipTests
FROM amazoncorretto:25
WORKDIR /app
COPY services/trip-processor/target/trip-processor.jar app.jar
USER nobody
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
