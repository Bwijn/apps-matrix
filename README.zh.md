<p align="center">
  <img src="banner.svg" alt="AppsMatrix" width="100%"/>
</p>

<p align="center">
  <a href="../../releases/latest"><img src="https://img.shields.io/github/v/release/Bwijn/apps-matrix?style=flat-square" alt="Release"/></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-brightgreen?style=flat-square&logo=android" alt="Android 8.0+"/>
  <img src="https://img.shields.io/badge/LSPosed-compatible-orange?style=flat-square" alt="LSPosed"/>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/Bwijn/apps-matrix?style=flat-square" alt="License"/></a>
</p>

<p align="center"><b>中文 | <a href="README.md">English</a></b></p>

一个 LSPosed 模块，给每个 App **单独造一套环境** —— 运营商、语言、时区，各管各的。TikTok 以为你在纽约连着 T-Mobile，京东该看中国移动还是中国移动。每个 App 活在自己的 Matrix 里，谁也不知道隔壁是什么。

## 为什么要用

`resetprop` 一把梭全局改运营商，京东淘宝一看你是 T-Mobile + en_US，风控直接拉闸 —— 弹验证、踢登录、封号预警。不改吧，TikTok Claude ChatGPT 又用不了。两头堵。

AppsMatrix **按进程 Hook**，每个 App 只看到你喂给它的身份，其他 App 该怎样还怎样。干净，不串味。

## 装模块

1. 去 [**Releases**](../../releases/latest) 下 APK
2. 装上：`adb install -r apps-matrix.apk`
3. **LSPosed 管理器** → 启用 **AppsMatrix** → 勾上要伪装的 App
4. 重启

完事。打开 App 就已经活在 Matrix 里了。

## 配置

改 `app/src/main/assets/matrix.json`，一个包名一套人设：

```json
{
  "com.zhiliaoapp.musically": {
    "label": "TikTok",
    "sim_operator": "310260",
    "sim_operator_name": "T-Mobile",
    "sim_country": "us",
    "network_operator": "310260",
    "network_operator_name": "T-Mobile",
    "network_country": "us",
    "locale_language": "en",
    "locale_country": "US",
    "timezone": "America/New_York"
  }
}
```

所有字段必须**自洽** —— 运营商填美国、语言填中文、时区填上海，这种穿帮人设一秒被风控抓。懂的都懂。

<details>
<summary><b>常用运营商代码</b></summary>

| 国家 | 运营商 | 代码 |
|------|--------|------|
| 美国 | T-Mobile | `310260` |
| 美国 | AT&T | `310410` |
| 美国 | Verizon | `311480` |
| 中国 | 中国移动 | `46000` |
| 中国 | 中国联通 | `46001` |
| 中国 | 中国电信 | `46003` |
| 日本 | NTT Docomo | `44010` |
| 英国 | EE | `23430` |

</details>

## 已知局限

- 配置写死在 APK 里，换目标得重新编译
- 只 Hook Java 层，走 NDK `__system_property_get` 的管不到
- 只管 SIM / 网络 / 语言 / 时区，不碰 IMEI 和设备指纹

## 交流群

<a href="https://t.me/AppsMatrixChat"><img src="tg_group_qrcode.png" alt="Telegram Group" width="200"/></a>

[**加入 Telegram 群**](https://t.me/AppsMatrixChat) — 反馈、交流、更新通知。

## License

GPL-3.0
