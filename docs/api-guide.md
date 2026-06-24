# API guide

Encore provides a REST API for submitting and managing transcoding jobs.

## OpenAPI / Swagger UI

A running Encore instance provides interactive API documentation:

- **Swagger UI:** `http(s)://your-instance/swagger-ui.html`
- **OpenAPI spec (JSON):** `http(s)://your-instance/v3/api-docs/`
- **OpenAPI spec (YAML):** `http(s)://your-instance/v3/api-docs.yaml`

## Submit a job

`POST /encoreJobs`

**Required fields:**

| Field          | Type   | Description                                   |
| -------------- | ------ | --------------------------------------------- |
| `profile`      | string | Name of the transcoding profile to use        |
| `outputFolder` | string | Directory path for output files               |
| `baseName`     | string | Base filename for outputs (without extension) |
| `inputs`       | array  | List of input files (at least one)            |

**Optional fields:**

| Field                 | Type    | Default | Description                                                   |
| --------------------- | ------- | ------- | ------------------------------------------------------------- |
| `externalId`          | string  | `null`  | External reference ID for backreference                       |
| `priority`            | int     | `0`     | Queue priority (0–100, higher = more priority)                |
| `progressCallbackUri` | string  | `null`  | URL for progress callbacks                                    |
| `profileParams`       | object  | `{}`    | Parameters for SpEL expressions in the profile                |
| `segmentLength`       | number  | `null`  | Segment length in seconds for parallel transcoding            |
| `seekTo`              | number  | `null`  | Seek position in seconds before encoding output (output seek) |
| `duration`            | number  | `null`  | Limit output duration in seconds                              |
| `thumbnailTime`       | number  | `null`  | Override thumbnail time in seconds                            |
| `debugOverlay`        | boolean | `false` | Overlay encoding metadata on video                            |
| `logContext`          | object  | `{}`    | Key-value pairs added to MDC log context                      |

### Input types

Each input has a `type` field:

=== "AudioVideo"

    Combined audio and video input (most common).

    ```json
    {
      "type": "AudioVideo",
      "uri": "/path/to/file.mp4",
      "videoLabel": "main",
      "audioLabel": "main"
    }
    ```

=== "Video"

    Video-only input.

    ```json
    {
      "type": "Video",
      "uri": "/path/to/video.mp4",
      "videoLabel": "main"
    }
    ```

=== "Audio"

    Audio-only input.

    ```json
    {
      "type": "Audio",
      "uri": "/path/to/audio.wav",
      "audioLabel": "main"
    }
    ```

**Common input fields:**

| Field    | Default      | Description                                          |
| -------- | ------------ | ---------------------------------------------------- |
| `uri`    | _(required)_ | Path or URI to the input file                        |
| `params` | `{}`         | FFmpeg decoding parameters                           |
| `seekTo` | `null`       | Seek to time in seconds before decoding (input seek) |
| `copyTs` | `false`      | Copy timestamps during decoding                      |

**Video input fields** (AudioVideo and Video):

| Field             | Default           | Description                                                                                                                                                                                                                                           |
| ----------------- | ----------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `videoLabel`      | `main`            | Label for matching with profile outputs                                                                                                                                                                                                               |
| `videoStream`     | _(auto)_          | Index of the video stream to use                                                                                                                                                                                                                      |
| `dar`             | _(from metadata)_ | Display aspect ratio to assume **for anamorphic inputs** (non-square pixels). Overrides the DAR reported by the input's metadata — intended as a fallback when that metadata is missing or corrupt. Ignored for square-pixel inputs. Example: `16:9`. |
| `cropTo`          | `null`            | Crop input to aspect ratio                                                                                                                                                                                                                            |
| `padTo`           | `null`            | Pad input to aspect ratio                                                                                                                                                                                                                             |
| `videoFilters`    | `[]`              | FFmpeg filters applied to all video outputs                                                                                                                                                                                                           |
| `probeInterlaced` | `true`            | Probe for interlaced content                                                                                                                                                                                                                          |

**Audio input fields** (AudioVideo and Audio):

| Field           | Default  | Description                                 |
| --------------- | -------- | ------------------------------------------- |
| `audioLabel`    | `main`   | Label for matching with profile outputs     |
| `audioStream`   | _(auto)_ | Index of the audio stream to use            |
| `audioFilters`  | `[]`     | FFmpeg filters applied to all audio outputs |
| `channelLayout` | `null`   | Hint for mono audio stream channel layout   |

