package com.jhkj.videoplayer.fragments

import android.content.Intent
import android.os.Bundle
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jhkj.videoplayer.R
import com.jhkj.videoplayer.adapter.ConnListAdapter
import com.jhkj.videoplayer.components.LoadingDialog
import com.jhkj.videoplayer.components.RecyclerViewWithContextMenu.RecyclerViewContextInfo
import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import com.jhkj.videoplayer.databinding.ConnFragmentLayoutBinding
import com.jhkj.videoplayer.pages.ConnectionEditActivity
import com.jhkj.videoplayer.pages.RemoteFilesActivity
import com.jhkj.videoplayer.utils.RemoteConnTest
import com.jhkj.videoplayer.utils.Res
import com.jhkj.videoplayer.viewmodels.ConnInfoVm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ConnectionsFragment: VisibilityFragment() {
    private var binding: ConnFragmentLayoutBinding? = null
    // 方式1：使用 viewModels() 委托
//    val viewModel: ConnInfoVm by viewModels()  // Fragment 作用域
    private val vm: ConnInfoVm by activityViewModels()  // Activity 作用域
    private var connAdapter: ConnListAdapter? = null
    private var loadingDialog: LoadingDialog? = null
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

        loadingDialog = LoadingDialog(requireContext())
        binding?.addConn?.setOnClickListener {
            if(isDoubleClick(it))return@setOnClickListener

        }

        connAdapter = ConnListAdapter({ item,idx ->
            loadingDialog?.show()
            lifecycleScope.launch(Dispatchers.IO){
                val isConnective = RemoteConnTest.testConn(item)
                withContext(Dispatchers.Main){
                    loadingDialog?.dismiss()
                    if(isConnective){
                        val tent = Intent(requireContext(), RemoteFilesActivity::class.java)
                        tent.putExtra("connInfo",item)
                        startActivity(tent)
                    }else{
                        showToast(Res.string(R.string.connection_not_accessible))
                    }
                }
            }
        }){ item,idx,view ->
            val x = view.x
            val y = view.y
            binding?.connectionList?.showContextMenu(x-60,y)
        }
        binding?.connectionList?.layoutManager = LinearLayoutManager(requireContext())
        binding?.connectionList?.adapter = connAdapter

        binding?.connectionList?.let {
            // 为 RecyclerView 注册上下文菜单
            registerForContextMenu(it)
        }
    }

    // 创建上下文菜单
    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val inflater: MenuInflater? = activity?.getMenuInflater()
        inflater?.inflate(com.jhkj.videoplayer.R.menu.remote_connection_item_menu, menu) // 加载菜单资源
    }

    // 处理菜单项点击事件
    override fun onContextItemSelected(item: MenuItem): Boolean {
        // 关键：获取传递过来的位置信息
        val menuInfo = item.menuInfo
        if (menuInfo is RecyclerViewContextInfo) {
            val info = menuInfo
            val position = info.position
            val selectedData: ConnInfo? = connAdapter?.getItem(position)
            when (item.itemId) {
                com.jhkj.videoplayer.R.id.menu_edit -> {                 // 处理编辑操作
                    val tent = Intent(requireContext(), ConnectionEditActivity::class.java)
                    tent.putExtra("connInfo",selectedData)
                    startActivity(tent)
                    return true
                }
                com.jhkj.videoplayer.R.id.menu_delete -> {
                    // 处理删除操作
                    selectedData?.let {
                        vm.deleteConn(selectedData)
                    }
                    return true
                }
                else -> return super.onContextItemSelected(item)
            }
        }
        return false
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
        binding?.connectionList?.let {
            unregisterForContextMenu(it)
        }

        super.onDestroy()
    }
}