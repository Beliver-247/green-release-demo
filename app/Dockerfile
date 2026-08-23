FROM eclipse-temurin:17-jre-jammy
ARG JAR_FILE=target/*-exec.jar
COPY ${JAR_FILE} /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
