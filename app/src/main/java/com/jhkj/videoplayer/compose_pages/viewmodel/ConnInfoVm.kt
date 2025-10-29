package com.jhkj.videoplayer.compose_pages.viewmodel

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import com.jhkj.videoplayer.compose_pages.room_dto.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch


class ConnInfoVm(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val connDao = db.connInfoDao()

    val allConn: Flow<List<ConnInfo>> = connDao.getAllConnInfo()

    fun insertConn(connInfo: ConnInfo) {
        viewModelScope.launch {
            connDao.insert(connInfo)
        }
    }

    // 方法1：通过 ID 检查
    fun insertOrUpdateConn(connInfo: ConnInfo, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val t = connDao.insertOrUpdate(connInfo)
                onResult.invoke(t > 0)
            } catch (e: SQLiteConstraintException) {
                // 处理唯一性冲突等错误
                e.printStackTrace()
                onResult.invoke(false)
            }
        }
    }

    fun deleteConn(conn: ConnInfo) {
        viewModelScope.launch {
            connDao.delete(conn)
        }
    }
}