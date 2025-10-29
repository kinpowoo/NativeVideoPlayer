package com.jhkj.videoplayer.compose_pages.models

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

object ConnConfig {

    /**
     * 参数-name
     */
    const val PARAMS_NAME = "ConnDto"

    /**
     * 参数-age
     */
    const val PARAMS_FLAG = "isEdit"
}


@Entity(tableName = "ConnInfo")
@Parcelize
data class ConnInfo(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "display_name") val displayName:String,
    @ColumnInfo(name = "domain") val domain:String,
    @ColumnInfo(name = "path") val path:String?,
    @ColumnInfo(name = "protocol") val protocol:String,
    @ColumnInfo(name = "port") val port:Int,
    @ColumnInfo(name = "username") val username:String,
    @ColumnInfo(name = "pass") val pass:String,
    @ColumnInfo(name = "conn_type") val connType:Int
):Parcelable