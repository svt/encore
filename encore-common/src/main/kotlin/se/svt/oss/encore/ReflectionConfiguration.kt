package se.svt.oss.encore

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration
import se.svt.oss.encore.model.profile.AudioEncode
import se.svt.oss.encore.model.profile.AudioEncoder
import se.svt.oss.encore.model.profile.GenericVideoEncode
import se.svt.oss.encore.model.profile.OutputProducer
import se.svt.oss.encore.model.profile.Profile
import se.svt.oss.encore.model.profile.SimpleAudioEncode
import se.svt.oss.encore.model.profile.ThumbnailEncode
import se.svt.oss.encore.model.profile.ThumbnailMapEncode
import se.svt.oss.encore.model.profile.VideoEncode
import se.svt.oss.encore.model.profile.X264Encode
import se.svt.oss.encore.model.profile.X265Encode
import se.svt.oss.encore.model.profile.X26XEncode

@RegisterReflectionForBinding(
    AudioEncode::class,
    AudioEncoder::class,
    GenericVideoEncode::class,
    OutputProducer::class,
    Profile::class,
    SimpleAudioEncode::class,
    ThumbnailEncode::class,
    ThumbnailMapEncode::class,
    VideoEncode::class,
    X26XEncode::class,
    X264Encode::class,
    X265Encode::class
)
@Configuration
class ReflectionConfiguration
