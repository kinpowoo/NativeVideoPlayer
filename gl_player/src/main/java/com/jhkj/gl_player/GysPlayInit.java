package com.jhkj.gl_player;


import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.cache.CacheFactory;
import com.shuyu.gsyvideoplayer.cache.ProxyCacheManager;
import com.shuyu.gsyvideoplayer.model.VideoOptionModel;
import com.shuyu.gsyvideoplayer.player.IjkPlayerManager;
import com.shuyu.gsyvideoplayer.player.PlayerFactory;
import com.shuyu.gsyvideoplayer.utils.GSYVideoType;
import java.util.ArrayList;
import java.util.List;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

public class GysPlayInit {

    public static void initEnv(){

        //EXOPlayer kernel, supports more formats
//        PlayerFactory.setPlayManager(Exo2PlayerManager.class);
        //System kernel mode
//        PlayerFactory.setPlayManager(SystemPlayerManager.class);
        //ijk kernel, default mode
        PlayerFactory.setPlayManager(IjkPlayerManager.class);
        //aliplay kernel, default mode
//        PlayerFactory.setPlayManager(AliPlayerManager.class);

        //exo cache mode, supports m3u8, only supports exo
//        CacheFactory.setCacheManager(ExoPlayerCacheManager.class);
        //Proxy cache mode, supports all modes, does not support m3u8, etc., default
        CacheFactory.setCacheManager(ProxyCacheManager.class);



        //Switch rendering mode
        GSYVideoType.setShowType(GSYVideoType.SCREEN_TYPE_DEFAULT);
        //Default display ratio
//        GSYVideoType.SCREEN_TYPE_DEFAULT = 0;
        //16:9
//        GSYVideoType.SCREEN_TYPE_16_9 = 1;
        //4:3
//        GSYVideoType.SCREEN_TYPE_4_3 = 2;
        //Full screen cropping display, for normal display CoverImageView it is recommended to use FrameLayout as the parent layout
//        GSYVideoType.SCREEN_TYPE_FULL = 4;
        //Full screen stretching display, when using this attribute, it is recommended to use FrameLayout for surface_container
//        GSYVideoType.SCREEN_MATCH_FULL = -4;


        //Switch drawing mode
//        GSYVideoType.setRenderType(GSYVideoType.SURFACE);
        GSYVideoType.setRenderType(GSYVideoType.GLSURFACE);
//        GSYVideoType.setRenderType(GSYVideoType.TEXTURE);

//        //ijk close log
        IjkPlayerManager.setLogLevel(IjkMediaPlayer.IJK_LOG_SILENT);


        List<VideoOptionModel> list = new ArrayList<>();
        list.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                "enable-accurate-seek", 1));  // 拖动进度条后视频会弹回原位置问题
        list.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                "mediacodec", 0));  // 开启硬件加速
        list.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                "opensles", 0));
        list.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                "mediacodec-hevc", 0));  // 开启硬件加速
        list.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                "opensles-hevc", 0));  //
        list.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                "framedrop", 5));  // 动态丢帧策略
        list.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                "reconnect", 1));  // 网络波动时自动重连
        list.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                "start-on-prepared", 1)); //分片数据预缓存
        list.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                "skip_loop_filter", 48)); //启用解码前丢帧策略
        list.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                "packet-buffering",  0));// 禁用网络缓冲
        list.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                "max-buffer-size",  1024*256));// 256KB缓冲阈值
        list.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                "probesize",  10240)); // 默认值 较大，调整为16KB

//        list.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER,
//                "packet-buffering", 1));
        list.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_FORMAT,
                "fflags", "fastseek"));
        list.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_FORMAT,
                "fflags", "nobuffer"));
        // 让内核自动处理旋转
        list.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                "mediacodec-auto-rotate", 1));
        list.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                "mediacodec-handle-resolution-change", 1));
        GSYVideoManager.instance().setOptionModelList(list);


    }
}
