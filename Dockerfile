# Use OpenJDK 21 as the base image
FROM eclipse-temurin:21-jdk

RUN apt update && apt install -y curl && apt install -y vim

RUN rm -rf /opt/apps/high-tps-servlet/conf/logback.xml

RUN mkdir -p /opt/apps/high-tps-servlet/conf

COPY high-tps-servlet/logback.xml /opt/apps/high-tps-servlet/conf/logback.xml

# Copy the Spring Boot JAR file into the container
COPY target/high-tps-servlet-1.0-SNAPSHOT.jar app.jar

# Expose the application port
EXPOSE 8080

# Run the Spring Boot application
ENTRYPOINT ["java", "-Dlogback.configurationFile=/opt/apps/high-tps-servlet/conf/logback.xml", "-jar", "app.jar"]