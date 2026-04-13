package com.sin_tech.ble_manager.ble_tradition.activity

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.sin_tech.ble_manager.databinding.BleDiscoveryItemLayoutBinding
import com.sin_tech.ble_manager.models.BleDevice

/**
 * @ClassName: CleanRecordAdapter
 * @Description: .
 * @Author: JJ
 * @CreateDate: 2021/10/26 13:54
 */
class BlueDeviceListAdapter(private val deviceSelect:(BleDevice)->Unit): RecyclerView.Adapter<BlueDeviceListAdapter.DeviceItemHolder>(){
    private val deviceList = mutableListOf<BleDevice>()
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
    fun addDevices(devices:List<BleDevice>){
        if(devices.isNotEmpty()){
            deviceList.addAll(devices)
        }
        notifyItemRangeInserted(0,devices.size)
    }

    fun appendDevice(device:BleDevice){
        if(!deviceList.contains(device)) {
            deviceList.add(device)
            notifyItemInserted(deviceList.size)
        }
    }

    fun updateDeviceList(devices:List<BleDevice>){
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

    fun getSelected(): BleDevice?{
        if(selectIndex < 0 || selectIndex >= deviceList.size)return null
        return deviceList[selectIndex]
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceItemHolder {
        context = parent.context
        val itemBinding = BleDiscoveryItemLayoutBinding.inflate(
            LayoutInflater.from(parent.context),parent,false)
        return DeviceItemHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: DeviceItemHolder, position: Int, payloads: MutableList<Any>) {
        super.onBindViewHolder(holder, position, payloads)

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if(pos >= 0 && pos < deviceList.size) {
                if(pos != selectIndex){
                    notifyItemChanged(selectIndex,"Unselect")
                    selectIndex = pos
                    notifyItemChanged(pos,"Select")
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

    @SuppressLint("UseCompatLoadingForDrawables", "MissingPermission")
    override fun onBindViewHolder(holder: DeviceItemHolder, position: Int) {
        if(holder.bindingAdapterPosition == -1 || position >= itemCount)return
        val pos =  holder.bindingAdapterPosition
        val device = deviceList.getOrNull(pos)
        if(device != null){
            holder.deviceName.text = device.name
            holder.deviceMac.text = device.mac
        }else{
            holder.deviceName.text = ""
            holder.deviceMac.text = ""
        }
    }

    override fun getItemCount(): Int {
        return deviceList.size
    }

    class DeviceItemHolder(itemHolder: BleDiscoveryItemLayoutBinding): ViewHolder(itemHolder.root){
        val deviceName = itemHolder.deviceName
        val deviceMac = itemHolder.deviceMac
    }

}