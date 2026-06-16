FROM eclipse-temurin:21-jre-alpine

ARG MODULE
ENV MODULE=${MODULE}

WORKDIR /app

COPY ${MODULE}/target/${MODULE}-1.0-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
