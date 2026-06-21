FROM maven:3.9.8-eclipse-temurin-21-alpine AS build
WORKDIR /build

COPY pom.xml .
COPY stats/pom.xml stats/
COPY stats/stats-dto/pom.xml stats/stats-dto/
COPY stats/stats-dto/src stats/stats-dto/src
COPY stats/stats-server/pom.xml stats/stats-server/
COPY stats/stats-server/src stats/stats-server/src
COPY stats/stats-client/pom.xml stats/stats-client/
COPY ewm-main-service/pom.xml ewm-main-service/

RUN mvn -pl stats/stats-server -am package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/stats/stats-server/target/stats-server-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
