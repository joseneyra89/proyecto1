FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp clean package dependency:copy-dependencies -Dmaven.test.skip=true -DincludeScope=runtime

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/classes ./target/classes
COPY --from=build /app/target/dependency ./target/dependency

EXPOSE 8081

CMD ["java", "-cp", "target/classes:target/dependency/*", "com.businessName.ticketApi.HelpTicketApi"]
