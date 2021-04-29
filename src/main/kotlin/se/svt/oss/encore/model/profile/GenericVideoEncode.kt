// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

data class GenericVideoEncode(
    override val width: Int?,
    override val height: Int?,
    override val twoPass: Boolean,
    override val params: LinkedHashMap<String, String>,
    override val filters: List<String> = emptyList(),
    override val audioEncode: AudioEncode?,
    override val suffix: String,
    override val format: String,
    override val codec: String
) : VideoEncode
