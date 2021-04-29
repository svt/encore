# Encore
[![License](https://img.shields.io/badge/license-EUPL-brightgreen.svg)](https://eupl.eu/)
[![REUSE status](https://api.reuse.software/badge/github.com/fsfe/reuse-tool)](https://api.reuse.software/info/github.com/fsfe/reuse-tool)


<!--
<img align="right" height="120" src="https://raw.githubusercontent.com/svt/encore/encore_logo.png">
-->

Encore is a scalable video transcoding tool, built on Open Source giants like [FFmpeg](https://www.ffmpeg.org/) and [Redisson](https://github.com/redisson).

Encore was created as to provide an easy way to scale and somewhat abstract the power of FFmpeg. 

Encore is aimed at the advanced technical user that needs a scalable video transcoding tool - for example as a part of a VoD (Video On Demand) Transcoding pipeline.

## Features

- Scalable, with queuing possibilities
- Profile Configuration
- Possibility to extend FFmpeg functionality
- Tested and tried in production

Encore is not 


- A live/stream transcoder
- Having packager functions (like Shaka, Bento and friends).
- Having a GUI

## Built with

- Kotlin
- Spring Boot
- Gradle
- FFmpeg
- mediainfo
- Redisson
- and many other great projects

## Documentation

Comprehensive documentation for Encore can (and should) be read at:

[Online](https://svt.github.io/encore-doc/)

or can be downloaded from it's

[GitHub Repo](https://github.com/svt/encore-doc)

If you have a running instance you can also directly see your
**OpenAPI-endpoints**:

```
http(s)://yourinstance/swagger-ui.html

as json

http(s)://yourinstance/v3/api-docs/

or as yaml

http(s)://yourinstance/v3/api-docs.yaml
```

### Local development

Please, see the documentation, but here are a few hints.

**Make sure you have Redis, FFmpeg and Mediainfo installed. for example from the [SVT Brew AVTools Tap](https://github.com/svt/homebrew-avtools)**

To run all tests and build:

```
$ ./gradlew clean build
```

To run an instance on your local machine 
```
$ SPRING_PROFILES_ACTIVE=local ./gradlew clean bootRun
```

To run a local Docker Image, you must first have a Docker Image with FFmpeg and tools installed <see documentation>, and then you could it use like this:
```
 
$ docker build -t encore --build-arg DOCKER_BASE_IMAGE=<yourdockerbaseimage>. && docker run -e SPRING_PROFILES_ACTIVE='local' encore
```

## License

Encore is licensed under the 

[EUPL-1.2-or-later](LICENSE)