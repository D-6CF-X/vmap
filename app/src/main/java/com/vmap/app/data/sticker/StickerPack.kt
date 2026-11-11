package com.vmap.app.data.sticker

/** 一套表情包里的一个：assets 路径 + 中文名。 */
data class Sticker(
    val asset: String,
    val label: String,
)

/**
 * 内置表情包。目前只有 2026「无限共鸣·纯蓝幻乐」官方那一套（20 张，162×162 GIF），
 * 挂在对应场次的详情页里，点开看动图。
 */
object StickerPack {

    val chunlan: List<Sticker> = listOf(
        Sticker("stickers/st01.gif", "登场"),
        Sticker("stickers/st02.gif", "魔法"),
        Sticker("stickers/st03.gif", "唱歌"),
        Sticker("stickers/st04.gif", "不要"),
        Sticker("stickers/st05.gif", "吃"),
        Sticker("stickers/st06.gif", "大哭"),
        Sticker("stickers/st07.gif", "展示"),
        Sticker("stickers/st08.gif", "惊恐"),
        Sticker("stickers/st09.gif", "送花"),
        Sticker("stickers/st10.gif", "忧伤"),
        Sticker("stickers/st11.gif", "打call"),
        Sticker("stickers/st12.gif", "思考"),
        Sticker("stickers/st13.gif", "拥抱"),
        Sticker("stickers/st14.gif", "放手一搏"),
        Sticker("stickers/st15.gif", "hello"),
        Sticker("stickers/st16.gif", "邀请"),
        Sticker("stickers/st17.gif", "嘻嘻"),
        Sticker("stickers/st18.gif", "求求"),
        Sticker("stickers/st19.gif", "感动"),
        Sticker("stickers/st20.gif", "生气"),
    )

    /** 按场次名挑表情包：2026 纯蓝幻乐巡演才有，其余场次返回空。 */
    fun forSpot(title: String): List<Sticker> =
        if (title.contains("纯蓝幻乐")) chunlan else emptyList()
}
