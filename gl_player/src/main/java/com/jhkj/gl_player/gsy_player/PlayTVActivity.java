package com.jhkj.gl_player.gsy_player;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.transition.Transition;
import android.view.KeyEvent;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

import com.jhkj.gl_player.HttpService.FileItem;
import com.jhkj.gl_player.databinding.ActivityPlayTvBinding;
import com.jhkj.gl_player.util.ContentUriUtil;
import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.utils.OrientationUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 单独的视频播放页面
 * Created by Bill on 2023/7/25.
 */
public class PlayTVActivity extends AppCompatActivity {

    public final static String IMG_TRANSITION = "IMG_TRANSITION";
    public final static String TRANSITION = "TRANSITION";
    OrientationUtils orientationUtils;

    private boolean isTransition;

    private ActivityPlayTvBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityPlayTvBinding.inflate(getLayoutInflater());

        View rootView = binding.getRoot();
        setContentView(rootView);


        isTransition = getIntent().getBooleanExtra(TRANSITION, false);
        init();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                //先返回正常状态
                if (orientationUtils.getScreenType() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                    binding.videoPlayerTv.getFullscreenButton().performClick();
                    return;
                }
                //释放所有
                binding.videoPlayerTv.setVideoAllCallBack(null);
                GSYVideoManager.releaseAllVideos();
                if (isTransition) {
                    finishAfterTransition();
                } else {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                            overridePendingTransition(androidx.appcompat.R.anim.abc_fade_in, androidx.appcompat.R.anim.abc_fade_out);
                        }
                    }, 500);
                }
            }
        });

        getIntentData();
    }

    private void getIntentData() {
        Intent intent = getIntent();
        var uri = intent.getData();
        /*
         * API > 16 时，有些系统的文件夹的文件分享，内容会放在 ClipData 中，而不是放在 mData 中
         */
        if (uri == null && intent.getClipData() != null) {
            ClipData.Item item = intent.getClipData().getItemAt(0);
            if (item != null) {
                uri = item.getUri();
            }
        }
        //从其他app跳入逻辑
        if (uri != null) {
            String path = ContentUriUtil.getPath(this, uri);
            if(!TextUtils.isEmpty(path)){
                String fileName = new File(path).getName();
                binding.videoPlayerTv.setUp(path, false, fileName);
                binding.videoPlayerTv.startPlayLogic();
            }
        } else {
            String filePath = intent.getStringExtra("file_url");
            String fileName = intent.getStringExtra("file_name");
            if(!TextUtils.isEmpty(filePath)){
                binding.videoPlayerTv.setUp(filePath, false, fileName);
                binding.videoPlayerTv.startPlayLogic();
            }
        }
    }


    private void init() {
        //增加title
        binding.videoPlayerTv.getTitleTextView().setVisibility(View.VISIBLE);
        //videoPlayer.setShowPauseCover(false);
        //videoPlayer.setSpeed(2f);
        //设置返回键
        binding.videoPlayerTv.getBackButton().setVisibility(View.VISIBLE);
        //设置旋转
        orientationUtils = new OrientationUtils(this, binding.videoPlayerTv);

        //设置全屏按键功能,这是使用的是选择屏幕，而不是全屏
        binding.videoPlayerTv.getFullscreenButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // ------- ！！！如果不需要旋转屏幕，可以不调用！！！-------
                // 不需要屏幕旋转，还需要设置 setNeedOrientationUtils(false)
                orientationUtils.resolveByClick();
            }
        });

        //videoPlayer.setBottomProgressBarDrawable(getResources().getDrawable(R.drawable.video_new_progress));
        //videoPlayer.setDialogVolumeProgressBar(getResources().getDrawable(R.drawable.video_new_volume_progress_bg));
        //videoPlayer.setDialogProgressBar(getResources().getDrawable(R.drawable.video_new_progress));
        //videoPlayer.setBottomShowProgressBarDrawable(getResources().getDrawable(R.drawable.video_new_seekbar_progress),
        //getResources().getDrawable(R.drawable.video_new_seekbar_thumb));
        //videoPlayer.setDialogProgressColor(getResources().getColor(R.color.colorAccent), -11);

        //是否可以滑动调整
        binding.videoPlayerTv.setIsTouchWiget(true);

        //设置返回按键功能
        binding.videoPlayerTv.getBackButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        //过渡动画
        initTransition();
    }


    @Override
    protected void onPause() {
        super.onPause();
        binding.videoPlayerTv.onVideoPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.videoPlayerTv.onVideoResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (orientationUtils != null)
            orientationUtils.releaseListener();
    }

    private void initTransition() {
        if (isTransition) {
            postponeEnterTransition();
            ViewCompat.setTransitionName(binding.videoPlayerTv, IMG_TRANSITION);
            addTransitionListener();
            startPostponedEnterTransition();
        } else {
            binding.videoPlayerTv.startPlayLogic();
        }
    }

    private void addTransitionListener() {
        Transition transition = getWindow().getSharedElementEnterTransition();
        if (transition != null) {
            transition.addListener(new OnTransitionListener() {
                @Override
                public void onTransitionEnd(Transition transition) {
                    super.onTransitionEnd(transition);
                    binding.videoPlayerTv.startPlayLogic();
                    transition.removeListener(this);
                }
            });
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        binding.videoPlayerTv.onKeyDown(keyCode,event);
        return super.onKeyDown(keyCode, event);
    }
}