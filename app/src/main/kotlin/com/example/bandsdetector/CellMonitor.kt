package com.example.bandsdetector

import android.content.Context
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager

/** 一个正在服务的小区（主载波或辅载波） */
data class ServingCell(
    val rat: String,         // "NR" / "LTE"
    val band: String,        // "n79" / "B41"
    val arfcn: Int,
    val freqMhz: Double,
    val rsrp: Int,
    val isPrimary: Boolean,           // true=主载波(PCell)，false=辅载波(SCell)
    val explicitSecondary: Boolean = false  // 系统明确上报为 SECONDARY_SERVING
)

/** 一次完整的网络快照 */
data class NetworkSnapshot(
    val carrier: String,                  // "中国移动" 等
    val tech: String,                     // "5GA" / "5G" / "5G(NSA)" / "4G" / ""
    val primary: ServingCell?,            // 主载波
    val aggregated: List<ServingCell>,    // 全部服务小区（含主载波）
    val hasData: Boolean
)

/** 用于把流量卡信号和小区做匹配的 key：制式 + 信号强度 */
private data class CellKey(val rat: String, val dbm: Int)

/**
 * 读取当前服务小区快照（只统计「默认流量卡」）。
 *
 * 双卡难点：allCellInfo 是整机范围、且 CellInfo 公开 API 不含 subId，
 * 同运营商双卡时 PLMN 过滤无法区分两张卡。
 * 解法：getSignalStrength() 是按订阅（单卡）返回的，取流量卡的
 * RAT+RSRP 作为指纹，在小区列表里匹配出流量卡自己的主载波，
 * 其余主载波（副卡的）一律排除；辅载波只保留系统明确上报
 * CONNECTION_SECONDARY_SERVING 的。
 */
