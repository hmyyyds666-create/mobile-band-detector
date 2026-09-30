# 检测频段 · Mobile Band Detector

> 一个**无需 root** 的安卓移动网络频段检测 App：打开就能看到「你的手机此刻连的是哪个频段」。

![version](https://img.shields.io/badge/version-Dyaowu--01-blue)
![platform](https://img.shields.io/badge/platform-Android%207.0%2B-green)
![minSdk](https://img.shields.io/badge/minSdk-24-informational)
![license](https://img.shields.io/badge/license-MIT-yellow)

作者：dy嗷.呜

---

## 下载安装

| | |
| --- | --- |
| **最新 APK** | [MobileBandDetector-Dyaowu-01.apk](https://github.com/hmyyyds666-create/mobile-band-detector/releases/download/vDyaowu-01/MobileBandDetector-Dyaowu-01.apk)（16 MB） |
| **全部版本** | [Releases](https://github.com/hmyyyds666-create/mobile-band-detector/releases) |

- 系统要求：Android 7.0（API 24）及以上
- 首次启动需授予**位置权限**（Android 读取基站信息的硬性要求，App 不读 GPS、不联网）
- 当前为 debug 签名包，安装时若提示"未知来源应用"，允许即可

---

## 这是做什么的

手机状态栏只会告诉你「5G」，但不会告诉你连的是 **n41 还是 n79**；运营商宣传的「5G-A 载波聚合」到底聚了哪几个频段，系统更是不提。

这个 App 把系统藏在 `TelephonyManager` 里的底层小区信息挖出来，用人话摆在你面前：

- 当前这张卡连的是**哪个运营商的哪个制式**（5GA / 5G / 5G(NSA) / 4G）
- 正在服务的**频段号**（n41、n79、B41…），以及它对应的频点号和下行中心频率
- 这个运营商**手里一共有哪些频段**，哪几个正在被聚合使用
- 信号强度（RSRP），帮你判断位置好不好

不联网、不上传、不要 root，所有解析都在手机本地完成。

## 界面

从上到下依次是：

1. **当前网络卡片** —— 运营商 + 制式，例如「中国移动 5G」，副行显示频点、频率、RSRP
2. **当前连接频段** —— 大字号高亮卡片，例如 `n41`，一眼就能看到
3. **载波聚合 / 双连接** —— 检测到 CA 或 EN-DC 时，逐条列出参与聚合的频段，标注「主载波 / 辅载波」
4. **运营商在用频段全览** —— 灰色小字列出该运营商全部 5G / 4G 频段（已连接的自动从列表中去重）
5. 底部浅色署名 `By dy嗷.呜`

页面每 **2 秒**自动刷新一次，从后台切回来时立即刷新，所以开关流量、切换 WiFi 后几乎实时看到变化。

## 权限说明

| 权限 | 为什么需要 |
| --- | --- |
| `ACCESS_FINE_LOCATION` | Android 从 9.0 起把「读取基站信息」归为位置相关能力，不授权就读不到小区列表。这也是为什么这类工具都要位置权限——**它并不读你的 GPS 定位，只用来读基站** |
| `ACCESS_COARSE_LOCATION` | 与上者配套申请 |

App 无网络权限，代码里没有任何联网、上传、埋点逻辑。

## 技术实现

| 环节 | 方案 |
| --- | --- |
| 小区读取 | `TelephonyManager.getAllCellInfo()`，优先取 NR 小区，回落 LTE |
| 频段换算 | NR-ARFCN / LTE-EARFCN 区间表 + 3GPP TS 38.104 / 36.104 频率公式 |
| 主辅载波识别 | `CellInfo.getCellConnectionStatus()`（API 28+）；状态未知时回退 `isRegistered` |
| 双卡隔离 | 默认流量卡 PLMN 过滤 + `getSignalStrength()`（按订阅返回）作信号指纹匹配主载波 |
| 5GA 判定 | 主载波为 NR **且**存在辅载波；主 LTE + NR 副腿判定为 NSA 双连接 |
| UI | Jetpack Compose + Material 3 动态取色（Android 12+ 跟随壁纸） |
| 构建 | AGP 8.5.2 / Kotlin 2.0.20 / Compose BOM 2024.09.02 / JDK 17 |

### 双卡为什么难，怎么解的

`getAllCellInfo()` 是**整机范围**的，会把两张卡的小区混在一起返回，而公开的 `CellInfo` API 里**没有 subId 字段**——系统不告诉你某个小区属于哪张卡。只靠 PLMN 过滤，只能区分"不同运营商"的双卡。

这里的解法是：用 `getSignalStrength()`（**按 SIM 订阅**返回，天然单卡）取出流量卡的制式与 RSRP 作为"指纹"，再回小区列表里匹配，把流量卡的主载波钉出来，其余主载波一律排除。匹配采用降级链：`RAT + RSRP 精确匹配` → `仅 RAT 匹配` → `第一个主载波`，保证任何 ROM 下界面都不空白。

### 已知限制

- 辅载波是否出现在系统上报里**取决于厂商 ROM**。部分机型只上报主小区，此时聚合列表会为空——这是系统接口限制，不是 App 缺陷。
- 频段区间表覆盖国内四大运营商（移动 / 联通 / 电信 / 广电）常用频段，境外频段可能显示为 `n?` / `B?`。

## 构建

环境：JDK 17、Android SDK（compileSdk 34 / build-tools 34.0.0）、Gradle 8.9。

```bash
# 1. 在项目根目录创建 local.properties，指向你的 SDK
echo "sdk.dir=/path/to/Android/sdk" > local.properties

# 2. 构建 debug 包
./gradlew assembleDebug

# 产物：app/build/outputs/apk/debug/app-debug.apk
```

Windows 下若项目路径含中文，`gradle.properties` 中的 `android.overridePathCheck=true` 必须保留。

## 项目结构

```
app/src/main/kotlin/com/example/bandsdetector/
├── MainActivity.kt      # 入口：权限申请 + 生命周期内重检权限
├── BandsScreen.kt       # 主界面（Compose / Material 3）
├── CellMonitor.kt       # 小区读取、双卡隔离、CA/NSA 判定
├── BandTables.kt        # ARFCN↔频段换算表、运营商频段清单
└── ui/theme/            # Material 3 主题（动态取色）
```

## License

[MIT](LICENSE) © 2026 dy嗷.呜
