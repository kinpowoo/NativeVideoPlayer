package com.jhkj.videoplayer.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*


object PermissionTool {
    fun checkPermissionForbid(activity: Activity, permission: String): Boolean {
        if (!ActivityCompat.shouldShowRequestPermissionRationale(
                activity, permission
            )
        ) {
            //如果返回false，表明不用向用户弹出提示，这个方法在检测权限被拒绝
            //后调用，如果用户没有完全禁止这个权限，会返回 true 表示还需要继续向用
            //户申请权限，如果返回 false 表示用户完全禁止了这个权限，需要到设置中开启
            jumpPermissionPage(activity)
            return false
        } else {
            return true
        }
    }

    fun checkPermissionNeverAsk(activity: Activity, permission: String): Boolean {
        return !ActivityCompat.shouldShowRequestPermissionRationale(
            activity, permission
        )
    }

    private fun jumpPermissionPage(mContext: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault())
        when (manufacturer) {
            "huawei" -> goHuaWeiManager(mContext)
            "vivo" -> goVivoManager(mContext)
            "oppo" -> goOppoManager(mContext)
            "Coolpad" -> goCoolpadManager(mContext)
            "meizu" -> goMeizuManager(mContext)
            "xiaomi" -> goXiaoMiManager(mContext)
            "samsung" -> goSamsungManager(mContext)
            "sony" -> goSonyManager(mContext)
            "lg" -> goLGManager(mContext)
            "letv" -> {
                goLetvManager(mContext)
            }

            "qiku", "360" -> {
                go360Manager(mContext)
            }

            else -> goIntentSetting(mContext)
        }
    }

    private fun goLGManager(mContext: Context) {
        try {
            val intent = Intent("android.intent.action.MAIN")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.putExtra("packageName", mContext.packageName)
            val comp = ComponentName(
                "com.android.settings",
                "com.android.settings.Settings\$AccessLockSummaryActivity"
            )
            intent.component = comp
            mContext.startActivity(intent)
        } catch (e: Exception) {
            goIntentSetting(mContext)
        }
    }


    private fun goSonyManager(mContext: Context) {
        try {
            val intent = Intent(mContext.packageName)
            val comp = ComponentName(
                "com.sonymobile.cta",
                "com.sonymobile.cta.SomcCTAMainActivity"
            )
            intent.component = comp
            mContext.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            goIntentSetting(mContext)
        }
    }

    private fun goHuaWeiManager(mContext: Context) {
        try {
            val intent = Intent(mContext.packageName)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            val comp = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.permissionmanager.ui.MainActivity"
            )
            intent.component = comp
            mContext.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            goIntentSetting(mContext)
        }
    }

    private fun getMiuiVersion(): String? {
        val propName = "ro.miui.ui.version.name"
        val line: String
        var input: BufferedReader? = null
        try {
            val p = Runtime.getRuntime().exec("getprop $propName")
            input = BufferedReader(
                InputStreamReader(p.inputStream), 1024
            )
            line = input.readLine()
            input.close()
        } catch (ex: IOException) {
            ex.printStackTrace()
            return null
        } finally {
            try {
                input?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return line
    }

    private fun goXiaoMiManager(mContext: Context) {
        val rom = getMiuiVersion()
        val intent = Intent()
        if ("V6" == rom || "V7" == rom) {
            intent.action = "miui.intent.action.APP_PERM_EDITOR"
            intent.setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
            )
            intent.putExtra("extra_pkgname", mContext.packageName)
            mContext.startActivity(intent)
        } else if ("V8" == rom || "V9" == rom) {
            intent.action = "miui.intent.action.APP_PERM_EDITOR"
            intent.setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            intent.putExtra("extra_pkgname", mContext.packageName)
            mContext.startActivity(intent)
        } else {
            goIntentSetting(mContext)
        }
    }

    private fun goMeizuManager(mContext: Context) {
        try {
            val intent = Intent("com.meizu.safe.security.SHOW_APPSEC")
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.putExtra("packageName", mContext.packageName)
            mContext.startActivity(intent)
        } catch (localActivityNotFoundException: ActivityNotFoundException) {
            localActivityNotFoundException.printStackTrace()
            goIntentSetting(mContext)
        }
    }

    private fun goSamsungManager(mContext: Context) {
        //三星4.3可以直接跳转
        goIntentSetting(mContext)
    }

    private fun goIntentSetting(mContext: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val uri: Uri = Uri.fromParts("package", mContext.packageName, null)
        intent.data = uri
        try {
            mContext.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun goOppoManager(context: Context) {
        try {
            val intent = Intent("android.settings.APPLICATION_DETAILS_SETTINGS")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.putExtra("packageName", context.packageName)
//            val comp = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.PermissionAppAllPermissionActivity")//R9SK 6.0.1  os-v3.0
            val comp = ComponentName(
                "com.coloros.securitypermission",
                "com.coloros.securitypermission.permission.PermissionAppAllPermissionActivity"
            )//R11t 7.1.1 os-v3.2
            intent.component = comp
            context.startActivity(intent)
        } catch (e: Exception) {
            goIntentSetting(context)
        }
    }

    private fun goLetvManager(context: Context) {
        try {
            val intent = Intent()
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.putExtra("packageName", context.packageName)
            val comp = ComponentName(
                "com.letv.android.letvsafe",
                "com.letv.android.letvsafe.PermissionAndApps"
            )
            intent.component = comp
            context.startActivity(intent)
        } catch (e: Exception) {
            goIntentSetting(context)
        }
    }

    //360只能打开到自带安全软件
    private fun go360Manager(context: Context) {
        try {
            val intent = Intent("android.intent.action.MAIN")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.putExtra("packageName", context.packageName)
            val comp = ComponentName(
                "com.qihoo360.mobilesafe",
                "com.qihoo360.mobilesafe.ui.index.AppEnterActivity"
            )
            intent.component = comp
            context.startActivity(intent)
        } catch (e: Exception) {
            goIntentSetting(context)
        }
    }

    /**
     * doStartApplicationWithPackageName("com.yulong.android.security:remote")
     * 和Intent open = getPackageManager().getLaunchIntentForPackage("com.yulong.android.security:remote");
     * startActivity(open);
     * 本质上没有什么区别，通过Intent open...打开比调用doStartApplicationWithPackageName方法更快，也是android本身提供的方法
     */
    private fun goCoolpadManager(mContext: Context) {
        doStartApplicationWithPackageName(mContext, "com.yulong.android.security:remote")
    }

    private fun goVivoManager(mContext: Context) {
        doStartApplicationWithPackageName(mContext, "com.bairenkeji.icaller")
    }


    private fun doStartApplicationWithPackageName(mContext: Context, packageName: String) {
        // 通过包名获取此APP详细信息，包括Activities、services、versioncode、name等等
        var packageinfo: PackageInfo? = null
        try {
            packageinfo = mContext.packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        if (packageinfo == null) {
            return
        }
        // 创建一个类别为CATEGORY_LAUNCHER的该包名的Intent
        val resolveIntent = Intent(Intent.ACTION_MAIN, null)
        resolveIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        resolveIntent.setPackage(packageinfo.packageName)
        // 通过getPackageManager()的queryIntentActivities方法遍历
        val resolveInfoList: List<*> = mContext.packageManager
            .queryIntentActivities(resolveIntent, 0)
        val resolveInfo: ResolveInfo? = resolveInfoList.iterator().next() as? ResolveInfo
        if (resolveInfo != null) {
            // packageName参数2 = 参数 packname
            val pkgName = resolveInfo.activityInfo.packageName
            // 这个就是我们要找的该APP的LAUNCHER的Activity[组织形式：packageName参数2.mainActivityname]
            val className = resolveInfo.activityInfo.name
            // LAUNCHER Intent
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            // 设置ComponentName参数1:packageName参数2:MainActivity路径
            val cn = ComponentName(pkgName, className)
            intent.component = cn
            try {
                mContext.startActivity(intent)
            } catch (e: Exception) {
                goIntentSetting(mContext)
                e.printStackTrace()
            }
        }
    }
}