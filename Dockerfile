ARG DOCKER_BASE_IMAGE
FROM ${DOCKER_BASE_IMAGE}

LABEL org.opencontainers.image.url="https://github.com/svt/encore"
LABEL org.opencontainers.image.source="https://github.com/svt/encore"
LABEL org.opencontainers.image.title="encore-debian"

ARG USR="root"
ARG JRE="openjdk-11-jre-headless"

USER root

RUN apt-get update --allow-releaseinfo-change && \ 
  apt-get install -yq --no-install-recommends ${JRE} && \
  apt-get clean && apt-get autoremove && rm -rf /var/lib/apt/lists/*

USER ${USR}

COPY --chown=${USR}:${USR} build/libs/encore.jar /app/encore.jar
COPY --chown=${USR}:${USR} bin/start.sh /app/start.sh

WORKDIR /app

ENV JAVA_OPTS "-XX:MaxRAMPercentage=10"

CMD ["/app/start.sh"]
