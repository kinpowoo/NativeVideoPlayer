package com.jhkj.videoplayer.compose_pages.room_dto

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnInfoDao {
    @Insert
    suspend fun insert(conn: ConnInfo):Long  // 返回插入的 rowId

    @Update
    suspend fun update(conn: ConnInfo):Int

    @Delete
    suspend fun delete(conn: ConnInfo)

    @Query("Delete FROM ConnInfo WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM ConnInfo")
    suspend fun getAllConnInfo(): List<ConnInfo>  // 返回 Flow 以支持 Compose 响应式更新

    @Query("SELECT * FROM ConnInfo WHERE id = :id")
    suspend fun getConnInfoById(id: Int): ConnInfo?

    // 检查条目是否存在
    @Query("SELECT COUNT(*) FROM ConnInfo WHERE id = :id")
    suspend fun isConnExists(id: Int): Int  // 返回 0 或 1

    // 插入或更新（需在事务中执行）
    @Transaction
    suspend fun insertOrUpdate(conn: ConnInfo):Long{
        if (isConnExists(conn.id) > 0) {
           return update(conn).toLong()  // 已存在则更新
        } else {
            return insert(conn)  // 不存在则插入
        }
    }

}