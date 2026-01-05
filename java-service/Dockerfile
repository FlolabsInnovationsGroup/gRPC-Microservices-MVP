FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/*.jar app.jar

EXPOSE 8080 50052

CMD ["java", "-jar", "app.jar"]