package com.jhkj.videoplayer.compose_pages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

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
data class ConnInfo(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "display_name") var displayName:String,
    @ColumnInfo(name = "domain") var domain:String,
    @ColumnInfo(name = "path") var path:String?,
    @ColumnInfo(name = "protocol") var protocol:String,
    @ColumnInfo(name = "port") var port:Int,
    @ColumnInfo(name = "username") var username:String,
    @ColumnInfo(name = "pass") var pass:String,
    @ColumnInfo(name = "conn_type") var connType:Int
): Serializable