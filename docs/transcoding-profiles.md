# Transcoding profiles

Profiles define what outputs Encore produces for a given job. They are YAML files that list one or more encode types (video, audio, thumbnails).

## How profiles work

Profiles are loaded from a **profile index file** — a YAML file that maps profile names to their file paths:

```yaml
# profiles.yml (the index file)
program: program.yml
archive: archive.yml
preview: preview.yml
```

Point Encore to the index file with the `encore-settings.profile.location` property:

```yaml
encore-settings:
  profile:
    location: file:/etc/encore/profiles.yml
```

Profile file paths are relative to the index file location.

## Profile structure

A profile has the following top-level fields:

| Field               | Default      | Description                                             |
| ------------------- | ------------ | ------------------------------------------------------- |
| `name`              | _(none)_     | Profile name                                            |
| `description`       | _(none)_     | Human-readable description                              |
| `scaling`           | `bicubic`    | FFmpeg scaling algorithm                                |
| `deinterlaceFilter` | `yadif`      | FFmpeg deinterlace filter                               |
| `joinSegmentParams` | `{}`         | Additional FFmpeg parameters used when joining segments |
| `encodes`           | _(required)_ | List of encode outputs (see types below)                |

## Encode types

Each entry in `encodes` has a `type` field that determines what kind of output is produced.

### VideoEncode

For any FFmpeg-supported video codec (e.g., DNxHD, ProRes). Requires explicit `codec` and `format`.

```yaml
- type: VideoEncode
  codec: dnxhd
  height: 1080
  suffix: _archive
  format: mxf
  twoPass: false
  params:
    b:v: 185M
    pix_fmt: yuv422p10le
```

