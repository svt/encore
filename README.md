# SVT Encore

[![License](https://img.shields.io/badge/license-EUPL-brightgreen.svg)](https://eupl.eu/)
[![REUSE status](https://api.reuse.software/badge/github.com/svt/encore)](https://api.reuse.software/info/github.com/svt/encore)
[![Latest release](https://img.shields.io/github/v/release/svt/encore)](https://github.com/svt/encore/releases)

<img align="center" width="30%" src="docs/assets/encore_logo.png">

**Scalable video transcoding as a service, built on [FFmpeg](https://www.ffmpeg.org/) and [Spring Boot](https://spring.io/projects/spring-boot).**

Encore wraps FFmpeg behind a REST API and queues transcoding jobs in Redis, distributing work across a horizontally scalable pool of workers. Jobs are defined by reusable YAML transcoding profiles, routed through priority queues so urgent work stays unblocked, and report progress via HTTP callbacks.

It targets advanced users integrating transcoding into automated pipelines — for example, as part of a VOD (Video On Demand) workflow. Encore has been in production at SVT since 2019 and open source since 2021.

**Full documentation: [svt.github.io/encore](https://svt.github.io/encore/)**

## Quickstart

```bash
docker compose up
```

Starts Redis and `encore-web` using the bundled [`docker-compose.yml`](docker-compose.yml). See [Getting Started](https://svt.github.io/encore/getting-started/) for creating a profile and submitting your first job.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Report bugs and request features via [GitHub Issues](https://github.com/svt/encore/issues).

## License

Copyright 2020–2026 Sveriges Television AB. Licensed under [EUPL-1.2-or-later](LICENSE).
