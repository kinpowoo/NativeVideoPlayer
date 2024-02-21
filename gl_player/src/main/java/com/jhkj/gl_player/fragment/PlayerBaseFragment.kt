package com.jhkj.gl_player.fragment

import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.*

/**
 * <pre>
 *     author : Taylor Zhang
 *     time   : 2021/02/24
 *     desc   : Visibility fragment.
 *     version: 1.0.0
 * </pre>
 */

open class PlayerBaseFragment(@LayoutRes contentLayoutId: Int = 0)
    : Fragment(contentLayoutId), IFragmentVisibility{

    // True if the fragment is visible to the user.
    private var mIsFragmentVisible = false

    // True if the fragment is visible to the user for the first time.
    private var mIsFragmentVisibleFirst = true

    override fun onResume() {
        super.onResume()
        determineFragmentVisible()
    }

    override fun onPause() {
        super.onPause()
        determineFragmentInvisible()
    }

    // 判断按钮是否重复点击
    open fun isDoubleClick(v: View): Boolean {
        val tag = v.getTag(v.id)
        val beforeTimeMiles = if (tag != null) tag as Long else 0
        val timeInMillis = Calendar.getInstance().timeInMillis
        v.setTag(v.id, timeInMillis)
        return timeInMillis - beforeTimeMiles < 800
    }

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
        if (parent != null && parent is PlayerBaseFragment) {
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
            if (it is PlayerBaseFragment) {
                it.determineFragmentVisible()
            }
        }
    }

    private fun determineChildFragmentInvisible() {
        childFragmentManager.fragments.forEach {
            if (it is PlayerBaseFragment) {
                it.determineFragmentInvisible()
            }
        }
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
}