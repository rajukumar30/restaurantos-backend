# Build the Spring Boot application with Java 21 and Maven.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# Run with a slim Java 21 runtime as a non-root user.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system restaurantos && useradd --system --gid restaurantos restaurantos
COPY --from=build /workspace/target/restaurantos-backend-0.1.0-SNAPSHOT.jar /app/app.jar
USER restaurantos
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
