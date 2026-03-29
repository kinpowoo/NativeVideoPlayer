package com.jhkj.videoplayer.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModelProvider
import com.jhkj.videoplayer.databinding.ProfileFragmentLayoutBinding
import com.jhkj.videoplayer.viewmodels.HomeFragmentVm
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

        binding?.wifiBox?.setOnClickListener {
            startActivity(Intent(activity, SelectHostOrGuest::class.java))
        }
    }

}