FROM maven:3.9.8-eclipse-temurin-21-alpine AS build
WORKDIR /build

COPY pom.xml .
COPY stats/pom.xml stats/
COPY stats/stats-dto/pom.xml stats/stats-dto/
COPY stats/stats-server/pom.xml stats/stats-server/
COPY stats/stats-client/pom.xml stats/stats-client/
COPY ewm-main-service/pom.xml ewm-main-service/
COPY ewm-main-service/src ewm-main-service/src

RUN mvn -pl ewm-main-service package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/ewm-main-service/target/ewm-main-service-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
