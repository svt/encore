// SPDX-FileCopyrightText: 2026 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type",
    defaultImpl = DialogueEnhancement.Native::class,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = DialogueEnhancement.Native::class, name = "native"),
    JsonSubTypes.Type(value = DialogueEnhancement.Dn::class, name = "dn"),
)
sealed class DialogueEnhancement {

    abstract val enabled: Boolean
    abstract val sidechainCompress: SidechainCompress

    abstract fun supports(inputChannelLayout: ChannelLayout): Boolean

    abstract fun build(inputChannelLayout: ChannelLayout, suffix: String): FilterPlan

    protected fun sidechainPipeline(
        layout: ChannelLayout,
        suffix: String,
        enhancedFC: String?,
    ): String {
        val layoutName = layout.layoutName
        val channels = layout.channels
        val channelSplit =
            "channelsplit=channel_layout=$layoutName${channels.joinToString(separator = "") { "[CH-$suffix-$it]" }}"
        val centerSplitFilters = listOfNotNull(enhancedFC, "asplit=2")
            .joinToString(",")
        val centerSplit = "[CH-$suffix-FC]$centerSplitFilters[SC-$suffix][CH-$suffix-FC-OUT]"
        val bgChannels = channels - ChannelId.FC
        val bgMerge =
            "${bgChannels.joinToString(separator = "") { "[CH-$suffix-$it]" }}amerge=inputs=${bgChannels.size}[BG-$suffix]"
        val compress =
            "[BG-$suffix][SC-$suffix]${sidechainCompress.filterString}[COMPR-$suffix]"
        val mixMerge = "[COMPR-$suffix][CH-$suffix-FC-OUT]amerge"
        return listOf(channelSplit, centerSplit, bgMerge, compress, mixMerge).joinToString(";")
    }

    /** FFmpeg built-in af_dialoguenhance (classical mid-side / FFT center extraction). */
    data class Native(
        override val enabled: Boolean = false,
        val dialogueEnhanceStereo: DialogueEnhanceStereo = DialogueEnhanceStereo(),
        override val sidechainCompress: SidechainCompress = SidechainCompress(),
    ) : DialogueEnhancement() {

        override fun supports(inputChannelLayout: ChannelLayout): Boolean = when {
            inputChannelLayout == ChannelLayout.CH_LAYOUT_STEREO && dialogueEnhanceStereo.enabled -> true
            inputChannelLayout.channels.size > 1 && inputChannelLayout.channels.contains(ChannelId.FC) -> true
            else -> false
        }

        override fun build(inputChannelLayout: ChannelLayout, suffix: String): FilterPlan {
            val filters = mutableListOf<String>()
            var effective = inputChannelLayout
            if (inputChannelLayout == ChannelLayout.CH_LAYOUT_STEREO && dialogueEnhanceStereo.enabled) {
                filters.add(dialogueEnhanceStereo.filterString)
                effective = ChannelLayout.CH_LAYOUT_3POINT0
            }
            filters.add(sidechainPipeline(effective, suffix, enhancedFC = null))
            return FilterPlan(filters, effective)
        }
    }

    /**
     * Neural enhancement via the `af_dnenhance` FFmpeg filter (DeepFilterNet 3
     * via libdf). Requires a patched FFmpeg build that includes the filter —
     * profiles using this variant against vanilla FFmpeg fail at encode time
     * with "No such filter: 'dnenhance'".
     */
    data class Dn(
        override val enabled: Boolean = false,
        /** Path to a DFN3 model tarball. Null → filter auto-discovers (e.g. brew-installed). */
        val model: String? = null,
        /** Enable DFN3 post-filter. Null → use filter default. */
        val postFilter: Boolean? = null,
        /** Maximum suppression in dB (filter caps the model's gain reduction). Null → use filter default. */
        val attenuationLimit: Double? = null,
        /** Algorithmic lookahead in 480-sample hops (0 for DFN3-LL, 2 for DFN3). Null → use filter default. */
        val lookahead: Int? = null,
        override val sidechainCompress: SidechainCompress = SidechainCompress(),
    ) : DialogueEnhancement() {

        override fun supports(inputChannelLayout: ChannelLayout): Boolean =
            inputChannelLayout == ChannelLayout.CH_LAYOUT_STEREO || ChannelId.FC in inputChannelLayout.channels

        override fun build(inputChannelLayout: ChannelLayout, suffix: String): FilterPlan = when (inputChannelLayout) {
            ChannelLayout.CH_LAYOUT_MONO ->
                FilterPlan(
                    listOf(
                        monoBridge(suffix),
                        sidechainPipeline(ChannelLayout.CH_LAYOUT_3POINT0, suffix, enhancedFC = dnenhanceCall()),
                    ),
                    ChannelLayout.CH_LAYOUT_3POINT0,
                )

            ChannelLayout.CH_LAYOUT_STEREO ->
                FilterPlan(
                    listOf(
                        stereoBridge(suffix),
                        sidechainPipeline(ChannelLayout.CH_LAYOUT_3POINT0, suffix, enhancedFC = dnenhanceCall()),
                    ),
                    ChannelLayout.CH_LAYOUT_3POINT0,
                )

            else -> FilterPlan(
                listOf(sidechainPipeline(inputChannelLayout, suffix, enhancedFC = dnenhanceCall())),
                inputChannelLayout,
            )
        }

        private fun dnenhanceCall(): String {
            val args = buildList {
                if (!model.isNullOrBlank()) add("model=$model")
                postFilter?.let { add("post_filter=${if (it) 1 else 0}") }
                attenuationLimit?.let { add("attenuation_limit=$it") }
                lookahead?.let { add("lookahead=$it") }
            }
            return if (args.isEmpty()) "dnenhance" else "dnenhance=${args.joinToString(":")}"
        }

        private fun stereoBridge(suffix: String): String =
            "asplit=2[DN-$suffix-LR][DN-$suffix-SC];" +
                "[DN-$suffix-SC]pan=mono|c0=0.707*c0+0.707*c1[DN-$suffix-FC];" +
                "[DN-$suffix-LR][DN-$suffix-FC]join=inputs=2:channel_layout=3.0"

        private fun monoBridge(suffix: String): String =
            "asplit=2[DN-$suffix-BG][DN-$suffix-FC];" +
                "[DN-$suffix-BG]pan=stereo|c0=0.707*c0|c1=0.707*c0[DN-$suffix-LR];" +
                "[DN-$suffix-LR][DN-$suffix-FC]join=inputs=2:channel_layout=3.0"
    }

    data class DialogueEnhanceStereo(
        val enabled: Boolean = true,
        val original: Int = 1,
        val enhance: Int = 1,
        val voice: Int = 2,
    ) {
        val filterString: String
            get() = "dialoguenhance=original=$original:enhance=$enhance:voice=$voice"
    }

    data class SidechainCompress(
        val ratio: Int = 8,
        val threshold: Double = 0.012,
        val release: Double = 1000.0,
        val attack: Double = 100.0,
    ) {
        val filterString: String
            get() = "sidechaincompress=threshold=$threshold:ratio=$ratio:release=$release:attack=$attack"
    }

    data class FilterPlan(
        val filters: List<String>,
        val effectiveChannelLayout: ChannelLayout,
    )
}
