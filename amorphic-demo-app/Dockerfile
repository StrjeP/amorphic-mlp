# Build context is the repo root (the Java projects share one Gradle build).
FROM eclipse-temurin:21-jdk AS build
ARG FOLD_IN=false
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon -q :amorphic-demo-app:bootJar -PfoldIn=${FOLD_IN} -x test \
 && cp amorphic-demo-app/build/libs/amorphic-demo-app-*[0-9].jar /app.jar

FROM eclipse-temurin:21-jre
COPY --from=build /app.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
