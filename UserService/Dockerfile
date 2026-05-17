## Multi-stage Dockerfile: build the Spring Boot jar inside the image so compose can build without
## a pre-built jar. This avoids the "image builds but no jar" situation when you run services locally
## but still want docker images.

FROM maven:3.9.4-eclipse-temurin-21 AS builder
WORKDIR /src
# Copy the repo root pom so multi-module builds can resolve the parent
COPY pom.xml ./
# Copy module directories into lowercase paths expected by the parent pom (Docker/Linux is case-sensitive)
# The repository folders are `TransactionService`, `NotificationService`, `UserService` but the parent POM
# references modules as lowercase paths: transactionservice, notificationservice, userservice.
COPY TransactionService ./transactionservice
COPY NotificationService ./notificationservice
COPY UserService ./userservice
# Build only the userservice module and its dependencies (module name as referenced by parent pom)
# Ensure the Spring Boot repackage goal runs so the produced jar is executable (has Main-Class in manifest).
RUN mvn -B -DskipTests -pl userservice -am package spring-boot:repackage

FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
# Copy the built jar from the builder stage. The target jar name may include a classifier or version,
# so we use a wildcard.
COPY --from=builder /src/userservice/target/*userservice-*-SNAPSHOT.jar /app/app.jar
EXPOSE 8082

ENTRYPOINT ["java","-jar","/app/app.jar"]

