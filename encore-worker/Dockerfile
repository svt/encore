ARG DOCKER_BASE_IMAGE
FROM ${DOCKER_BASE_IMAGE}

LABEL org.opencontainers.image.url="https://github.com/svt/encore"
LABEL org.opencontainers.image.source="https://github.com/svt/encore"

# produced by gradle target nativeCompile
COPY build/native/nativeCompile/encore-worker /app/

WORKDIR /app

CMD ["/app/encore-worker"]
