FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/vm3-spring-gateway-1.0.0.jar app.jar
EXPOSE 8020
ENTRYPOINT ["java", "-jar", "app.jar"]
