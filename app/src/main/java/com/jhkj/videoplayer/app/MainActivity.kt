package com.jhkj.videoplayer.app

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.navigation.NavigationBarView
import com.jhkj.gl_player.fragment.IFragmentVisibility
import com.jhkj.videoplayer.R
import com.jhkj.videoplayer.databinding.MainLayoutBinding
import com.jhkj.videoplayer.fragments.ConnectionsFragment
import com.jhkj.videoplayer.fragments.HomeFragment
import com.jhkj.videoplayer.fragments.ProfileFragment
import com.jhkj.videoplayer.utils.ImmersiveStatusBarUtils


class MainActivity : BaseActivity() {
    private var binding: MainLayoutBinding? = null
    private val fragmentList: MutableList<Fragment> = mutableListOf()
    private var homeFragment: HomeFragment? = null
    private var connFragment: ConnectionsFragment? = null
    private var profileFragment: ProfileFragment? = null
    private val savedCurrentID = "currentId"
    private var currentId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainLayoutBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
//        ImmersiveStatusBarUtils.setImmersiveStatusBar(this)
        ImmersiveStatusBarUtils.setFullScreen(this, true)

        //初始化fragments
        initFragments(savedInstanceState)

        supportActionBar?.hide()

        binding?.bottomNavigationView?.setOnItemSelectedListener(mOnNavigationItemSelectedListener)
        binding?.bottomNavigationView?.setOnItemReselectedListener(
            mOnNavigationItemReselectedListener
        )
    }


    private val mOnNavigationItemSelectedListener
            : NavigationBarView.OnItemSelectedListener =
        object : NavigationBarView.OnItemSelectedListener {
            override fun onNavigationItemSelected(item: MenuItem): Boolean {
                val itemId = item.itemId
                if (itemId == R.id.navigation_home) {
                    switchFragment(0)
                    return true
                }
                if (itemId == R.id.navigation_conn) {
                    switchFragment(1)
                    return true
                }
                if (itemId == R.id.navigation_profile) {
                    switchFragment(2)
                    return true
                }
                return false
            }
        }

    private val mOnNavigationItemReselectedListener
            : NavigationBarView.OnItemReselectedListener =
        object : NavigationBarView.OnItemReselectedListener {
            override fun onNavigationItemReselected(item: MenuItem) {
                if (item.itemId == R.id.navigation_home) {

                }else if (item.itemId == R.id.navigation_conn) {

                }else if (item.itemId == R.id.navigation_profile) {

                }
            }
        }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        switchFragment(0)
    }

    private fun switchFragment(index: Int) {
        if (currentId == index) return
//        if (index == 0) {
//            binding?.bottomNavigationView?.selectedItemId = R.id.navigation_home
//        }
//        if (index == 1) {
//            binding?.bottomNavigationView?.selectedItemId = R.id.navigation_conn
//        }
//        if (index == 2) {
//            binding?.bottomNavigationView?.selectedItemId = R.id.navigation_profile
//        }
        when (index) {
            0 -> {
                if (homeFragment == null) {
                    homeFragment = HomeFragment()
                }
                addFragment(homeFragment!!, HomeFragment::class.java.name)
                showFragment(homeFragment!!)
            }

            1 -> {
                if (connFragment == null) {
                    connFragment = ConnectionsFragment()
                }
                addFragment(connFragment!!, ConnectionsFragment::class.java.name)
                showFragment(connFragment!!)
            }

            2 -> {
                if (profileFragment == null) {
                    profileFragment = ProfileFragment()
                }
                addFragment(profileFragment!!, ProfileFragment::class.java.name)
                showFragment(profileFragment!!)
            }
        }
        currentId = index
    }

    /*添加fragment*/
    private fun addFragment(fragment: Fragment, tag: String) {
        /*判断该fragment是否已经被添加过  如果没有被添加  则添加*/
        if (!fragment.isAdded && null == supportFragmentManager.findFragmentByTag(tag)) {
            val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
            //commit方法是异步的，调用后不会立即执行，而是放到UI任务队列中
            transaction.add(R.id.content_container, fragment, tag).commit()
            //让commit动作立即执行
            supportFragmentManager.executePendingTransactions()
            /*添加到 fragmentList*/
            addToList(fragment)
        }
    }

    private fun addToList(fragment: Fragment?) {
        if (fragment != null && !fragmentList.contains(fragment)) {
            fragmentList.add(fragment)
        }
    }

    /*显示fragment*/
    private fun showFragment(fragment: Fragment) {
        for (frag in fragmentList) {
            if (frag !== fragment) {
                /*先隐藏其他fragment*/
                supportFragmentManager.beginTransaction().hide(frag).commit()
            }
        }
        if (fragment is IFragmentVisibility) {
            if (fragment.isVisibleToUser()) return
        }
        supportFragmentManager.beginTransaction().show(fragment).commit()
    }

    private fun initFragments(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            /*获取保存的fragment  没有的话返回null*/
            homeFragment = supportFragmentManager.getFragment(
                savedInstanceState,
                HomeFragment::class.java.name
            ) as HomeFragment?
            connFragment = supportFragmentManager.getFragment(
                savedInstanceState,
                ConnectionsFragment::class.java.name
            ) as ConnectionsFragment?
            profileFragment = supportFragmentManager.getFragment(
                savedInstanceState,
                ProfileFragment::class.java.name
            ) as ProfileFragment?
            val cachedId = savedInstanceState.getInt(savedCurrentID, 0)
            if (cachedId in 0..3) {
                currentId = cachedId
            }
            addToList(homeFragment)
            addToList(connFragment)
            addToList(profileFragment)
            switchFragment(currentId)
        } else {
            switchFragment(0)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        //通过onSaveInstanceState方法保存当前显示的fragment
        if (homeFragment != null) {
            supportFragmentManager.putFragment(
                outState,
                HomeFragment::class.java.name, homeFragment!!
            )
        }
        if (connFragment != null) {
            supportFragmentManager.putFragment(
                outState,
                ConnectionsFragment::class.java.name, connFragment!!
            )
        }
        if (profileFragment != null) {
            supportFragmentManager.putFragment(
                outState,
                ProfileFragment::class.java.name, profileFragment!!
            )
        }
        outState.putInt(savedCurrentID, currentId)
        super.onSaveInstanceState(outState)
    }
}