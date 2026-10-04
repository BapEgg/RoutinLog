FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S routinlog && adduser -S -G routinlog routinlog
WORKDIR /app
COPY --chown=routinlog:routinlog build/libs/*-SNAPSHOT.jar /app/server.jar
USER routinlog
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-Djava.awt.headless=true", "-jar", "/app/server.jar"]
