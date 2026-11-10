package com.vmap.app.domain.model

/**
 * 板块。固定的导航层级，不是用户自定义标签。
 *
 * 新增角色时只在这里加枚举值，并补一条 strings.xml 文案，不改数据库结构。
 */
enum class Section {
    TIANYI,
    MIKU,
    ;

    /** 板块的另一个。用于「一键切换」。 */
    val other: Section
        get() = if (this == TIANYI) MIKU else TIANYI
}

/**
 * 视图范围：单板块，或两个板块合并的「综合」。
 *
 * 综合模式只影响展示（地图、时间线、统计），不落库——
 * 打卡记录本身的归属永远是 [Section]。
 */
enum class Board(val label: String) {
    TIANYI("依你随行"),
    MIKU("因音而遇"),
    ALL("综合"),
    ;

    /** 综合模式没有归属板块，返回 null。 */
    val section: Section?
        get() = when (this) {
            TIANYI -> Section.TIANYI
            MIKU -> Section.MIKU
            ALL -> null
        }

    companion object {
        fun of(section: Section): Board = when (section) {
            Section.TIANYI -> TIANYI
            Section.MIKU -> MIKU
        }
    }
}
