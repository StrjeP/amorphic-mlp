# Build context is the repo root (the Java projects share one Gradle build).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon -q :pricing-calc-service:bootJar -x test \
 && cp pricing-calc-java/pricing-calc-service/build/libs/pricing-calc-service-*[0-9].jar /service.jar

FROM eclipse-temurin:21-jre
COPY --from=build /service.jar /service.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/service.jar"]
