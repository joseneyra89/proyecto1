# Dockerfile modificado para usar variables de entorno para la base de datos
FROM maven:3.9.9-eclipse-temurin-21

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean compile dependency:copy-dependencies -DskipTests

# Establecer las variables de entorno para la conexión a la base de datos
ENV DB_HOST=tickets-db.cfe68u8wo536.us-east-2.rds.amazonaws.com
ENV DB_PORT=5432
ENV DB_NAME=tickets_db
ENV DB_USER=postgres
ENV DB_PASSWORD=Joseneyra_17

EXPOSE 8080

CMD ["java", "-cp", "target/classes:target/dependency/*", "com.businessName.ticketApi.HelpTicketApi"]