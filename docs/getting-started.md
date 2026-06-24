# Getting started

## Prerequisites

- **FFmpeg 8 or later** — built with the codecs you need (e.g., `libx264`, `libx265`). FFmpeg 8 is the minimum supported major version.
- **[MediaInfo](https://mediaarea.net/en/MediaInfo)** — used for analysing input files.
- **Redis 8+** with the [JSON](https://redis.io/docs/latest/develop/data-types/json/) and [Search](https://redis.io/docs/latest/develop/interact/search-and-query/) modules — for job storage and querying. Tested with `redis:8.6-alpine`. May work with Valkey or other Redis-compatible servers that support these modules, but this has not been tested.
- **Java 25+** (GraalVM recommended) or **Docker**.

<!-- prettier-ignore -->
!!! tip "Reference FFmpeg build"
    At SVT we build and run FFmpeg via our [`ffmpeg-encore`](https://github.com/svt/homebrew-avtools) Homebrew formula, which bundles the [`af_dnenhance`](https://github.com/svt/ffmpeg-filter-dnenhance) filter used for neural dialogue enhancement. See its [releases](https://github.com/svt/homebrew-avtools/releases) for the FFmpeg version we currently run. The pre-built Docker images instead bundle FFmpeg from [static-ffmpeg](https://github.com/wader/static-ffmpeg).

## Running Encore

### Option A: Docker

Pre-built Docker images are published to the GitHub Container Registry on each release. They bundle FFmpeg from [wader/static-ffmpeg](https://github.com/wader/static-ffmpeg) and MediaInfo, including `libx264`, `libx265`, `aac` and most common codecs, but **not** `libfdk_aac`. If you need `libfdk_aac` or other non-free codecs, build your own image (see [Deployment](deployment.md#docker-images)).

The quickest way to get a local instance running is with Docker Compose — a ready-to-use `docker-compose.yml` at the project root starts Redis and `encore-web`:

```bash
docker compose up
```

To run the containers manually instead:

1. Start Redis 8+ (includes JSON and Search modules):

   ```bash
   docker run -d --name redis -p 6379:6379 redis:8.6-alpine
   ```

2. Start encore-web:

   ```bash
   docker run -d --name encore-web \
     -p 8080:8080 \
     -e ENCORE_SETTINGS_REDIS_URI=redis://host.docker.internal:6379 \
     -e ENCORE_SETTINGS_PROFILE_LOCATION=file:/profiles/profiles.yml \
     -v /path/to/profiles:/profiles \
     -v /path/to/media:/media \
     ghcr.io/svt/encore-web:latest
   ```

3. Start one or more workers (optional — `encore-web` can also process jobs):

   ```bash
   docker run -d --name encore-worker \
     -e ENCORE_SETTINGS_REDIS_URI=redis://host.docker.internal:6379 \
     -e ENCORE_SETTINGS_PROFILE_LOCATION=file:/profiles/profiles.yml \
     -v /path/to/profiles:/profiles \
     -v /path/to/media:/media \
     ghcr.io/svt/encore-worker:latest
   ```

Workers are typically launched on demand (for example as Kubernetes jobs via KEDA) rather than kept running, so they are not included in the Compose file — see [Scaling with KEDA](deployment.md#scaling-with-keda).

### Option B: JAR

Build from source, or download JARs from the [GitHub Releases](https://github.com/svt/encore/releases) page:

```bash
./gradlew build -x test
```

Run encore-web:

```bash
java -jar encore-web/build/libs/encore-web-*-boot.jar \
  --encore-settings.redis.uri=redis://localhost:6379 \
  --encore-settings.profile.location=file:/path/to/profiles.yml
```

## Your first transcode

### 1. Create a profile

Create a file called `simple.yml`:

```yaml
name: simple
description: Simple x264 encode
encodes:
  - type: X264Encode
    suffix: _x264_720
    height: 720
    twoPass: false
    params:
      b:v: 2000k
      pix_fmt: yuv420p
    audioEncode:
      type: AudioEncode
      bitrate: 128k
```

Create a profile index file `profiles.yml` that references it:

```yaml
simple: simple.yml
```

Point Encore to the profiles directory by setting `encore-settings.profile.location` to the path of the index file.

### 2. Submit a job

```bash
curl -X POST http://localhost:8080/encoreJobs \
  -H 'Content-Type: application/json' \
  -d '{
    "profile": "simple",
    "outputFolder": "/media/output",
    "baseName": "my_video",
    "inputs": [
      {
        "type": "AudioVideo",
        "uri": "/media/input/source.mp4"
      }
    ]
  }'
```

The response contains the job with its `id` and status `QUEUED`.

### 3. Check job status

```bash
curl http://localhost:8080/encoreJobs/{job-id}
```

The `status` field will progress through: `NEW` → `QUEUED` → `IN_PROGRESS` → `SUCCESSFUL` (or `FAILED`/`CANCELLED`).

When successful, the `output` field contains analysed metadata of the output files, and the transcoded files are in your `outputFolder`.

### 4. Explore the API

Open [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) in your browser to see the full API documentation.
