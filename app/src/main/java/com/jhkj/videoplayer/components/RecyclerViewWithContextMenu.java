package com.jhkj.videoplayer.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

public class RecyclerViewWithContextMenu extends RecyclerView {
    private final RecyclerViewContextInfo mContextInfo = new RecyclerViewContextInfo();

    public RecyclerViewWithContextMenu(Context context) { super(context); }
    // ... 其他构造方法 ...
    public RecyclerViewWithContextMenu(Context context, AttributeSet attrs){
        super(context,attrs);
    }
    public RecyclerViewWithContextMenu(Context context, AttributeSet attrs,int defStyleAttr){
        super(context,attrs,defStyleAttr);
    }

    @Override
    public boolean showContextMenuForChild(View originalView, float x, float y) {
        LayoutManager layoutManager = getLayoutManager();
        if (layoutManager != null) {
            int position = layoutManager.getPosition(originalView);
            if (position >= 0) {
                mContextInfo.mPosition = position; // 保存位置信息
                return super.showContextMenuForChild(originalView, x, y);
            }
        }
        return false;
    }

    @Override
    protected ContextMenu.ContextMenuInfo getContextMenuInfo() {
        return mContextInfo; // 返回包含位置信息的对象
    }

    // 自定义的 ContextMenuInfo 类，用于承载位置信息
    public static class RecyclerViewContextInfo implements ContextMenu.ContextMenuInfo {
        private int mPosition = -1;
        public int getPosition() { return mPosition; }
    }
}