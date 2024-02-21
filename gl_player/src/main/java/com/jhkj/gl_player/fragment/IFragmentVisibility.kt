package com.jhkj.gl_player.fragment

interface IFragmentVisibility {

    /**
     * Fragment可见时调用。
     */
    fun onVisible() {}

    /**
     * Fragment不可见时调用。
     */
    fun onInvisible() {}

    /**
     * Fragment第一次可见时调用。
     */
    fun onVisibleFirst() {}

    /**
     * Fragment可见时（第一次除外）调用。
     */
    fun onVisibleExceptFirst() {}

    /**
     * Fragment当前是否对用户可见
     */
    fun isVisibleToUser(): Boolean
}