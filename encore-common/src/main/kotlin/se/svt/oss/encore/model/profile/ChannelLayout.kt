// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import com.fasterxml.jackson.annotation.JsonValue
import se.svt.oss.encore.model.profile.ChannelId.BC
import se.svt.oss.encore.model.profile.ChannelId.BFC
import se.svt.oss.encore.model.profile.ChannelId.BFL
import se.svt.oss.encore.model.profile.ChannelId.BFR
import se.svt.oss.encore.model.profile.ChannelId.BL
import se.svt.oss.encore.model.profile.ChannelId.BR
import se.svt.oss.encore.model.profile.ChannelId.DL
import se.svt.oss.encore.model.profile.ChannelId.DR
import se.svt.oss.encore.model.profile.ChannelId.FC
import se.svt.oss.encore.model.profile.ChannelId.FL
import se.svt.oss.encore.model.profile.ChannelId.FLC
import se.svt.oss.encore.model.profile.ChannelId.FR
import se.svt.oss.encore.model.profile.ChannelId.FRC
import se.svt.oss.encore.model.profile.ChannelId.LFE
import se.svt.oss.encore.model.profile.ChannelId.LFE2
import se.svt.oss.encore.model.profile.ChannelId.SL
import se.svt.oss.encore.model.profile.ChannelId.SR
import se.svt.oss.encore.model.profile.ChannelId.TBC
import se.svt.oss.encore.model.profile.ChannelId.TBL
import se.svt.oss.encore.model.profile.ChannelId.TBR
import se.svt.oss.encore.model.profile.ChannelId.TC
import se.svt.oss.encore.model.profile.ChannelId.TFC
import se.svt.oss.encore.model.profile.ChannelId.TFL
import se.svt.oss.encore.model.profile.ChannelId.TFR
import se.svt.oss.encore.model.profile.ChannelId.TSL
import se.svt.oss.encore.model.profile.ChannelId.TSR
import se.svt.oss.encore.model.profile.ChannelId.WL
import se.svt.oss.encore.model.profile.ChannelId.WR

enum class ChannelLayout(@JsonValue val layoutName: String, val channels: List<ChannelId>) {
    CH_LAYOUT_MONO("mono", listOf(FC)),
    CH_LAYOUT_STEREO("stereo", listOf(FL, FR)),
    CH_LAYOUT_2POINT1("2.1", listOf(FL, FR, LFE)),
    CH_LAYOUT_3POINT0("3.0", listOf(FL, FR, FC)),
    CH_LAYOUT_3POINT0_BACK("3.0(back)", listOf(FL, FR, BC)),
    CH_LAYOUT_4POINT0("4.0", listOf(FL, FR, FC, BC)),
    CH_LAYOUT_QUAD("quad", listOf(FL, FR, BL, BR)),
    CH_LAYOUT_QUAD_SIDE("quad(side)", listOf(FL, FR, SL, SR)),
    CH_LAYOUT_3POINT1("3.1", listOf(FL, FR, FC, LFE)),
    CH_LAYOUT_5POINT0("5.0", listOf(FL, FR, FC, BL, BR)),
    CH_LAYOUT_5POINT0_SIDE("5.0(side)", listOf(FL, FR, FC, SL, SR)),
    CH_LAYOUT_4POINT1("4.1", listOf(FL, FR, FC, LFE, BC)),
    CH_LAYOUT_5POINT1("5.1", listOf(FL, FR, FC, LFE, BL, BR)),
    CH_LAYOUT_5POINT1_SIDE("5.1(side)", listOf(FL, FR, FC, LFE, SL, SR)),
    CH_LAYOUT_6POINT0("6.0", listOf(FL, FR, FC, BC, SL, SR)),
    CH_LAYOUT_6POINT0_FRONT("6.0(front)", listOf(FL, FR, FLC, FRC, SL, SR)),
    CH_LAYOUT_3POINT1POINT2("3.1.2", listOf(FL, FR, FC, LFE, TFL, TFR)),
    CH_LAYOUT_HEXAGONAL("hexagonal", listOf(FL, FR, FC, BL, BR, BC)),
    CH_LAYOUT_6POINT1("6.1", listOf(FL, FR, FC, LFE, BC, SL, SR)),
    CH_LAYOUT_6POINT1_BACK("6.1(back)", listOf(FL, FR, FC, LFE, BL, BR, BC)),
    CH_LAYOUT_6POINT1_FRONT("6.1(front)", listOf(FL, FR, LFE, FLC, FRC, SL, SR)),
    CH_LAYOUT_7POINT0("7.0", listOf(FL, FR, FC, BL, BR, SL, SR)),
    CH_LAYOUT_7POINT0_FRONT("7.0(front)", listOf(FL, FR, FC, FLC, FRC, SL, SR)),
    CH_LAYOUT_7POINT1("7.1", listOf(FL, FR, FC, LFE, BL, BR, SL, SR)),
    CH_LAYOUT_7POINT1_WIDE("7.1(wide)", listOf(FL, FR, FC, LFE, BL, BR, FLC, FRC)),
    CH_LAYOUT_7POINT1_WIDE_SIDE("7.1(wide-side)", listOf(FL, FR, FC, LFE, FLC, FRC, SL, SR)),
    CH_LAYOUT_5POINT1POINT2("5.1.2", listOf(FL, FR, FC, LFE, BL, BR, TFL, TFR)),
    CH_LAYOUT_OCTAGONAL("octagonal", listOf(FL, FR, FC, BL, BR, BC, SL, SR)),
    CH_LAYOUT_CUBE("cube", listOf(FL, FR, BL, BR, TFL, TFR, TBL, TBR)),
    CH_LAYOUT_5POINT1POINT4("5.1.4", listOf(FL, FR, FC, LFE, BL, BR, TFL, TFR, TBL, TBR)),
    CH_LAYOUT_7POINT1POINT2("7.1.2", listOf(FL, FR, FC, LFE, BL, BR, SL, SR, TFL, TFR)),
    CH_LAYOUT_7POINT1POINT4("7.1.4", listOf(FL, FR, FC, LFE, BL, BR, SL, SR, TFL, TFR, TBL, TBR)),
    CH_LAYOUT_7POINT2POINT3("7.2.3", listOf(FL, FR, FC, LFE, BL, BR, SL, SR, TFL, TFR, TBC, LFE2)),
    CH_LAYOUT_9POINT1POINT4("9.1.4", listOf(FL, FR, FC, LFE, BL, BR, FLC, FRC, SL, SR, TFL, TFR, TBL, TBR)),
    CH_LAYOUT_HEXADECAGONAL(
        "hexadecagonal",
        listOf(
            FL, FR, FC, BL, BR, BC, SL, SR, TFL, TFC, TFR, TBL, TBC, TBR, WL, WR
        )
    ),
    CH_LAYOUT_DOWNMIX("downmix)", listOf(DL, DR)),
    CH_LAYOUT_22POINT2(
        "22.2",
        listOf(
            FL,
            FR,
            FC,
            LFE,
            BL,
            BR,
            FLC,
            FRC,
            BC,
            SL,
            SR,
            TC,
            TFL,
            TFC,
            TFR,
            TBL,
            TBC,
            TBR,
            LFE2,
            TSL,
            TSR,
            BFC,
            BFL,
            BFR
        )
    );

    companion object {
        fun defaultChannelLayout(numChannels: Int) = entries.firstOrNull { it.channels.size == numChannels }
        fun getByNameOrNull(layoutName: String) = entries.firstOrNull { it.layoutName == layoutName }
    }
}
