package com.jhkj.videoplayer.fragments

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.jhkj.videoplayer.adapter.ConnListAdapter
import com.jhkj.videoplayer.databinding.ConnFragmentLayoutBinding
import com.jhkj.videoplayer.pages.ConnectionEditActivity
import com.jhkj.videoplayer.pages.SelectConnTypeActivity
import com.jhkj.videoplayer.viewmodels.ConnInfoVm


class ConnectionsFragment: VisibilityFragment() {
    private var binding: ConnFragmentLayoutBinding? = null
    // 方式1：使用 viewModels() 委托
//    val viewModel: ConnInfoVm by viewModels()  // Fragment 作用域
    private val vm: ConnInfoVm by activityViewModels()  // Activity 作用域
    private var connAdapter: ConnListAdapter? = null

    // 方式2：传统方式
//    val viewModel = ViewModelProvider(this)[ConnInfoVm::class.java]
//    val activityViewModel = ViewModelProvider(requireActivity())[ConnInfoVm::class.java]

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = ConnFragmentLayoutBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding?.addConn?.setOnClickListener {
            if(isDoubleClick(it))return@setOnClickListener
            val tent = Intent(requireContext(), SelectConnTypeActivity::class.java)
//            val options = ActivityOptions.makeSceneTransitionAnimation(requireActivity())
            startActivity(tent)
//            activity?.overridePendingTransition(0,0)
        }

        connAdapter = ConnListAdapter({ item,idx ->
            val tent = Intent(requireContext(), ConnectionEditActivity::class.java)
            tent.putExtra("connInfo",item)
            startActivity(tent)
        }){ item,idx ->

        }
        binding?.connectionList?.layoutManager = LinearLayoutManager(requireContext())
        binding?.connectionList?.adapter = connAdapter
    }

    override fun onVisibleFirst() {
        super.onVisibleFirst()

        vm.getAllConn {
            connAdapter?.addConn(it)
        }
    }

    override fun onVisibleExceptFirst() {
        super.onVisibleExceptFirst()
        vm.getAllConn {
            connAdapter?.addConn(it)
        }
    }


    override fun onDestroy() {
        vm.disconnect()
        super.onDestroy()
    }
}