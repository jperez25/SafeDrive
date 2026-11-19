# Built from the repo root after: ./mvnw -pl services/policy-rating -am package -DskipTests
FROM amazoncorretto:25
WORKDIR /app
COPY services/policy-rating/target/policy-rating.jar app.jar
USER nobody
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
