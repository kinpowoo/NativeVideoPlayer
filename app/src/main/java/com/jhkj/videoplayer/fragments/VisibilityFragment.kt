package com.jhkj.videoplayer.fragments

import android.content.Intent
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import cody.bus.ElegantBus
import cody.bus.LiveDataWrapper
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jhkj.gl_player.fragment.IFragmentVisibility
import com.jhkj.videoplayer.app.BaseActivity
import java.util.Calendar

open class VisibilityFragment(@LayoutRes contentLayoutId: Int = 0)
    : Fragment(contentLayoutId), IFragmentVisibility{

    // True if the fragment is visible to the user.
    private var mIsFragmentVisible = false

    // True if the fragment is visible to the user for the first time.
    private var mIsFragmentVisibleFirst = true
    private var busEvents = mutableListOf<String>()

    override fun onResume() {
        super.onResume()
        determineFragmentVisible()
    }

    override fun onPause() {
        super.onPause()
        determineFragmentInvisible()
    }

    //公共elegant event 添加
    fun addElegantEvent(eventName:String): LiveDataWrapper<Any> {
        if(!busEvents.contains(eventName)) {
            busEvents.add(eventName)
        }
        return ElegantBus.getDefault(eventName)
    }

    // 判断按钮是否重复点击
    open fun isDoubleClick(v: View): Boolean {
        val tag = v.getTag(v.id)
        val beforeTimeMiles = if (tag != null) tag as Long else 0
        val timeInMillis = Calendar.getInstance().timeInMillis
        v.setTag(v.id, timeInMillis)
        return timeInMillis - beforeTimeMiles < 800
    }

    //主题改变时可以做一些操作
    open fun onThemeChange(){}
    //地区语言改变时可以做一些操作
    open fun onLocaleChanged(){}
    //依附的activity的menu点击
    open fun onPrepareMenu(menu: Menu){}
    //依附的activity的menu点击
    open fun onMenuClick(menuId:Int){}
    //依附的activity back 点击
    open fun onFinishClick(cb:((Boolean)->Unit)?){}
    //返回按钮点击
    open fun onBackPressClick(){}
    //依附的activity 传递数据给fragment
    open fun onDataEvent(eventType:Int,data:Any?){}

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            determineFragmentInvisible()
        } else {
            determineFragmentVisible()
        }
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser) {
            determineFragmentVisible()
        } else {
            determineFragmentInvisible()
        }
    }

    override fun isVisibleToUser(): Boolean = mIsFragmentVisible

    private fun determineFragmentVisible() {
        val parent = parentFragment
        if (parent != null && parent is VisibilityFragment) {
            if (!parent.isVisibleToUser()) {
                // Parent Fragment is invisible, child fragment must be invisible.
                return
            }
        }

        if (isResumed && !isHidden && userVisibleHint && !mIsFragmentVisible) {
            mIsFragmentVisible = true
            onVisible()
            if (mIsFragmentVisibleFirst) {
                mIsFragmentVisibleFirst = false
                onVisibleFirst()
            } else {
                onVisibleExceptFirst()
            }
            determineChildFragmentVisible()
        }
    }

    private fun determineFragmentInvisible() {
        if (mIsFragmentVisible) {
            mIsFragmentVisible = false
            onInvisible()
            determineChildFragmentInvisible()
        }
    }

    private fun determineChildFragmentVisible() {
        childFragmentManager.fragments.forEach {
            if (it is VisibilityFragment) {
                it.determineFragmentVisible()
            }
        }
    }

    private fun determineChildFragmentInvisible() {
        childFragmentManager.fragments.forEach {
            if (it is VisibilityFragment) {
                it.determineFragmentInvisible()
            }
        }
    }


    protected fun showToast(str: String){
        Toast.makeText(context,str, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        if(busEvents.isNotEmpty()){
            busEvents.forEach {
                ElegantBus.getDefault(it).removeObservers(this)
            }
            busEvents.clear()
        }
        super.onDestroyView()
    }

    /**
     * 显示 fragment dialog
     */
    fun showFragmentDialog(bottomFragment : BottomSheetDialogFragment?){
        try {
            bottomFragment?.let{
                parentFragmentManager.beginTransaction().add(bottomFragment,
                        bottomFragment.javaClass.simpleName).commitNowAllowingStateLoss()
            }
        }catch (e:IllegalStateException){
            e.printStackTrace()
        }
    }

    /**
     * 移除已添加的 fragment
     */
    fun removeAddedFragment(bottomFragment : BottomSheetDialogFragment?):Boolean{
        //getSupportFragmentManager 要用在 FragmentActivity 及其子类中!!
        //getParentFragmentManager 和 getChildFragmentManager 由 Fragment 来调用
        //Fragment 调用 getParentFragmentManager 获得属于 FragmentActivity 的 FragmentManager
        // getParentFragmentManager, If this Fragment is a child of another Fragment,
        // the FragmentManager returned here will be the parent's getChildFragmentManager().
        //Fragment 调用 getChildFragmentManager 获得 子 Fragment 的 FragmentManager
        try {
            if (bottomFragment?.isAdded == true ||
                    parentFragmentManager.findFragmentByTag(
                            bottomFragment?.javaClass?.simpleName) != null) {
                //如果bottomFragment已经被添加过了
                parentFragmentManager.beginTransaction()
                        .remove(bottomFragment!!).commitNowAllowingStateLoss()
                return parentFragmentManager.executePendingTransactions()
            }
        }catch (e:IllegalStateException){
            return false
        }
        return true
    }


    fun go(target: Class<out BaseActivity?>?) {
        startActivity(Intent(requireContext(), target))
    }

}