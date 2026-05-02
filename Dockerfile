FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app
COPY . .

RUN ./gradlew build -x test --no-daemon

FROM eclipse-temurin:17-jre-jammy

COPY --from=build /app/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app.jar"]