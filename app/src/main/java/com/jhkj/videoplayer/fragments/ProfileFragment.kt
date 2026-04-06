package com.jhkj.videoplayer.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.jhkj.videoplayer.adapter.ThirdServiceDto
import com.jhkj.videoplayer.adapter.ThirdServiceListAdapter
import com.jhkj.videoplayer.databinding.ProfileFragmentLayoutBinding
import com.jhkj.videoplayer.third_file_framework.SelectFileServerType
import com.jhkj.videoplayer.viewmodels.HomeFragmentVm
import com.sin_tech.ble_manager.ble_tradition.activity.SelectBlueHostOrGuest
import com.sintech.wifi_direct.activity.SelectHostOrGuest

class ProfileFragment: VisibilityFragment() {

    private var binding: ProfileFragmentLayoutBinding? = null
    private lateinit var vm: HomeFragmentVm
    private var adapter: ThirdServiceListAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = ProfileFragmentLayoutBinding.inflate(inflater, container, false)
        vm = ViewModelProvider(requireActivity())[this.javaClass.name, HomeFragmentVm::class.java]
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ThirdServiceListAdapter{ item,idx ->
            if(idx == 0){
                startActivity(Intent(activity, SelectBlueHostOrGuest::class.java))
            }else if(idx == 1){
                startActivity(Intent(activity, SelectHostOrGuest::class.java))
            }else if(idx == 2){
                startActivity(Intent(activity, SelectFileServerType::class.java))
            }
        }
        binding?.servicesList?.layoutManager = GridLayoutManager(requireActivity(),
            4)
        binding?.servicesList?.adapter = adapter
        val datas = mutableListOf(
            ThirdServiceDto(com.jhkj.videoplayer.R.drawable.baseline_bluetooth_searching_24,
                getString(com.jhkj.videoplayer.R.string.ble)),
            ThirdServiceDto(com.jhkj.videoplayer.R.drawable.wifi_hotspot,
                getString(com.jhkj.videoplayer.R.string.wifi_direct)),
            ThirdServiceDto(com.jhkj.videoplayer.R.drawable.file_server,
                getString(com.jhkj.videoplayer.R.string.file_server))
        )
        adapter?.addFiles(datas)
    }

}