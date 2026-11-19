# Built from the repo root after: ./mvnw -pl services/scoring -am package -DskipTests
FROM amazoncorretto:25
WORKDIR /app
COPY services/scoring/target/scoring.jar app.jar
USER nobody
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
