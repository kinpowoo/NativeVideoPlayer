package com.jhkj.videoplayer.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.jhkj.videoplayer.databinding.ProfileFragmentLayoutBinding
import com.jhkj.videoplayer.viewmodels.HomeFragmentVm
import com.sin_tech.ble_manager.ble_tradition.activity.SelectBlueHostOrGuest
import com.sintech.wifi_direct.activity.SelectHostOrGuest

class ProfileFragment: VisibilityFragment() {

    private var binding: ProfileFragmentLayoutBinding? = null
    private lateinit var vm: HomeFragmentVm

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

        binding?.blueBox?.setOnClickListener {
            startActivity(Intent(activity, SelectBlueHostOrGuest::class.java))
        }
        binding?.wifiBox?.setOnClickListener {
            startActivity(Intent(activity, SelectHostOrGuest::class.java))
        }
    }

}