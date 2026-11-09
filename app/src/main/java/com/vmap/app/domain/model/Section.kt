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
