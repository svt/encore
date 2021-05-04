# SVT Encore
[![License](https://img.shields.io/badge/license-EUPL-brightgreen.svg)](https://eupl.eu/)
[![REUSE status](https://api.reuse.software/badge/github.com/fsfe/reuse-tool)](https://api.reuse.software/info/github.com/fsfe/reuse-tool)


<img align="center" width="30%" src="https://raw.githubusercontent.com/svt/encore-doc/main/src/img/svt_encore_logo.png">

&nbsp;
&nbsp;
  
SVT *Encore* is a scalable video transcoding tool, built on Open Source giants like [FFmpeg](https://www.ffmpeg.org/) and [Redisson](https://github.com/redisson).


*Encore* was created to scale, and abstract the transcoding _power of FFmpeg_, and to offer a simple solution for Transcoding - Transcoding-as-a-Service.

*Encore* is aimed at the advanced technical user that needs a scalable video transcoding tool - for example, as a part of their VOD (Video On Demand) transcoding pipeline.

## Features

- Scalable - queuing and concurrency options
- Flexible profile configuration
- Possibility to extend FFmpeg functionality
- Tested and tried in production

_Encore_ is not

- A live/stream transcoder
- A Video packager (see <<faq>>)
- An GUI application

_Built with_

* Kotlin
* Gradle
* Spring Boot
* FFmpeg
* Redisson
* and many other great projects

## Documentation

Comprehensive documentation for _Encore_ can (and should) be read:

[Online](https://svt.github.io/encore-doc/)

or downloaded from the:

[GitHub Repository](https://github.com/svt/encore-doc)

If you have a running instance, you can also view the

**OpenAPI Endpoints**:

```
http(s)://yourinstance/swagger-ui.html

as json

http(s)://yourinstance/v3/api-docs/

or as yaml

http(s)://yourinstance/v3/api-docs.yaml
```

### Local development

Please see the [online documentation](https://svt.github.io/encore-doc/#the-user-guide)

## License

Copyright 2020 Sveriges Television AB.

Encore is licensed under the 

[EUPL-1.2-or-later](LICENSE) license

## Primary maintainer

SVT Videcore Team - (videocore svt se)