FROM eclipse-temurin:17-jdk AS build

WORKDIR /app
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
COPY src ./src

RUN chmod +x ./gradlew && ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:17-jre

WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-Xmx384m -XX:MaxRAMPercentage=75"
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
