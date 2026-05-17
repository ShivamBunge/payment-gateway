## Multi-stage Dockerfile: build the Spring Boot jar inside the image so compose can build without a
## pre-built jar on disk.

FROM maven:3.9.4-eclipse-temurin-21 AS builder
WORKDIR /src
# Copy root pom so the multi-module build can resolve parent
COPY pom.xml ./
# Copy module folders into lowercase paths expected by the parent pom (Linux is case-sensitive)
COPY TransactionService ./transactionservice
COPY NotificationService ./notificationservice
COPY UserService ./userservice
# Build only the transactionservice module and its required modules
# Ensure the Spring Boot repackage goal runs so the produced jar is executable (has Main-Class in manifest).
RUN mvn -B -DskipTests -pl transactionservice -am package spring-boot:repackage

FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY --from=builder /src/transactionservice/target/*transactionservice-*-SNAPSHOT.jar /app/app.jar
EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]

