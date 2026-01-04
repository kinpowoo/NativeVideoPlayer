package com.jhkj.videoplayer.pages

import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import com.jhkj.videoplayer.R
import com.jhkj.videoplayer.adapter.ConnTypeAdapter
import com.jhkj.videoplayer.app.BaseActivity
import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import com.jhkj.videoplayer.databinding.SelectConnTypeLayoutBinding
import com.jhkj.videoplayer.utils.ImmersiveStatusBarUtils
import com.jhkj.videoplayer.utils.Res

class ConnectionEditActivity : BaseActivity() {
    private var binding: SelectConnTypeLayoutBinding? = null
    private var connDto: ConnInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SelectConnTypeLayoutBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        ImmersiveStatusBarUtils.setFullScreen(this,true)
        supportActionBar?.hide()

    }
}