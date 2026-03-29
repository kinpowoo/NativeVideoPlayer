package com.sintech.wifi_direct.service;

import android.annotation.SuppressLint;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

/**
 * JobScheduler服务
 * 用于在Android 5.0+上调度后台任务
 */
public class WiFiDirectJobService extends JobService {

    private static final String TAG = "WiFiDirectJobService";
    private static final int JOB_ID = 1001;

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "Job started: " + params.getJobId());

        // 启动前台服务
        WiFiDirectForegroundService.startService(this);

        // 返回true表示工作还在进行
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "Job stopped: " + params.getJobId());

        // 返回true表示需要重新调度
        return true;
    }

    /**
     * 调度Job
     */
    @SuppressLint("MissingPermission")
    public static void scheduleJob(Context context) {
        ComponentName serviceComponent = new ComponentName(context, WiFiDirectJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, serviceComponent);

        // 设置触发条件
        builder.setMinimumLatency(1000); // 至少1秒后执行
        builder.setOverrideDeadline(5000); // 最多5秒后执行
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        builder.setPersisted(true); // 设备重启后保持
        builder.setRequiresCharging(false);
        builder.setRequiresDeviceIdle(false);

        // 设置重试策略
        builder.setBackoffCriteria(30000, JobInfo.BACKOFF_POLICY_LINEAR);

        // 调度Job
        JobScheduler jobScheduler = (JobScheduler)
            context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        if (jobScheduler != null) {
            int result = jobScheduler.schedule(builder.build());
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.d(TAG, "Job scheduled successfully");
            } else {
                Log.e(TAG, "Failed to schedule job");
            }
        }
    }

    /**
     * 取消Job
     */
    public static void cancelJob(Context context) {
        JobScheduler jobScheduler = (JobScheduler)
                context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        if (jobScheduler != null) {
            jobScheduler.cancel(JOB_ID);
            Log.d(TAG, "Job cancelled");
        }
    }
}