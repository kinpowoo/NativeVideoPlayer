package com.jhkj.gl_player.util;

import android.annotation.SuppressLint;
import android.view.View;
import android.widget.CheckedTextView;

import com.jhkj.gl_player.R;

/**
 * @ClassName: WorkingDialog
 * @Description: .
 * @Author: JJ
 * @CreateDate: 17/8/2021 下午 5:16
 */
public class SpeedDialog extends NPopupWindow implements CheckedTextView.OnClickListener{
    private CheckedTextView speed05;
    private CheckedTextView speed075;
    private CheckedTextView speed100;
    private CheckedTextView speed125;
    private CheckedTextView speed150;
    private CheckedTextView speed200;

    private SpeedCallback speedCallback;

    public void setSpeedCallback(SpeedCallback callback){
        this.speedCallback = callback;
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    public SpeedDialog(View contentView, int width, int height){
        super(contentView,width,height);

        speed05 = contentView.findViewById(R.id.speed_05);
        speed075 = contentView.findViewById(R.id.speed_075);
        speed100 = contentView.findViewById(R.id.speed_100);
        speed125 = contentView.findViewById(R.id.speed_125);
        speed150 = contentView.findViewById(R.id.speed_150);
        speed200 = contentView.findViewById(R.id.speed_200);

        speed05.setOnClickListener(this);
        speed075.setOnClickListener(this);
        speed100.setOnClickListener(this);
        speed125.setOnClickListener(this);
        speed150.setOnClickListener(this);
        speed200.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        String tag = (String)view.getTag();
        float speed = Float.parseFloat(tag);
        if(speedCallback != null) {
            speedCallback.onSpeed(speed);
        }
        setSpeed(speed);
        dismiss();
    }

    public void setSpeed(float targetSpeed){
        speed05.setChecked(0.5f == targetSpeed);
        speed075.setChecked(0.75f == targetSpeed);
        speed100.setChecked(1.0f == targetSpeed);
        speed125.setChecked(1.25f == targetSpeed);
        speed150.setChecked(1.5f == targetSpeed);
        speed200.setChecked(2.0f == targetSpeed);
    }
}