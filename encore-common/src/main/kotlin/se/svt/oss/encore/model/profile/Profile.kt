// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

data class Profile(
    val name: String,
    val description: String,
    val encodes: List<OutputProducer>,
    val scaling: String? = "bicubic",
    val deinterlaceFilter: String = "yadif",
    val filterSettings: FilterSettings = FilterSettings(),
    val joinSegmentParams: LinkedHashMap<String, Any?> = linkedMapOf(),
)

data class FilterSettings(
    /**
     * The splitFilter property will be treated differently depending on if the values contains a '=' or not.
     * If no '=' is included, the value is treated as the name of the filter to use and something like
     * 'SPLITFILTERVALUE=N[ou1][out2]...' will be added to the filtergraph, where N is the number of
     * relevant outputs in the profile.
     * If an '=' is included, the value is assumed to already include the size parameters and something like
     * 'SPLITFILTERVALUE[ou1][out2]...' will be added to the filtergraph. Care must be taken to ensure that the
     * size parameters match the number of relevant outputs in the profile.
     * This latter form of specifying the split filter can be useful for
     * certain custom split filters that allow extra parameters, ie ni_quadra_split filter for netinit quadra
     * cards which allows access to scaled output from the decoder.
     */
    val splitFilter: String = "split",
    val scaleFilter: String = "scale",
    val scaleFilterParams: LinkedHashMap<String, String> = linkedMapOf(),
    val cropFilter: String = "crop",
    val padFilter: String = "pad",
)
