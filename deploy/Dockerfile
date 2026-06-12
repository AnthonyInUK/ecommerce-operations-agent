FROM eclipse-temurin:17-jre

WORKDIR /app

ARG JAR_FILE=assistant-agent-start/target/assistant-agent-start-0.2.6.jar

RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

COPY ${JAR_FILE} app.jar

RUN mkdir -p /app/logs /app/data /app/olist-csv

EXPOSE 18080

ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-Dfile.encoding=UTF-8", "-jar", "/app/app.jar"]
