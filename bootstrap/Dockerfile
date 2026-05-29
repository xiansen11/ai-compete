FROM maven:3.9.11-eclipse-temurin-17 AS builder

WORKDIR /workspace

COPY pom.xml ./
COPY framework/pom.xml framework/pom.xml
COPY infra-ai/pom.xml infra-ai/pom.xml
COPY bootstrap/pom.xml bootstrap/pom.xml
COPY mcp-server/pom.xml mcp-server/pom.xml

RUN mvn -pl bootstrap -am -DskipTests dependency:go-offline

COPY framework framework
COPY infra-ai infra-ai
COPY bootstrap bootstrap
COPY resources resources
COPY mvnw mvnw
COPY mvnw.cmd mvnw.cmd
COPY .mvn .mvn

RUN mvn -pl bootstrap -am -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /workspace/bootstrap/target/ai-compete-bootstrap-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
