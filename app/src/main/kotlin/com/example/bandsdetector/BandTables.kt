package com.example.bandsdetector

/** 一个频段的展示信息 */
data class BandInfo(
    val band: String,        // 例如 "n79" / "B41"
    val freqDesc: String,    // 例如 "4.8~4.9 GHz"
    val is5G: Boolean
)

/** NR-ARFCN → 频段（仅覆盖国内运营商常用范围） */
fun nrArfcnToBand(arfcn: Int): String? = when (arfcn) {
    in 151600..159600 -> "n28"
    in 361000..376000 -> "n3"
    in 422000..434000 -> "n1"
    in 499200..537999 -> "n41"
    in 620000..653333 -> "n78"
    in 693334..733333 -> "n79"
    else -> null
}

/** NR-ARFCN → 下行中心频率 MHz（3GPP TS 38.104） */
fun nrArfcnToFreqMhz(arfcn: Int): Double = when {
    arfcn < 600000 -> arfcn * 0.005
    else -> 3000.0 + (arfcn - 600000) * 0.015
}

/** LTE EARFCN → 频段（仅覆盖国内运营商常用范围） */
fun lteEarfcnToBand(earfcn: Int): String? = when (earfcn) {
    in 0..599 -> "B1"
    in 1200..1949 -> "B3"
    in 2400..2649 -> "B5"
    in 3450..3799 -> "B8"
    in 36200..36349 -> "B34"
    in 37750..38249 -> "B38"
    in 38250..38449 -> "B39"
    in 38650..39649 -> "B40"
    in 39650..41589 -> "B41"
    else -> null
}

/** LTE EARFCN → 下行中心频率 MHz（TS 36.104 常用下行段） */
fun lteEarfcnToFreqMhz(earfcn: Int): Double = when (earfcn) {
    in 0..599 -> 2110.0 + (earfcn - 0) * 0.1
    in 1200..1949 -> 1805.0 + (earfcn - 1200) * 0.1
    in 2400..2649 -> 869.0 + (earfcn - 2400) * 0.1
    in 3450..3799 -> 925.0 + (earfcn - 3450) * 0.1
    in 36200..36349 -> 2010.0 + (earfcn - 36200) * 0.1
    in 37750..38249 -> 2570.0 + (earfcn - 37750) * 0.1
    in 38250..38449 -> 1880.0 + (earfcn - 38250) * 0.1
    in 38650..39649 -> 2300.0 + (earfcn - 38650) * 0.1
    in 39650..41589 -> 2496.0 + (earfcn - 39650) * 0.1
    else -> 0.0
}

/** 运营商 MCCMNC 识别 + 各运营商频段列表 */
object CarrierBands {

    fun nameFor(mccMnc: String?): String = when (mccMnc) {
        "46000", "46002", "46004", "46007", "46008" -> "中国移动"
        "46001", "46006", "46009", "46010" -> "中国联通"
        "46003", "46005", "46011", "46012" -> "中国电信"
        "46015" -> "中国广电"
        else -> "未知运营商"
    }

    /** 返回该运营商在用的全部频段（5G 在前） */
    fun bandsFor(carrier: String): List<BandInfo> = when (carrier) {
        "中国移动" -> listOf(
            BandInfo("n41", "2.6 GHz (2515~2675 MHz)", true),
            BandInfo("n79", "4.9 GHz (4800~4900 MHz)", true),
            BandInfo("n28", "700 MHz (与广电共建)", true),
            BandInfo("B39", "1.9 GHz TDD", false),
            BandInfo("B34", "2.0 GHz TDD", false),
            BandInfo("B38", "2.6 GHz TDD", false),
            BandInfo("B40", "2.3 GHz TDD", false),
            BandInfo("B41", "2.5 GHz TDD", false),
            BandInfo("B3", "1.8 GHz FDD", false),
            BandInfo("B8", "900 MHz FDD", false)
        )
        "中国联通" -> listOf(
            BandInfo("n78", "3.5 GHz (3500~3600 MHz)", true),
            BandInfo("n1", "2.1 GHz FDD", true),
            BandInfo("B1", "2.1 GHz FDD", false),
            BandInfo("B3", "1.8 GHz FDD", false),
            BandInfo("B8", "900 MHz FDD", false)
        )
        "中国电信" -> listOf(
            BandInfo("n78", "3.5 GHz (3400~3500 MHz)", true),
            BandInfo("n1", "2.1 GHz FDD", true),
            BandInfo("B1", "2.1 GHz FDD", false),
            BandInfo("B3", "1.8 GHz FDD", false),
            BandInfo("B5", "850 MHz FDD", false)
        )
        "中国广电" -> listOf(
            BandInfo("n28", "700 MHz", true),
            BandInfo("n79", "4.9 GHz", true)
        )
        else -> emptyList()
    }
}
