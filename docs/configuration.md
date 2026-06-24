# Configuration reference

Encore is configured via standard Spring Boot mechanisms — `application.yml`, environment variables, or command-line arguments. All properties use Spring Boot's [relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding) (e.g., `encore-settings.concurrency` in YAML, `ENCORE_SETTINGS_CONCURRENCY` as env var, `--encore-settings.concurrency=4` on the command line).

## Core settings (`encore-settings.*`)

| Property                   | Type     | Default  | Description                                                                                                                                                                                       |
| -------------------------- | -------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `concurrency`              | int      | `2`      | Number of Redis priority queues. Also the polling-thread count in `encore-web`. See [How concurrency is wired up](deployment.md#how-concurrency-is-wired-up).                                     |
| `local-temporary-encode`   | boolean  | `false`  | Transcode to local temp directory before copying to output folder. Useful when output storage is slow or unstable (see [Local Temporary Transcoding](deployment.md#local-temporary-transcoding)). |
| `poll-initial-delay`       | Duration | `10s`    | Wait time after startup before first poll                                                                                                                                                         |
| `poll-delay`               | Duration | `5s`     | Interval between queue polls                                                                                                                                                                      |
| `poll-queue`               | int      | _(all)_  | Poll only the specified queue number                                                                                                                                                              |
| `poll-disabled`            | boolean  | `false`  | Disable queue polling entirely                                                                                                                                                                    |
| `poll-higher-prio`         | boolean  | `true`   | Poll higher-priority queues before assigned queue                                                                                                                                                 |
| `worker-drain-queue`       | boolean  | `false`  | Keep polling until queue is empty before exiting (encore-worker)                                                                                                                                  |
| `shared-work-dir`          | path     | _(none)_ | Shared directory for segmented transcoding                                                                                                                                                        |
| `segmented-encode-timeout` | Duration | `120m`   | Timeout for segmented transcode                                                                                                                                                                   |

## Security settings (`encore-settings.security.*`)

| Property  | Type    | Default | Description                      |
| --------- | ------- | ------- | -------------------------------- |
| `enabled` | boolean | `false` | Enable HTTP basic authentication |
| `users`   | map     | `{}`    | Named user accounts — see below  |

When `enabled` is `true`, at least one user must be configured or the application will fail to start.

### User configuration (`encore-settings.security.users.<name>.*`)

| Field      | Type              | Default      | Description                                                                                    |
| ---------- | ----------------- | ------------ | ---------------------------------------------------------------------------------------------- |
| `password` | string            | _(required)_ | Password for the account. Plain text or Spring Security encoder prefix (`{bcrypt}$2a$10$...`). |
| `role`     | `USER` \| `ADMIN` | `USER`       | Role for the account. `ADMIN` implies `USER`.                                                  |

Roles:

- **USER** — can read jobs (`GET /encoreJobs/**`, `/queue`, `/queueCount`) and access Swagger UI
- **ADMIN** — can create and cancel jobs (`POST /encoreJobs`, `POST /encoreJobs/*/cancel`), plus everything USER can do

```yaml
encore-settings:
  security:
    enabled: true
    users:
      ops-user:
        password: "{bcrypt}$2a$10$..."
        role: USER
      ci-admin:
        password: "${CI_ADMIN_PASSWORD}"
        role: ADMIN
```

## OpenAPI settings (`encore-settings.open-api.*`)

| Property        | Type   | Default                | Description                       |
| --------------- | ------ | ---------------------- | --------------------------------- |
| `title`         | string | `Encore OpenAPI`       | OpenAPI documentation title       |
| `description`   | string | `Endpoints for Encore` | OpenAPI documentation description |
| `contact-name`  | string | `""`                   | Contact name                      |
| `contact-url`   | string | `""`                   | Contact URL                       |
| `contact-email` | string | `""`                   | Contact email                     |

## Encoding settings (`encore-settings.encoding.*`) { #encoding }

| Property                        | Type    | Default            | Description                                                                                                                                                                      |
| ------------------------------- | ------- | ------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `flip-width-height-if-portrait` | boolean | `true`             | Swap width/height for portrait video to keep landscape scaling dimensions                                                                                                        |
| `exit-on-error`                 | boolean | `true`             | Exit FFmpeg on encoding errors                                                                                                                                                   |
| `global-params`                 | map     | `{}`               | Global FFmpeg parameters applied to all encodes                                                                                                                                  |
| `audio-mix-presets`             | map     | `{"default": ...}` | Named audio mix presets (see below)                                                                                                                                              |
| `default-channel-layouts`       | map     | `{}`               | Default channel layouts by channel count                                                                                                                                         |
| `include-quality-metrics`       | set     | `["vmaf"]`         | Which quality metrics to include in results                                                                                                                                      |
| `filter-valid-ffprobe-params`   | boolean | `true`             | Filter out FFmpeg input parameters that are not valid for ffprobe when analysing inputs. Disable if you have custom input parameters that should be passed to ffprobe.           |
| `protocol-input-params`         | map     | _(see below)_      | Default FFmpeg input parameters to add per protocol scheme. Keys are protocol names (`http`, `rtmp`, etc.); `https` is normalised to `http`. Per-input `params` take precedence. |

### Protocol input params

`protocol-input-params` maps a protocol scheme to a set of FFmpeg input options that are automatically added for any input whose URI uses that scheme. `https` is treated the same as `http`.

Default:

```yaml
encore-settings:
  encoding:
    protocol-input-params:
      http:
        reconnect: "1"
        reconnect_on_network_error: "1"
```

This adds HTTP reconnect parameters to all HTTP/HTTPS inputs by default. To disable reconnect params entirely, set `protocol-input-params` to an empty map. To add params for other protocols (e.g. `rtmp`), add additional entries.

Per-input `params` always take precedence over protocol defaults.

### Audio mix presets

Audio mix presets define how audio channels are mapped when converting between channel layouts (e.g., 5.1 surround to stereo).

When an `AudioEncode` needs to convert from a source layout to a target layout, Encore resolves the pan filter in the following order:

1. **`pan-mapping[source][target]`** — an explicit mapping for that specific source→target pair.
2. **`default-pan[target]`** — a default pan filter for the target layout, but **only when downmixing** (target has fewer or equal channels than the source). This is not used for upmixing.
3. **`fallback-to-auto`** — if `true`, fall back to FFmpeg's automatic channel mapping.
4. **Error** — if none of the above match, the encode fails (or is skipped if `optional: true`).

Upmixing (e.g., stereo to 5.1) is not generally done unless you provide an explicit `pan-mapping` entry for that source→target pair, or rely on `fallback-to-auto`.

```yaml
encore-settings:
  encoding:
    audio-mix-presets:
      default:
        fallback-to-auto: true
        default-pan:
          stereo: "FL=FL+0.707*FC+0.707*BL+0.707*SL|FR=FR+0.707*FC+0.707*BR+0.707*SR"
        pan-mapping:
          mono:
            stereo: "FL=0.707*FC|FR=0.707*FC"
```

| Field              | Default | Description                                                                    |
| ------------------ | ------- | ------------------------------------------------------------------------------ |
| `fallback-to-auto` | `true`  | Use FFmpeg automatic channel mapping when no preset mapping exists             |
| `default-pan`      | `{}`    | Default pan filter strings by target channel layout (only used for downmixing) |
| `pan-mapping`      | `{}`    | Specific pan mappings: source layout → target layout → pan filter string       |

## Redis settings (`encore-settings.redis.*`)

| Property          | Type     | Default                  | Description                                                                                                          |
| ----------------- | -------- | ------------------------ | -------------------------------------------------------------------------------------------------------------------- |
| `uri`             | string   | `redis://localhost:6379` | Redis connection URI ([Lettuce URI format](https://redis.github.io/lettuce/user-guide/connecting-redis/#uri-syntax)) |
| `prefix`          | string   | `encore`                 | Redis key prefix                                                                                                     |
| `job-expire-time` | Duration | `7d`                     | TTL for completed jobs                                                                                               |

## HTTP response compression

`encore-web` enables HTTP response compression by default for HAL/JSON responses. Spring Boot only compresses responses larger than a minimum size (2 KB by default) and only when the client advertises `Accept-Encoding`, so small responses pass through uncompressed. Useful for paginated job listings and other large HAL responses; the CPU cost lands on the web layer, not the workers.

To disable, override via standard Spring properties:

```yaml
server:
  compression:
    enabled: false
```

The default MIME types are `application/vnd.hal+json`, `application/json`, and `application/problem+json` (Spring Problem Details error responses). See [Spring Boot's compression settings](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#appendix.application-properties.server) for `min-response-size` and other knobs.

## Profile settings (`encore-settings.profile.*`)

| Property                 | Type     | Default      | Description                                                            |
| ------------------------ | -------- | ------------ | ---------------------------------------------------------------------- |
| `location`               | Resource | _(required)_ | Path to the profile index file (e.g., `file:/etc/encore/profiles.yml`) |
| `spel-expression-prefix` | string   | `#{`         | SpEL expression delimiter prefix                                       |
| `spel-expression-suffix` | string   | `}`          | SpEL expression delimiter suffix                                       |

## Example configuration

```yaml
encore-settings:
  concurrency: 4
  poll-disabled: false
  poll-delay: 5s
  security:
    enabled: true
    users:
      ops-user:
        password: "{bcrypt}$2a$10$..."
        role: USER
      ci-admin:
        password: "${CI_ADMIN_PASSWORD}"
        role: ADMIN
  encoding:
    audio-mix-presets:
      default:
        fallback-to-auto: true
        default-pan:
          stereo: "FL=FL+0.707*FC+0.707*BL+0.707*SL|FR=FR+0.707*FC+0.707*BR+0.707*SR"
  redis:
    uri: redis://redis.example.com:6379
    prefix: encore
    job-expire-time: 14d
  profile:
    location: file:/etc/encore/profiles.yml
```
