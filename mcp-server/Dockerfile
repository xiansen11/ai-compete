FROM maven:3.9.11-eclipse-temurin-17 AS builder

WORKDIR /workspace

COPY pom.xml ./
COPY framework/pom.xml framework/pom.xml
COPY mcp-server/pom.xml mcp-server/pom.xml

RUN mvn -pl mcp-server -am -DskipTests dependency:go-offline

COPY framework framework
COPY mcp-server mcp-server
COPY mvnw mvnw
COPY mvnw.cmd mvnw.cmd
COPY .mvn .mvn

RUN mvn -pl mcp-server -am -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /workspace/mcp-server/target/mcp-server-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 9099

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
