FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/screening-service-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
