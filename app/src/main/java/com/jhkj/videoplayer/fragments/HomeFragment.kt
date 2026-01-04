package com.jhkj.videoplayer.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.jhkj.videoplayer.databinding.HomeFragmentLayoutBinding
import com.jhkj.videoplayer.viewmodels.HomeFragmentVm

class HomeFragment: VisibilityFragment() {
    private var binding: HomeFragmentLayoutBinding? = null
    private lateinit var vm: HomeFragmentVm

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = HomeFragmentLayoutBinding.inflate(inflater, container, false)
        vm = ViewModelProvider(requireActivity())[this.javaClass.name, HomeFragmentVm::class.java]
        return binding!!.root
    }


}