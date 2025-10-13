package com.robining.games.frame.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import com.robining.games.frame.R
import com.robining.games.frame.startup.StartUpContext

object AppUtil {
    fun jumpToMarket(context: Context, packageName: String = context.packageName, marketPackageName : String? = "com.android.vending") {
        //存在手机里没安装应用市场的情况，跳转会包异常，做一个接收判断
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("market://details?id=${packageName}")
        marketPackageName?.let { intent.setPackage(marketPackageName) }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) {
            //可以接收
            context.startActivity(intent)
        } else {
            //没有应用市场，尝试通过浏览器跳转到Google Play
            viewH5("https://play.google.com/store/apps/details?id=${packageName}")
        }
    }

    fun getGpMarketUrl(context: Context, packageName: String = context.packageName): String {
        return "https://play.google.com/store/apps/details?id=${packageName}"
    }

    fun startAppOrInstall(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            jumpToMarket(context, packageName)
            return
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun isInstall(context: Context, packageName: String): Boolean {
        return context.packageManager.getLaunchIntentForPackage(packageName) != null
    }

    fun viewH5(url: String) {
        val uri: Uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        val context = StartUpContext.context
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            val chooserIntent = Intent.createChooser(intent,"Open With")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)
        }catch (ex : Exception){
            Toast.makeText(
                context,
                context.getString(R.string.browser_not_install),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun getStatusBarHeight(context: Context = StartUpContext.context): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }
}