package com.vmap.app.data.db

import androidx.room.TypeConverter
import com.vmap.app.domain.model.Section

class Converters {

    @TypeConverter
    fun fromSection(value: Section): String = value.name

    @TypeConverter
    fun toSection(value: String): Section = Section.valueOf(value)
}