fun readNetworkSnapshot(context: Context): NetworkSnapshot {
    return try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // 默认流量卡对应的 TelephonyManager（API 24+）
        val dataTm: TelephonyManager = try {
            val subId = android.telephony.SubscriptionManager.getDefaultDataSubscriptionId()
            if (subId != android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                tm.createForSubscriptionId(subId) ?: tm
            } else tm
        } catch (e: Exception) { tm }

        // networkOperator 无服务时可能为空串，回退用 simOperator（SIM 归属 PLMN）
        val plmn = try {
            val net = dataTm.networkOperator
            if (net.isNullOrEmpty()) dataTm.simOperator else net
        } catch (e: Exception) { null }
        val carrier = CarrierBands.nameFor(plmn)
        val empty = NetworkSnapshot(carrier, "", null, emptyList(), hasData = false)

        // 流量卡自己的信号指纹（API 29+ getCellSignalStrengths；按订阅返回，天然单卡）
        val dataKeys: Set<CellKey> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                dataTm.signalStrength?.cellSignalStrengths?.flatMap { css ->
                    when (css) {
                        is CellSignalStrengthNr -> listOf(
                            CellKey("NR", css.dbm),
                            CellKey("NR", css.ssRsrp),
                            CellKey("NR", css.csiRsrp)
                        )
                        is CellSignalStrengthLte -> listOf(
                            CellKey("LTE", css.dbm),
                            CellKey("LTE", css.rsrp)
                        )
                        else -> emptyList()
                    }
                }?.toSet() ?: emptySet()
            } else emptySet()
        } catch (e: Exception) { emptySet() }
        val dataRats = dataKeys.map { it.rat }.toSet()

        val infos = dataTm.allCellInfo ?: tm.allCellInfo ?: return empty
        val serving = mutableListOf<ServingCell>()

        for (info in infos) {
            // 先按流量卡 PLMN 过滤（排除异运营商副卡/邻区）
            // 注意：getMccString/getMncString 只在 CellIdentity 的子类上，基类没有
            if (!plmn.isNullOrEmpty()) {
                val cellPlmn: String? = when (info) {
                    is CellInfoNr -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val id = info.cellIdentity as android.telephony.CellIdentityNr
                        (id.mccString ?: "") + (id.mncString ?: "")
                    } else null
                    is CellInfoLte -> {
                        val id = info.cellIdentity
                        (id.mccString ?: "") + (id.mncString ?: "")
                    }
                    else -> null
                }
                if (!cellPlmn.isNullOrEmpty() && cellPlmn != plmn) continue
            }

            // 主/辅/邻区判定：API 28+ 有 connectionStatus，但很多 ROM 上报
            // CONNECTION_UNKNOWN/NONE，此时回退 isRegistered（注册小区一定是某张卡的主载波）
            val isPrimary: Boolean
            val isServing: Boolean
            val explicitSecondary: Boolean
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                when (info.cellConnectionStatus) {
                    CellInfo.CONNECTION_PRIMARY_SERVING -> {
                        isPrimary = true; isServing = true; explicitSecondary = false
                    }
                    CellInfo.CONNECTION_SECONDARY_SERVING -> {
                        isPrimary = false; isServing = true; explicitSecondary = true
                    }
                    else -> {
                        isPrimary = info.isRegistered
                        isServing = info.isRegistered
                        explicitSecondary = false
                    }
                }
            } else {
                isPrimary = info.isRegistered
                isServing = info.isRegistered
                explicitSecondary = false
            }
            if (!isServing) continue

            when (info) {
                is CellInfoNr -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val id = info.cellIdentity as android.telephony.CellIdentityNr
                        val ss = info.cellSignalStrength as CellSignalStrengthNr
                        val arfcn = id.nrarfcn
                        if (arfcn == Int.MAX_VALUE || arfcn <= 0) continue
                        serving += ServingCell(
                            rat = "NR",
                            band = nrArfcnToBand(arfcn) ?: "n?",
                            arfcn = arfcn,
                            freqMhz = nrArfcnToFreqMhz(arfcn),
                            rsrp = ss.ssRsrp,
                            isPrimary = isPrimary,
                            explicitSecondary = explicitSecondary
                        )
                    }
                }
                is CellInfoLte -> {
                    val id = info.cellIdentity
                    val ss: CellSignalStrengthLte = info.cellSignalStrength
                    val earfcn = id.earfcn
                    if (earfcn == Int.MAX_VALUE || earfcn <= 0) continue
                    // getRsrp() 需要 API 26，minSdk 24 直接用会在老设备 NoSuchMethodError
                    // 闪退；LTE 下 getDbm() 返回值即 RSRP，全 API 可用
                    serving += ServingCell(
                        rat = "LTE",
                        band = lteEarfcnToBand(earfcn) ?: "B?",
                        arfcn = earfcn,
                        freqMhz = lteEarfcnToFreqMhz(earfcn),
                        rsrp = ss.dbm,
                        isPrimary = isPrimary,
                        explicitSecondary = explicitSecondary
                    )
                }
            }
        }

        if (serving.isEmpty()) return empty

        // 用流量卡的信号指纹钉出它的主载波：
        // 1) RAT+RSRP 精确匹配  2) 仅 RAT 匹配  3) 第一个主载波  4) 第一个小区
        val primaries = serving.filter { it.isPrimary }
        val primary = primaries.firstOrNull { CellKey(it.rat, it.rsrp) in dataKeys }
            ?: primaries.firstOrNull { dataRats.isNotEmpty() && it.rat in dataRats }
            ?: primaries.firstOrNull()
            ?: serving.first().copy(isPrimary = true)

        // 聚合列表 = 流量卡主载波 + 系统明确上报为辅载波的小区。
        // 其他"主载波"（即副卡的小区）一律排除——这就是双卡合并显示根因的修复点。
        val aggregated = (listOf(primary) + serving.filter {
            it.explicitSecondary && !(it.rat == primary.rat && it.arfcn == primary.arfcn)
        }).distinctBy { it.rat to it.arfcn }

        // 5GA 的严格语义：主载波必须是 NR，且同卡检测到辅载波（真载波聚合）。
        // 若主载波是 LTE、副腿是 NR，那是 NSA（EN-DC）双连接，不是 5GA。
        val primaryIsNr = primary.rat == "NR"
        val hasNr = aggregated.any { it.rat == "NR" }
        val tech = when {
            primaryIsNr && aggregated.size > 1 -> "5GA"
            primaryIsNr -> "5G"
            hasNr -> "5G(NSA)"
            else -> "4G"
        }

        NetworkSnapshot(
            carrier = carrier,
            tech = tech,
            primary = primary,
            aggregated = aggregated,
            hasData = true
        )
    } catch (e: SecurityException) {
        NetworkSnapshot(readCarrierName(context), "", null, emptyList(), hasData = false)
    } catch (e: Exception) {
        NetworkSnapshot(readCarrierName(context), "", null, emptyList(), hasData = false)
    }
}

/** 读取运营商名（基于 MCCMNC，不受系统语言影响） */
fun readCarrierName(context: Context): String {
    return try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        CarrierBands.nameFor(tm.networkOperator)
    } catch (e: Exception) {
        "未知运营商"
    }
}
