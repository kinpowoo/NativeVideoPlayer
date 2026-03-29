package com.sintech.wifi_direct.activity

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sintech.wifi_direct.ImmersiveStatusBarUtils
import com.sintech.wifi_direct.client.WiFiDirectClient
import com.sintech.wifi_direct.databinding.WifiClientLayoutBinding
import com.sintech.wifi_direct.protocol.ClientCallback
import com.sintech.wifi_direct.protocol.FileReceiveCallback
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.file.Files


class WifiClientActivity : AppCompatActivity(),ClientCallback,FileReceiveCallback{
    private var binding: WifiClientLayoutBinding? = null
    private val sb = StringBuilder()
    private var client:WiFiDirectClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = WifiClientLayoutBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        ImmersiveStatusBarUtils.setFullScreen(this, true)

        setSupportActionBar(binding!!.toolbar)
        binding?.toolbar?.setNavigationOnClickListener {
            finish()
        }


        connectToServer()


        binding?.sendBtn?.setOnClickListener {
            val msg = binding?.inputEt?.text?.toString() ?: ""
            if(isConnected()) {
                client?.sendString(msg)
            }
            binding?.inputEt?.setText("")
        }
    }

    fun connectToServer(){
        Thread{
            client?.disconnect()
            client = null
            client = WiFiDirectClient(
                "192.168.3.24", 8888,
                WeakReference(this), WeakReference(this)
            )
            client?.connect()
        }.start()
    }

    override fun onConnected() {
        println("Connected to server")
        appendStrAndShow("Connected to server")
    }

    override fun onDisconnected(reason: String?) {
        println("Disconnected: " + reason)
        appendStrAndShow("disconnected :$reason")
    }

    override fun onMessageReceived(message: String?) {
        println("Message received: " + message)
        appendStrAndShow("msg receive:$message")
    }

    override fun onHeartbeatReceived() {
        // 心跳接收处理
    }

    override fun onHeartbeatAckReceived() {
        // 心跳确认处理
    }

    override fun onFileAckReceived(ack: String?) {
        println("File ack: " + ack)
        appendStrAndShow("ack receive:$ack")
    }
    override fun onFileReceiveStarted(fileId: String, fileName: String?, fileSize: Long) {
        println(
            "File receive started: " + fileName +
                    " (" + fileSize + " bytes)"
        )
    }

    override fun onFileChunkReceived(fileId: String, chunkIndex: Int, chunkSize: Int) {
        // 文件分片接收处理
    }

    override fun onFileReceived(fileId: String, fileName: String?, fileData: ByteArray) {
        // 保存文件
        try {
            val file = File("received_$fileName")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Files.write(file.toPath(), fileData)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onFileTransferError(error: String?) {
        System.err.println("File transfer error: " + error)
    }


    fun appendStrAndShow(str:String){
        sb.append(str).append("\n")
        runOnUiThread {
            binding?.msgArea?.text = sb.toString()
        }
    }

    fun toast(str:String){
        runOnUiThread {
            Toast.makeText(this@WifiClientActivity,str,Toast.LENGTH_SHORT).show()
        }
    }
    private fun isConnected(): Boolean{
        toast("未连接到服务器")
        return client?.isConnected ?: false
    }

    override fun onDestroy() {
        client?.disconnect("exit page!")
        client = null
        super.onDestroy()
    }
}