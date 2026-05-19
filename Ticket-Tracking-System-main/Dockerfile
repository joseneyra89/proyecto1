FROM maven:3.9.9-eclipse-temurin-8 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp clean package dependency:copy-dependencies -Dmaven.test.skip=true -DincludeScope=runtime

FROM eclipse-temurin:8-jre

WORKDIR /app

COPY --from=build /app/target/classes ./target/classes
COPY --from=build /app/target/dependency ./target/dependency

EXPOSE 8080

CMD ["java", "-cp", "target/classes:target/dependency/*", "com.businessName.ticketApi.HelpTicketApi"]
