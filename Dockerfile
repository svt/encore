ARG DOCKER_BASE_IMAGE
FROM ${DOCKER_BASE_IMAGE}

COPY build/libs/encore.jar /app/encore.jar
COPY bin/start.sh /app/start.sh

WORKDIR /app

ENV JAVA_OPTS "-XX:MaxRAMPercentage=10"

CMD ["/app/start.sh"]