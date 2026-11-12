package com.vmap.app.core.text

import java.util.Locale

/**
 * 从演出名里认「系列」：优先取书名号里的内容
 * （「洛天依 2026「无限共鸣·纯蓝幻乐」· 上海站」→ 无限共鸣·纯蓝幻乐）；
 * 没有书名号的（Vsinger Live 2017 全息演唱会）就取「·」前面那一段。
 *
 * 地图的换系列提示、系列歌单、场次筛选都用这一份，别各写一套。
 */
fun seriesOfEventName(title: String): String {
    val m = Regex("「([^」]{1,24})」").find(title)
    if (m != null) return m.groupValues[1].trim()
    return title.substringBefore('·').trim()
}

/** 把 "2026.08.22" 里的月日取成 "08.22"，没有就返回原样的年份。 */
fun monthDayOf(dateLabel: String): String {
    val parts = dateLabel.split(".")
    if (parts.size >= 3) {
        val m = parts[1].toIntOrNull()
        val d = parts[2].toIntOrNull()
        if (m != null && d != null) return String.format(Locale.US, "%02d.%02d", m, d)
    }
    if (parts.size == 2) {
        val m = parts[1].toIntOrNull()
        if (m != null) return String.format(Locale.US, "%02d", m)
    }
    return dateLabel
}

/**
 * 歌名比对用的宽松形式：小写、去掉括号副标题、去掉空格和常见标点。
 *
 * 节目单上写「反乌托邦Pt.2」，库里可能是「反乌托邦Pt2」；
 * 节目单写「黑凤梨」，库里是「黑凤梨(洛天依AI)」——归一化之后才能对上。
 */
fun looseSongTitle(t: String): String {
    var s = t.lowercase()
    s = Regex("[（(\\[【].*?[）)\\]】]").replace(s, "")
    s = s.replace(Regex("[\\s\\-·—~.,:;!?、'\"“”‘’]"), "")
    return s.trim()
}