### Example

```bash
curl -X POST http://localhost:8080/encoreJobs \
  -H 'Content-Type: application/json' \
  -d '{
    "profile": "program",
    "outputFolder": "/output/videos",
    "baseName": "my_video",
    "priority": 50,
    "inputs": [
      {
        "type": "AudioVideo",
        "uri": "/input/source.mp4"
      }
    ]
  }'
```

**Response** (201 Created):

```json
{
  "id": "fb2baa17-8972-451b-bb1e-1bc773283476",
  "profile": "program",
  "outputFolder": "/output/videos",
  "baseName": "my_video",
  "status": "QUEUED",
  "progress": 0,
  "createdDate": "2025-10-21T10:30:00.000+02:00",
  ...
}
```

## Get job status

`GET /encoreJobs/{id}`

```bash
curl http://localhost:8080/encoreJobs/fb2baa17-8972-451b-bb1e-1bc773283476
```

### Job status values

| Status        | Description                                                                                                   |
| ------------- | ------------------------------------------------------------------------------------------------------------- |
| `NEW`         | Initial state on creation; transitions to `QUEUED` almost immediately. Observed only transiently in practice. |
| `QUEUED`      | Waiting in queue                                                                                              |
| `IN_PROGRESS` | Currently transcoding                                                                                         |
| `SUCCESSFUL`  | Transcoding completed                                                                                         |
| `FAILED`      | Transcoding failed (see `message` field)                                                                      |
| `CANCELLED`   | Job was cancelled                                                                                             |

**Read-only fields on the job:**

| Field            | Description                                         |
| ---------------- | --------------------------------------------------- |
| `status`         | Current job status                                  |
| `progress`       | Transcoding progress (0–100)                        |
| `speed`          | Transcoding speed relative to playback speed        |
| `startedDate`    | When the job was picked from the queue              |
| `completedDate`  | When the job completed (success or failure)         |
| `message`        | Error message if failed                             |
| `output`         | Analysed metadata of output files (when successful) |
| `qualityMetrics` | VMAF quality metrics (if configured)                |

## List jobs

`GET /encoreJobs`

Returns a paginated list of jobs, sorted by `createdDate` descending.

```bash
# Default (page 0, size 10)
curl http://localhost:8080/encoreJobs

# Custom pagination
curl "http://localhost:8080/encoreJobs?page=0&size=20&sort=createdDate,asc"
```

## Search jobs

Several search endpoints are available:

```bash
# Find by status
curl "http://localhost:8080/encoreJobs/search/findByStatus?status=FAILED"

# Find by external ID
curl "http://localhost:8080/encoreJobs/search/findByExternalId?externalId=my-ref-123"

# Find by profile
curl "http://localhost:8080/encoreJobs/search/findByProfile?profile=program"

# Find by base name
curl "http://localhost:8080/encoreJobs/search/findByBaseName?baseName=my_video"
```

All search endpoints support pagination parameters (`page`, `size`, `sort`).

## Cancel a job

`POST /encoreJobs/{jobId}/cancel`

```bash
curl -X POST http://localhost:8080/encoreJobs/fb2baa17-8972-451b-bb1e-1bc773283476/cancel
```

Behaviour depends on the current job status:

| Current Status                        | Result                                                     |
| ------------------------------------- | ---------------------------------------------------------- |
| `NEW` / `QUEUED`                      | Job is immediately cancelled                               |
| `IN_PROGRESS`                         | Cancellation signal is sent; the FFmpeg process is stopped |
| `SUCCESSFUL` / `FAILED` / `CANCELLED` | Returns `409 Conflict`                                     |

## Progress callbacks

When a job has `progressCallbackUri` set, Encore sends HTTP POST requests to that URL with progress updates.

**Callback payload:**

```json
{
  "jobId": "fb2baa17-8972-451b-bb1e-1bc773283476",
  "externalId": "my-ref-123",
  "progress": 57,
  "status": "IN_PROGRESS"
}
```

Callbacks are also sent when a job completes (status `SUCCESSFUL`, `FAILED`, or `CANCELLED`). Callback failures are logged but do not affect the job.

## Queue endpoints

```bash
# List all items in the queue
curl http://localhost:8080/queue

# Get count of items per queue
curl http://localhost:8080/queueCount
```
