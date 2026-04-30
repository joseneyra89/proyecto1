FROM maven:3.9.9-eclipse-temurin-21

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean compile dependency:copy-dependencies -DskipTests

EXPOSE 8080

CMD ["java", "-cp", "target/classes:target/dependency/*", "com.businessName.ticketApi.HelpTicketApi"]