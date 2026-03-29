package com.sintech.wifi_direct.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sintech.wifi_direct.ImmersiveStatusBarUtils
import com.sintech.wifi_direct.databinding.SelectHostOrGuestLayoutBinding

class SelectHostOrGuest : AppCompatActivity() {
    private var binding: SelectHostOrGuestLayoutBinding? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SelectHostOrGuestLayoutBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        ImmersiveStatusBarUtils.setFullScreen(this, true)

        setSupportActionBar(binding!!.toolbar)
        binding?.toolbar?.setNavigationOnClickListener {
            finish()
        }

        binding?.clientBox?.setOnClickListener {
            startActivity(Intent(this@SelectHostOrGuest, WifiClientActivity::class.java))
        }

        binding?.serverBox?.setOnClickListener {
            startActivity(Intent(this@SelectHostOrGuest, WifiServerActivity::class.java))
        }
    }

}