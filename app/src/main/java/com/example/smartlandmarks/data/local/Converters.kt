package com.example.smartlandmarks.data.local

import androidx.room.TypeConverter
import com.example.smartlandmarks.domain.model.VisitStatus

/** Room cannot persist enums directly; store the name and fail safe on unknown values. */
class Converters {

    @TypeConverter
    fun fromVisitStatus(status: VisitStatus): String = status.name

    @TypeConverter
    fun toVisitStatus(raw: String): VisitStatus =
        runCatching { VisitStatus.valueOf(raw) }.getOrDefault(VisitStatus.FAILED)
}
