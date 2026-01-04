package com.jhkj.videoplayer.viewmodels


import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import com.jhkj.videoplayer.compose_pages.room_dto.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ConnInfoVm(application: Application) : AndroidViewModel(application){
    private val db = AppDatabase.getInstance(application)
    private val connDao = db.connInfoDao()

    fun getAllConn(callback:(List<ConnInfo>)->Unit) {
        viewModelScope.launch {
            val connections = withContext(Dispatchers.IO){
                connDao.getAllConnInfo()
            }
            callback(connections)
        }
    }

    fun insertConn(connInfo: ConnInfo,callback:(Long)->Unit) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                connDao.insert(connInfo)
            }
            callback(res)
        }
    }


    // 方法1：通过 ID 检查
    fun insertOrUpdateConn(connInfo: ConnInfo, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val t = withContext(Dispatchers.IO) {
                    connDao.insertOrUpdate(connInfo)
                }
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
             withContext(Dispatchers.IO) {
                connDao.delete(conn)
            }
        }
    }

    fun deleteConnById(id: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                connDao.deleteById(id)
            }
        }
    }

    suspend fun getConnBy(id: Int,callback:(ConnInfo?)->Unit){
        val conn = withContext(Dispatchers.IO) {
           connDao.getConnInfoById(id)
        }
        callback.invoke(conn)
    }

    fun disconnect(){
        try {
            db.close()
        }catch (e: Exception){
            e.printStackTrace()
        }
    }
}