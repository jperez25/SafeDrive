# Built from the repo root after: ./mvnw -pl services/gateway -am package -DskipTests
FROM amazoncorretto:25
WORKDIR /app
COPY services/gateway/target/gateway.jar app.jar
USER nobody
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
