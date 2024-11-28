package com.kieronquinn.app.findmylog

import android.app.Activity
import android.app.AndroidAppHelper
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.time.Instant

class Xposed: IXposedHookLoadPackage {

    companion object {
        private const val PACKAGE_ADM = "com.google.android.apps.adm"
        private const val ID_REFRESH = "common_device_panel_btn_locate_device"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if(lpparam.packageName != PACKAGE_ADM) return
        var refreshId: Int? = null
        XposedHelpers.findAndHookMethod(
            View::class.java,
            "findViewById",
            Integer.TYPE,
            object: XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    super.afterHookedMethod(param)
                    val view = param.thisObject as View
                    if(refreshId == null) {
                        refreshId = view.context.resources.getIdentifier(
                            ID_REFRESH,
                            "id",
                            PACKAGE_ADM
                        )
                    }
                    if(param.args[0] == refreshId) {
                        val refreshButton = param.result as Button
                        refreshButton.setupRefreshHooks()
                    }
                }
            }
        )
        XposedHelpers.findAndHookConstructor(
            "com.google.android.gms.maps.model.LatLng",
            lpparam.classLoader,
            Double::class.java,
            Double::class.java,
            object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    super.beforeHookedMethod(param)
                    //Location updates always come from a Consumer, no other instantiations do
                    val calling = getCallingInformation(lpparam.classLoader) ?: return
                    if(calling.interfaces.firstOrNull()?.name == "java.util.function.Consumer") {
                        val latitude = param.args[0] as Double
                        val longitude = param.args[1] as Double
                        handleLatLng(latitude, longitude)
                    }
                }
            }
        )
    }

    private var currentLatLng: String? = null
    private var shouldLog = false

    @Synchronized
    private fun handleLatLng(latitude: Double, longitude: Double) {
        if(!shouldLog) return //Reject if not ready
        if(latitude == 0.0 || longitude == 0.0) return //Reject uninitialized
        val newLatLng = "$latitude,$longitude"
        if(currentLatLng == newLatLng) return
        currentLatLng = newLatLng
        val context = AndroidAppHelper.currentApplication()
        val timestamp = Instant.now()
        val line = "${timestamp},$latitude,$longitude"
        Log.d("FML", "Writing to log: $line")
        context.openFileOutput("locations.csv", Context.MODE_PRIVATE or Context.MODE_APPEND).use {
            it.write("${line}\n".toByteArray())
            it.flush()
        }
    }

    private fun Button.setupRefreshHooks() {
        val blockerView = View(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }
        val activity = getActivity()!!
        activity.run {
            val windowInsetsController =
                WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            (window.decorView as ViewGroup).addView(blockerView)
            startLogLoop(this)
        }
    }

    private fun View.getActivity(): Activity? {
        var context: Context? = context
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }

    private fun Button.startLogLoop(activity: Activity) {
        var logLoop: Thread? = null
        Log.d("FML", "startLogLoop")
        logLoop = Thread {
            while(true) {
                try {
                    Thread.sleep(60_000L)
                    shouldLog = true
                    Log.d("FML", "Refreshing")
                    activity.runOnUiThread {
                        performClick()
                    }
                }catch (e: Exception) {
                    Log.e("FML", "Received exception, stopping thread")
                    logLoop?.interrupt()
                }
            }
        }
        logLoop.start()
    }

    private fun getCallingInformation(classLoader: ClassLoader): Class<*>? {
        val classes = Thread.currentThread().stackTrace.map { it.className }
        val lspIndex = classes.indexOfFirst { it == "LSPHooker_" }
        if(lspIndex == -1 || lspIndex == classes.size) return null
        val result = classes[lspIndex + 1]
        return XposedHelpers.findClass(result, classLoader)
    }

}