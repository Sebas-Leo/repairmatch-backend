FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build --chown=10001:10001 /app/target/*.jar app.jar

USER 10001:10001
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0
ENTRYPOINT ["java", "-jar", "app.jar"]
