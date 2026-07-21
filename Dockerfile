FROM eclipse-temurin:17-jre

WORKDIR /app

ARG JAR_FILE=target/wu-jia-you-chong-2.0.0-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
