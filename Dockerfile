# Multi-stage Dockerfile for Spring Boot Application
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY expenses.db* ./
EXPOSE 5000
ENV PORT=5000
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:5000/ || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
