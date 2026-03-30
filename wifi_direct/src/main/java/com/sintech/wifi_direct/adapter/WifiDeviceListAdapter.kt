package com.sintech.wifi_direct.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.sintech.wifi_direct.databinding.DiscoveryItemLayoutBinding

/**
 * @ClassName: CleanRecordAdapter
 * @Description: .
 * @Author: JJ
 * @CreateDate: 2021/10/26 13:54
 */
class WifiDeviceListAdapter(private val deviceSelect:(WifiP2pDevice)->Unit): RecyclerView.Adapter<WifiDeviceListAdapter.DeviceItemHolder>(){
    private val deviceList = mutableListOf<WifiP2pDevice>()
    private lateinit var context: Context
    private var selectIndex = -1

    fun cleanDevices(){
        val oldLen = deviceList.size
        deviceList.clear()
        selectIndex = -1
        if(oldLen > 0){
            notifyItemRangeRemoved(0,oldLen+1)
        }
    }
    /**
     * 添加记录
     */
    fun addDevices(devices:List<WifiP2pDevice>){
        if(devices.isNotEmpty()){
            deviceList.addAll(devices)
        }
        notifyItemRangeInserted(0,devices.size)
    }

    fun updateDeviceList(devices:List<WifiP2pDevice>){
        val oldLen = deviceList.size
        deviceList.clear()
        if(oldLen > 0){
            notifyItemRangeRemoved(0,oldLen)
        }
        if(devices.isNotEmpty()){
            deviceList.addAll(devices)
            notifyItemRangeInserted(0,devices.size)
        }
    }

    fun getSelected(): WifiP2pDevice?{
        if(selectIndex < 0 || selectIndex >= deviceList.size)return null
        return deviceList[selectIndex]
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceItemHolder {
        context = parent.context
        val itemBinding = DiscoveryItemLayoutBinding.inflate(
            LayoutInflater.from(parent.context),parent,false)
        return DeviceItemHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: DeviceItemHolder, position: Int, payloads: MutableList<Any>) {
        super.onBindViewHolder(holder, position, payloads)

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if(pos >= 0 && pos < deviceList.size) {
                if(pos != selectIndex){
                    notifyItemChanged(selectIndex,"Cat_Unselect")
                    selectIndex = pos
                    notifyItemChanged(pos,"Cat_Select")
                    deviceSelect.invoke(deviceList[pos])
                }
            }
        }

        if(payloads.isNotEmpty()){
            val action = payloads.first() as? String

        }else{
            onBindViewHolder(holder,position)
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onBindViewHolder(holder: DeviceItemHolder, position: Int) {
        if(holder.bindingAdapterPosition == -1 || position >= itemCount)return
        val pos =  holder.bindingAdapterPosition
        val device = deviceList.getOrNull(pos)
        if(device != null){
            holder.deviceName.text = device.deviceName
            holder.deviceMac.text = device.deviceAddress
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                holder.deviceIp.text = device.ipAddress?.toString() ?: ""
                holder.deviceIp.visibility = View.VISIBLE
            }else{
                holder.deviceIp.text = ""
                holder.deviceIp.visibility = View.GONE
            }
        }else{
            holder.deviceName.text = ""
            holder.deviceMac.text = ""
            holder.deviceIp.text = ""
            holder.deviceIp.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int {
        return deviceList.size
    }

    class DeviceItemHolder(itemHolder: DiscoveryItemLayoutBinding): ViewHolder(itemHolder.root){
        val deviceName = itemHolder.deviceName
        val deviceMac = itemHolder.deviceMac
        val deviceIp = itemHolder.deviceIp
    }

}