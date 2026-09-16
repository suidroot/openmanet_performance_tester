package net.openmanet.perfapp.data

import androidx.room.TypeConverter
import net.openmanet.perfapp.data.entities.GpsSource

class Converters {
    @TypeConverter
    fun fromGpsSource(value: GpsSource): String = value.name

    @TypeConverter
    fun toGpsSource(value: String): GpsSource = GpsSource.valueOf(value)
}