| Field          | Default      | Description                                                            |
| -------------- | ------------ | ---------------------------------------------------------------------- |
| `codec`        | _(required)_ | FFmpeg video codec name                                                |
| `suffix`       | _(required)_ | Output file suffix (e.g., `_archive`)                                  |
| `format`       | _(required)_ | Output container format                                                |
| `width`        | _(auto)_     | Target width in pixels                                                 |
| `height`       | _(auto)_     | Target height in pixels                                                |
| `twoPass`      | `false`      | Enable two-pass encoding                                               |
| `params`       | `{}`         | FFmpeg output parameters                                               |
| `filters`      | `[]`         | Additional FFmpeg video filters                                        |
| `audioEncode`  | _(none)_     | Single audio encode to mux into the output                             |
| `audioEncodes` | `[]`         | Multiple audio encodes to mux into the output                          |
| `inputLabel`   | `main`       | Which video input to use (see [Multiple Inputs](#multiple-inputs))     |
| `optional`     | `false`      | Skip instead of failing if input label not found                       |
| `enabled`      | `true`       | Set to `false` to disable this encode                                  |
| `cropTo`       | _(none)_     | Crop to aspect ratio before scaling (e.g., `1:1`)                      |
| `padTo`        | _(none)_     | Pad to aspect ratio after scaling (e.g., `16:9`)                       |
| `vmaf`         | _(none)_     | VMAF quality metrics configuration (see [VMAF](#vmaf-quality-metrics)) |

<!-- prettier-ignore -->
!!! note
    When both `width` and `height` are set, Encore scales while maintaining aspect ratio and rounding to divisible-by-2 dimensions. When only one is set, the other is calculated proportionally.

### X264Encode

H.264 video encoding using `libx264`. Has all the fields from [VideoEncode](#videoencode) with `codec` fixed to `libx264` and `format` defaulting to `mp4`, plus codec-specific parameters:

```yaml
- type: X264Encode
  suffix: _x264_720
  height: 720
  twoPass: true
  params:
    b:v: 2000k
    maxrate: 3000k
    bufsize: 4000k
    pix_fmt: yuv420p
  x264-params:
    bframes: 6
    ref: 4
    me: hex
  audioEncode:
    type: AudioEncode
    bitrate: 128k
```

| Field         | Default | Description                    |
| ------------- | ------- | ------------------------------ |
| `x264-params` | `{}`    | x264 codec-specific parameters |
| `format`      | `mp4`   | Output container format        |

### X265Encode

H.265/HEVC video encoding using `libx265`. Has all the fields from [VideoEncode](#videoencode) with `codec` fixed to `libx265` and `format` defaulting to `mp4`, plus codec-specific parameters:

```yaml
- type: X265Encode
  suffix: _x265_1080
  height: 1080
  twoPass: true
  params:
    b:v: 3000k
    pix_fmt: yuv420p
  x265-params:
    bframes: 6
    ref: 4
```

| Field         | Default | Description                    |
| ------------- | ------- | ------------------------------ |
| `x265-params` | `{}`    | x265 codec-specific parameters |
| `format`      | `mp4`   | Output container format        |

### AudioEncode

Full-featured audio encoding with channel layout mixing and optional dialogue enhancement.

```yaml
- type: AudioEncode
  bitrate: 128k
  suffix: _STEREO
  channelLayout: stereo
```

| Field                 | Default        | Description                                                             |
| --------------------- | -------------- | ----------------------------------------------------------------------- |
| `codec`               | `aac`          | FFmpeg audio codec                                                      |
| `bitrate`             | _(auto)_       | Output bitrate (e.g., `128k`)                                           |
| `samplerate`          | `48000`        | Sample rate in Hz                                                       |
| `channelLayout`       | `stereo`       | Target channel layout                                                   |
| `suffix`              | auto-generated | Output file suffix                                                      |
| `params`              | `{}`           | Additional FFmpeg parameters                                            |
| `filters`             | `[]`           | Audio filters                                                           |
| `audioMixPreset`      | `default`      | Audio mix preset name (see [Configuration Reference](configuration.md)) |
| `format`              | `mp4`          | Output container format                                                 |
| `inputLabel`          | `main`         | Which audio input to use                                                |
| `optional`            | `false`        | Skip if no matching input                                               |
| `enabled`             | `true`         | Disable this encode                                                     |
| `dialogueEnhancement` | _(disabled)_   | Dialogue enhancement settings (see below)                               |

**Dialogue enhancement**

Dialogue enhancement makes speech easier to follow — a common accessibility aid. Encore offers two variants, selected via the `type` field on `dialogueEnhancement`:

- **`native`** _(default)_ — uses only stock FFmpeg filters. Synthesises a centre channel from stereo via [`dialoguenhance`](https://ffmpeg.org/ffmpeg-filters.html#dialoguenhance); surround inputs with an existing centre channel are passed through to the sidechain step directly.
- **`dn`** — a neural model ([DeepFilterNet 3](https://github.com/Rikorose/DeepFilterNet)) running inside FFmpeg via the `af_dnenhance` filter. Cleaner separation in difficult material, and improves surround content too because the existing centre channel is cleaned before the background is ducked.

Both variants end the same way: the dialogue (centre) channel is used as the sidechain to FFmpeg's [`sidechaincompress`](https://ffmpeg.org/ffmpeg-filters.html#sidechaincompress), so the background level dips while someone is speaking and returns when they stop.

**Native**

```yaml
- type: AudioEncode
  bitrate: 128k
  suffix: _STEREO_DE
  dialogueEnhancement:
    type: native # optional — native is the default
    enabled: true
    sidechainCompress:
      ratio: 8
      threshold: 0.012
      attack: 100
      release: 1000
    dialogueEnhanceStereo:
      enabled: true
      original: 1
      enhance: 1
      voice: 2
```

| Field                            | Default | Description                                                      |
| -------------------------------- | ------- | ---------------------------------------------------------------- |
| `enabled`                        | `false` | Master switch for the variant                                    |
| `sidechainCompress.ratio`        | `8`     | Compression ratio applied to the background bus                  |
| `sidechainCompress.threshold`    | `0.012` | Centre level at which ducking engages                            |
| `sidechainCompress.attack`       | `100`   | Attack time, ms                                                  |
| `sidechainCompress.release`      | `1000`  | Release time, ms                                                 |
| `dialogueEnhanceStereo.enabled`  | `true`  | Synthesise a centre channel from stereo L/R via `dialoguenhance` |
| `dialogueEnhanceStereo.original` | `1`     | Centre factor to keep in front (0–1)                             |
| `dialogueEnhanceStereo.enhance`  | `1`     | Dialogue boost (0–3)                                             |
| `dialogueEnhanceStereo.voice`    | `2`     | Voice-detection strength (2–32)                                  |

The native variant works on stereo inputs (when `dialogueEnhanceStereo.enabled` is `true`) and any surround layout that already has a centre channel. For stereo, FFmpeg's `dialoguenhance` synthesises a centre channel and the effective post-enhancement layout becomes 3.0. For surround sources the existing centre channel is used directly — `dialoguenhance` is not invoked, and the effective layout is unchanged. Mono inputs and surround layouts without a centre channel are not supported — the encode is skipped when `optional: true`, otherwise it fails. After enhancement the audio mix preset (`audioMixPreset`) handles the final downmix from the post-enhancement layout to the encode's target `channelLayout`.

**Neural (`type: dn`)**

<!-- prettier-ignore -->
!!! warning "Requires a patched FFmpeg build"
    `type: dn` invokes the `af_dnenhance` FFmpeg filter, which is not part of upstream FFmpeg. The pre-built `ghcr.io/svt/encore-*` images do **not** include it, and profiles using `type: dn` against vanilla FFmpeg fail at encode time with `No such filter: 'dnenhance'`. The filter source — together with build instructions — lives at [svt/ffmpeg-filter-dnenhance](https://github.com/svt/ffmpeg-filter-dnenhance).

All filter parameters are optional — omitting them lets the filter pick its own defaults, which is the common case:

```yaml
- type: AudioEncode
  bitrate: 128k
  suffix: _STEREO_DE
  dialogueEnhancement:
    type: dn
    enabled: true
```

For full control:

```yaml
- type: AudioEncode
  bitrate: 128k
  suffix: _STEREO_DE
  dialogueEnhancement:
    type: dn
    enabled: true
    model: /opt/homebrew/share/libdf/DeepFilterNet3.tar.gz
    postFilter: true
    attenuationLimit: 100.0
    lookahead: 2
    sidechainCompress:
      ratio: 8
      threshold: 0.012
```

| Field                 | Default                   | Description                                                 |
| --------------------- | ------------------------- | ----------------------------------------------------------- |
| `enabled`             | `false`                   | Master switch for the variant                               |
| `model`               | _(filter auto-discovers)_ | Path to a DeepFilterNet 3 model tarball                     |
| `postFilter`          | _(filter default)_        | Enable the DFN3 post-filter                                 |
| `attenuationLimit`    | _(filter default)_        | Maximum suppression in dB — caps the model's gain reduction |
| `lookahead`           | _(filter default)_        | Algorithmic lookahead in 480-sample hops                    |
| `sidechainCompress.*` | _(see Native)_            | Same fields and defaults as the native variant              |

The neural variant works on mono, stereo, and any surround layout with a centre channel. Mono and stereo sources are bridged to a 3.0 layout (mono via equal-power duplication into a stereo background; stereo via a downmix into a synthesised centre) and `dnenhance` is applied to that centre. Surround sources with an existing centre channel use it directly and the effective layout is unchanged. Surround layouts without a centre channel are not supported — the encode is skipped when `optional: true`, otherwise it fails. After enhancement the audio mix preset (`audioMixPreset`) handles the final downmix from the post-enhancement layout to the encode's target `channelLayout` — the same downstream path the native variant uses, so a single mix preset can drive both.

### SimpleAudioEncode

A simpler audio encode that preserves the input channel layout without mixing.

```yaml
- type: SimpleAudioEncode
  codec: pcm_s24le
  suffix: _audio
  format: mxf
```

| Field        | Default       | Description                  |
| ------------ | ------------- | ---------------------------- |
| `codec`      | `aac`         | FFmpeg audio codec           |
| `bitrate`    | _(auto)_      | Output bitrate               |
| `samplerate` | _(unchanged)_ | Sample rate conversion       |
| `suffix`     | `_<codec>`    | Output file suffix           |
| `params`     | `{}`          | Additional FFmpeg parameters |
| `format`     | `mp4`         | Output container format      |
| `inputLabel` | `main`        | Which audio input to use     |
| `optional`   | `false`       | Skip if no matching input    |
| `enabled`    | `true`        | Disable this encode          |

### ThumbnailEncode

Extract thumbnail images at specified times or intervals.

```yaml
- type: ThumbnailEncode
  percentages: [25, 50, 75]
  thumbnailWidth: -2
  thumbnailHeight: 1080
  quality: 5
```

| Field             | Default        | Description                                                                                                                                                       |
| ----------------- | -------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `percentages`     | `[25, 50, 75]` | Extract at these percentages of the duration                                                                                                                      |
| `intervalSeconds` | _(none)_       | Extract at regular intervals instead of percentages                                                                                                               |
| `thumbnailWidth`  | `-2`           | Width in pixels (`-2` = proportional)                                                                                                                             |
| `thumbnailHeight` | `1080`         | Height in pixels                                                                                                                                                  |
| `quality`         | `5`            | JPEG quality (1–31, lower = better)                                                                                                                               |
| `suffix`          | `_thumb`       | Output file suffix                                                                                                                                                |
| `suffixZeroPad`   | `2`            | Zero-padding for numbered thumbnails                                                                                                                              |
| `inputLabel`      | `main`         | Which video input to use                                                                                                                                          |
| `optional`        | `false`        | Skip if not possible                                                                                                                                              |
| `enabled`         | `true`         | Disable this encode                                                                                                                                               |
| `decodeOutput`    | _(none)_       | Use a decoded video output as input instead of the original (see [Reusing an encoded output as thumbnail source](#reusing-an-encoded-output-as-thumbnail-source)) |

<!-- prettier-ignore -->
!!! note
    The `thumbnailTime` field on the job overrides the profile's percentage/interval configuration.

### ThumbnailMapEncode

Generate a sprite sheet (tile map) of thumbnails, useful for video scrubbing previews.

```yaml
- type: ThumbnailMapEncode
  tileWidth: 160
  tileHeight: 90
  cols: 12
  rows: 20
```

| Field          | Default        | Description                                                                                                                               |
| -------------- | -------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `tileWidth`    | `160`          | Width of each tile in pixels                                                                                                              |
| `tileHeight`   | `90`           | Height of each tile in pixels                                                                                                             |
| `cols`         | `12`           | Number of columns                                                                                                                         |
| `rows`         | `20`           | Number of rows                                                                                                                            |
| `quality`      | `5`            | JPEG quality (1–31, lower = better)                                                                                                       |
| `suffix`       | auto-generated | Output file suffix                                                                                                                        |
| `format`       | `jpg`          | Output format                                                                                                                             |
| `inputLabel`   | `main`         | Which video input to use                                                                                                                  |
| `optional`     | `true`         | Skip if not possible                                                                                                                      |
| `enabled`      | `true`         | Disable this encode                                                                                                                       |
| `decodeOutput` | _(none)_       | Use a decoded video output as input (see [Reusing an encoded output as thumbnail source](#reusing-an-encoded-output-as-thumbnail-source)) |

### Reusing an encoded output as thumbnail source

Both `ThumbnailEncode` and `ThumbnailMapEncode` accept a `decodeOutput` field. Its value is the **zero-based index of an earlier entry in the profile's `encodes` list** — the thumbnail is then generated by decoding that entry's output (via FFmpeg's loopback decoder) instead of re-decoding the original source.

For example, with `decodeOutput: 0` and the complete profile below, thumbnails are generated from the decoded frames of the first `X264Encode`:

```yaml
encodes:
  - type: X264Encode # index 0 — produces the video we'll decode from
    suffix: _x264_1080
    height: 1080
    # …

  - type: ThumbnailEncode
    decodeOutput: 0 # reuse the decoded frames of the X264 output
```

This is primarily a workaround for thumbnail issues observed with FFmpeg 7+, and has the useful side effect of only decoding the source once when both a video encode and thumbnails are produced.

## SpEL expressions

Profiles support [Spring Expression Language (SpEL)](https://docs.spring.io/spring-framework/reference/core/expressions.html) for dynamic parameterisation. Expressions are delimited by `#{` and `}` by default.

The job's `profileParams` map is available as context. This allows a single profile to handle variations — different frame rates, bitrate tiers, feature flags — without duplicating the profile file.

A common pattern is to branch on a parameter with a safe default using `?:` (Elvis operator) and a ternary `? :`:

```yaml
name: program
encodes:
  - type: X264Encode
    suffix: "#{(profileParams['fps'] ?: 25) > 30 ? '_x264_3800' : '_x264_3100'}"
    height: 1080
    twoPass: true
    params:
      b:v: "#{(profileParams['fps'] ?: 25) > 30 ? '3800k' : '3100k'}"
      r: "#{profileParams['fps'] ?: 25}"
      fps_mode: cfr
```

`profileParams['fps'] ?: 25` reads the `fps` param from the job, falling back to `25` if it is absent. The `> 30` check selects higher bitrates for 50/60 fps content. A job submitting 50 fps content would include:

```json
{
  "profile": "program",
  "profileParams": { "fps": 50 },
  "outputFolder": "/output",
  "baseName": "clip",
  "inputs": [{ "type": "AudioVideo", "uri": "/input/source.mp4" }]
}
```

## Multiple inputs

Jobs can have multiple inputs with different labels. Encode types reference inputs by their `inputLabel` field.

**Job with multiple inputs:**

```json
{
  "profile": "composite",
  "outputFolder": "/output",
  "baseName": "composite_output",
  "inputs": [
    {
      "type": "AudioVideo",
      "uri": "/input/main_video.mp4",
      "videoLabel": "main",
      "audioLabel": "main"
    },
    {
      "type": "Audio",
      "uri": "/input/alt_audio.wav",
      "audioLabel": "alt"
    }
  ]
}
```

**Profile referencing multiple inputs:**

```yaml
encodes:
  - type: AudioEncode
    bitrate: 128k
    suffix: _STEREO
    inputLabel: main

  - type: AudioEncode
    bitrate: 128k
    suffix: _STEREO_ALT
    inputLabel: alt
```

## VMAF quality metrics

<!-- prettier-ignore -->
!!! warning "Limitations"
    VMAF is not supported in segmented encoding mode. Scores will not be meaningful if the encode changes the frame rate of the source — use the same frame rate as the input.

<!-- prettier-ignore -->
!!! note "Performance"
    Calculating VMAF runs `libvmaf` alongside the encode and adds CPU work to every encoded frame. Depending on hardware, this can noticeably slow the overall transcode.

<!-- prettier-ignore -->
!!! warning "Memory use on fast encodes"
    `libvmaf` runs in the same FFmpeg process as the encode and queues frames for scoring with no upper bound. When the encoder produces frames faster than VMAF can score them, the backlog grows for the whole job and can exhaust memory (OOM) on long inputs. The risk grows with input duration, source resolution (VMAF runs at the **source** resolution, not the output resolution), and the number of VMAF-enabled outputs in the profile.

    Raise `subsample` so VMAF scores fewer frames and keeps pace with the encoder. Lowering `threads` makes it *worse* (VMAF drains more slowly) — `subsample` is the lever. Slow, encoder-bound profiles are naturally safe: the encode paces the job and VMAF keeps up on its own.

    **Rule of thumb**, keyed on the job's reported encode speed relative to realtime (the whole-profile speed Encore records). Starting points for a 1080p source with `threads: 4` and the default model — use a lower threshold for higher-resolution sources, profiles with many VMAF-enabled outputs, or single-pass profiles (where VMAF runs across the whole encode rather than only the second pass):

    | Reported encode speed    | `subsample` |
    | ------------------------ | ----------- |
    | up to ~1× realtime       | not needed  |
    | ~1–2× realtime           | `3`         |
    | faster than ~2× realtime | `5`         |

    When in doubt, watch process memory over a long job: flat is fine; steadily climbing means the queue is growing — raise `subsample`, or disable VMAF for that profile.

Video encodes can optionally calculate [VMAF](https://github.com/Netflix/vmaf) quality scores by comparing the encoded output against the source.

```yaml
- type: X264Encode
  suffix: _x264_720
  height: 720
  params:
    b:v: 2000k
  vmaf:
    enabled: true
    model: version=vmaf_4k_v0.6.1\\:name=vmaf4k|version=vmaf_v0.6.1\\:name=vmaf
    feature: name=psnr
    threads: 4
```

The `model` field accepts a pipe-separated list of VMAF models in FFmpeg's `version=...\:name=...` format. When multiple models are specified, scores are reported under each model's name (the `name=` you assign becomes the key under `qualityMetrics` in the job result). The `feature` field adds extra metrics — `name=psnr` enables PSNR scores alongside VMAF.

<!-- prettier-ignore -->
!!! note "Why the `\\:` escaping?"
    Inside FFmpeg's libvmaf filter, `:` separates one model's options (e.g. `version=...:name=...`), and `|` separates multiple models. The value is passed through FFmpeg's filter-argument parser, which strips one layer of escaping — so a literal `:` inside a model definition must be written as `\\:` in the profile. Don't escape the `|` between models.

| Field        | Default            | Description                                                            |
| ------------ | ------------------ | ---------------------------------------------------------------------- |
| `enabled`    | `false`            | Enable VMAF calculation                                                |
| `model`      | _(FFmpeg default)_ | VMAF model(s) in FFmpeg `version=...\:name=...` format, pipe-separated |
| `threads`    | _(auto)_           | Number of threads for VMAF calculation                                 |
| `subsample`  | _(none)_           | Score every Nth frame (higher = faster, less accurate; bounds memory on fast encodes — see warning above) |
| `feature`    | _(none)_           | Additional feature metrics (e.g. `name=psnr`)                          |
| `refFilters` | `[]`               | Filters to apply to the reference stream before comparison             |

When enabled, VMAF scores are included in the job's `qualityMetrics` field after encoding completes, keyed by output filename and then by metric name (e.g. `vmaf`, `vmaf4k`, `psnr_y`). The full VMAF log is also saved alongside each output file with `.vmaf.json` appended to the filename (e.g. `my_video_720p.mp4.vmaf.json`).

By default, only the `vmaf` metric is included in the results. Use `encore-settings.encoding.include-quality-metrics` to control which metrics are stored (see [Configuration Reference](configuration.md#encoding)):

```yaml
encore-settings:
  encoding:
    include-quality-metrics: [vmaf, vmaf4k, psnr_y, psnr_cb, psnr_cr]
```

## Complete example

SVT's production profile for long-form programmes — a five-rung x264 ABR ladder, stereo and surround audio variants with optional dialogue enhancement, and thumbnails. Each video rung enables VMAF with `subsample: 5`, since a fast x264 ladder on long inputs is exactly the case where unbounded VMAF scoring can exhaust memory (see the warning above):

```yaml
name: program
description: Program profile
scaling: bicubic
encodes:
  - type: X264Encode
    suffix: #{(profileParams['fps'] ?: 25) > 30 ? "_x264_3800" : "_x264_3100"}
    twoPass: true
    width: 1920
    height: 1080
    vmaf:
      enabled: true
      threads: 4
      subsample: 5
    params:
      b:v: #{(profileParams['fps'] ?: 25) > 30 ? "3800k" : "3100k"}
      maxrate: #{(profileParams['fps'] ?: 25) > 30 ? "5700k" : "4700k"}
      bufsize: #{(profileParams['fps'] ?: 25) > 30 ? "9000k" : "7600k"}
      r: #{profileParams['fps'] ?: 25}
      fps_mode: cfr
      pix_fmt: yuv420p
      force_key_frames: "expr:not(mod(n,#{profileParams['gop'] ?: 96}))"
      profile:v: high
      level: #{(profileParams['fps'] ?: 25) > 30 ? "4.2" : "4.1"}
    x264-params:
      deblock: 0,0
      aq-mode: 1
      aq-strength: 1.0
      b-adapt: 2
      bframes: 6
      b-bias: 0
      b-pyramid: 2
      chroma-qp-offset: -2
      direct: auto
      rc-lookahead: #{(profileParams['fps'] ?: 25) > 30 ? 80 : 60}
      keyint: #{(profileParams['gop'] ?: 96) * 2}
      keyint_min: #{profileParams['gop'] ?: 96}
      me: umh
      merange: 24
      cabac: 1
      partitions: all
      ref: 4
      scenecut: 40
      subme: 9
      trellis: 2
      weightp: 2
    audioEncode:
      type: AudioEncode
      codec: libfdk_aac
      bitrate: 192k

  - type: X264Encode
    suffix: #{(profileParams['fps'] ?: 25) > 30 ? "_x264_2550" : "_x264_2069"}
    twoPass: true
    width: 1280
    height: 720
    vmaf:
      enabled: true
      threads: 4
      subsample: 5
    params:
      b:v: #{(profileParams['fps'] ?: 25) > 30 ? "2550k" : "2069k"}
      maxrate: #{(profileParams['fps'] ?: 25) > 30 ? "3825k" : "3104k"}
      bufsize: #{(profileParams['fps'] ?: 25) > 30 ? "5100k" : "4138k"}
      r: #{profileParams['fps'] ?: 25}
      fps_mode: cfr
      pix_fmt: yuv420p
      force_key_frames: "expr:not(mod(n,#{profileParams['gop'] ?: 96}))"
      profile:v: main
      level: #{(profileParams['fps'] ?: 25) > 30 ? "3.2" : "3.1"}
    x264-params:
      deblock: 0,0
      aq-mode: 1
      aq-strength: 1.0
      b-adapt: 2
      bframes: 6
      b-bias: 0
      b-pyramid: 2
      chroma-qp-offset: -2
      direct: auto
      rc-lookahead: #{(profileParams['fps'] ?: 25) > 30 ? 80 : 60}
      keyint: #{(profileParams['gop'] ?: 96) * 2}
      keyint_min: #{profileParams['gop'] ?: 96}
      me: hex
      merange: 16
      cabac: 1
      partitions: all
      ref: 4
      scenecut: 40
      subme: 9
      trellis: 2
      weightp: 2
    audioEncode:
      type: AudioEncode
      codec: libfdk_aac
      bitrate: 128k

  - type: X264Encode
    suffix: #{(profileParams['fps'] ?: 25) > 30 ? "_x264_1410" : "_x264_1312"}
    twoPass: true
    width: 960
    height: 540
    vmaf:
      enabled: true
      threads: 4
      subsample: 5
    params:
      b:v: #{(profileParams['fps'] ?: 25) > 30 ? "1410k" : "1312k"}
      maxrate: #{(profileParams['fps'] ?: 25) > 30 ? "2115k" : "1968k"}
      bufsize: #{(profileParams['fps'] ?: 25) > 30 ? "2820k" : "2624k"}
      r: #{profileParams['fps'] ?: 25}
      fps_mode: cfr
      pix_fmt: yuv420p
      force_key_frames: "expr:not(mod(n,#{profileParams['gop'] ?: 96}))"
      level: #{(profileParams['fps'] ?: 25) > 30 ? "3.2" : "3.1"}
      profile:v: main
    x264-params:
      deblock: 0,0
      aq-mode: 1
      aq-strength: 1.0
      b-adapt: 2
      bframes: 6
      b-bias: 0
      b-pyramid: 2
      chroma-qp-offset: -2
      direct: auto
      rc-lookahead: #{(profileParams['fps'] ?: 25) > 30 ? 80 : 60}
      keyint: #{(profileParams['gop'] ?: 96) * 2}
      keyint_min: #{profileParams['gop'] ?: 96}
      me: hex
      merange: 16
      cabac: 1
      partitions: all
      ref: 4
      scenecut: 40
      subme: 9
      trellis: 2
      weightp: 2
    audioEncode:
      type: AudioEncode
      codec: libfdk_aac
      bitrate: 96k

  - type: X264Encode
    suffix: #{(profileParams['fps'] ?: 25) > 30 ? "_x264_910" : "_x264_806"}
    twoPass: true
    width: 640
    height: 360
    vmaf:
      enabled: true
      threads: 4
      subsample: 5
    params:
      b:v: #{(profileParams['fps'] ?: 25) > 30 ? "910412" : "806121"}
      maxrate: #{(profileParams['fps'] ?: 25) > 30 ? "1365618" : "1209182"}
      bufsize: #{(profileParams['fps'] ?: 25) > 30 ? "1820824" : "1612242"}
      r: #{profileParams['fps'] ?: 25}
      fps_mode: cfr
      pix_fmt: yuv420p
      force_key_frames: "expr:not(mod(n,#{profileParams['gop'] ?: 96}))"
      profile:v: main
      level: #{(profileParams['fps'] ?: 25) > 30 ? "3.2" : "3.1"}
    x264-params:
      deblock: 0,0
      aq-mode: 1
      aq-strength: 1.0
      b-adapt: 2
      bframes: 6
      b-bias: 0
      b-pyramid: 2
      chroma-qp-offset: -2
      direct: auto
      rc-lookahead: #{(profileParams['fps'] ?: 25) > 30 ? 80 : 60}
      keyint: #{(profileParams['gop'] ?: 96) * 2}
      keyint_min: #{profileParams['gop'] ?: 96}
      me: hex
      merange: 16
      cabac: 1
      partitions: all
      ref: 4
      scenecut: 40
      subme: 9
      trellis: 2
      weightp: 2
    audioEncode:
      type: AudioEncode
      codec: libfdk_aac
      bitrate: 96k

  - type: X264Encode
    suffix: #{(profileParams['fps'] ?: 25) > 30 ? "_x264_410" : "_x264_320"}
    twoPass: true
    width: 416
    height: 234
    vmaf:
      enabled: true
      threads: 4
      subsample: 5
    params:
      b:v: #{(profileParams['fps'] ?: 25) > 30 ? "410450" : "324051"}
      maxrate: #{(profileParams['fps'] ?: 25) > 30 ? "615675" : "486077"}
      bufsize: #{(profileParams['fps'] ?: 25) > 30 ? "820900" : "648102"}
      r: #{profileParams['fps'] ?: 25}
      fps_mode: cfr
      pix_fmt: yuv420p
      force_key_frames: "expr:not(mod(n,#{profileParams['gop'] ?: 96}))"
      profile:v: baseline
      level: #{(profileParams['fps'] ?: 25) > 30 ? "3.2" : "3.1"}
    x264-params:
      deblock: 0,0
      aq-mode: 1
      aq-strength: 1.0
      chroma-qp-offset: -2
      rc-lookahead: #{(profileParams['fps'] ?: 25) > 30 ? 40 : 30}
      keyint: #{(profileParams['gop'] ?: 96) * 2}
      keyint_min: #{profileParams['gop'] ?: 96}
      me: hex
      merange: 16
      cabac: 0
      8x8dct: 0
      ref: 3
      scenecut: 40
      subme: 9
      trellis: 2
    audioEncode:
      type: AudioEncode
      codec: libfdk_aac
      bitrate: 96k

  - type: AudioEncode
    codec: libfdk_aac
    bitrate: 192k
    suffix: _STEREO
    params:
      cutoff: 20000

  - type: AudioEncode
    codec: libfdk_aac
    bitrate: 64k
    suffix: _STEREO_LB
    params:
      profile:a: aac_he
      cutoff: 14000

  - type: AudioEncode
    codec: libfdk_aac
    bitrate: 32k
    suffix: _STEREO_TB
    params:
      profile:a: aac_he_v2
      cutoff: 14000

  - type: AudioEncode
    codec: libfdk_aac
    bitrate: 192k
    suffix: _STEREO_DE
    dialogueEnhancement:
      enabled: true
      dialogueEnhanceStereo:
        enabled: #{(profileParams['dialogueEnhancementEnabled'] ?: true)}
    audioMixPreset: de-dynamic
    optional: true
    params:
      cutoff: 20000

  - type: AudioEncode
    codec: libfdk_aac
    bitrate: 64k
    suffix: _STEREO_LB_DE
    dialogueEnhancement:
      enabled: true
      dialogueEnhanceStereo:
        enabled: #{(profileParams['dialogueEnhancementEnabled'] ?: true)}
    audioMixPreset: de-dynamic
    optional: true
    params:
      profile:a: aac_he
      cutoff: 14000

  - type: AudioEncode
    codec: libfdk_aac
    bitrate: 32k
    suffix: _STEREO_TB_DE
    dialogueEnhancement:
      enabled: true
      dialogueEnhanceStereo:
        enabled: #{(profileParams['dialogueEnhancementEnabled'] ?: true)}
    audioMixPreset: de-dynamic
    optional: true
    params:
      profile:a: aac_he_v2
      cutoff: 14000

  - type: AudioEncode
    codec: ac3
    bitrate: 448k
    suffix: _SURROUND_DE
    dialogueEnhancement:
      enabled: true
    audioMixPreset: de-dynamic
    optional: true
    channelLayout: "5.1"

  - type: AudioEncode
    codec: ac3
    bitrate: 448k
    suffix: _SURROUND
    optional: true
    channelLayout: "5.1"

  - type: ThumbnailMapEncode
    decodeOutput: 0
```

A few things worth noting about this profile:

**`decodeOutput: 0`** — the thumbnail is decoded from the first video output (the 1080p rung) rather than re-decoding the source. This avoids an extra decode pass. `0` is the zero-based index into the `encodes` list.

**`libfdk_aac` codec** — all AAC encodes here use `libfdk_aac`, which is not included in the pre-built Docker images (it requires a custom build due to licensing). If you substitute `aac`, the `profile:a: aac_he` and `profile:a: aac_he_v2` parameters are not supported by the standard AAC encoder and must be removed — the `_STEREO_LB` and `_STEREO_TB` outputs will fall back to plain AAC at the specified bitrates.

**`de-dynamic` audio mix preset** — the dialogue-enhanced encodes reference a custom `de-dynamic` preset that must be added to your `encore-settings`:

```yaml
encore-settings:
  encoding:
    audio-mix-presets:
      de-dynamic:
        fallback-to-auto: false
        default-pan:
          stereo: "FL<FL+1.5*FC+0.707107*BL+0.707107*SL|FR<FR+1.5*FC+0.707107*BR+0.707107*SR"
        pan-mapping:
          "[5.1]":
            "[5.1]": "c0=c0|c1=c1|c2<1.5*c2|c3=c3|c4=c4|c5=c5"
          "[5.1(side)]":
            "[5.1]": "c0=c0|c1=c1|c2<1.5*c2|c3=c3|c4=c4|c5=c5"
```

This preset boosts the centre (dialogue) channel by 1.5× before the dialogue-enhancement filters receive it. The channel layout keys (`[5.1]`) must be quoted in YAML to avoid being parsed as sequence syntax.
