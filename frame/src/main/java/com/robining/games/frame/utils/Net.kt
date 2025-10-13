package com.robining.games.frame.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import androidx.annotation.MainThread
import com.robining.games.frame.startup.StartUpContext

object Net {
    private val onceTasks = mutableSetOf<Runnable>()
    private val alwaysTasks = mutableSetOf<Runnable>()
    private val networkChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (networkIsConnected()) {
                doAfterNetworkConnected()
            }
        }
    }

    fun networkIsConnected(): Boolean {
        val manager =
            StartUpContext.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetworkInfo
        return network != null && network.isConnected
    }

    fun init() {
        //注册网络连接变化的广播
        val intentFilter = IntentFilter()
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        StartUpContext.context.registerReceiver(networkChangeReceiver, intentFilter)
    }

    @MainThread
    fun doAfterConnectedOnce(task: Runnable, immediateExecute: Boolean = true) {
        if (immediateExecute && networkIsConnected()) {
            task.run()
        } else {
            onceTasks.add(task)
        }
    }

    @MainThread
    fun doAfterConnectedAlways(task: Runnable, immediateExecute: Boolean = true) {
        if (immediateExecute && networkIsConnected()) {
            task.run()
        }

        alwaysTasks.add(task)
    }

    @MainThread
    fun removeTaskInAlways(task: Runnable) {
        alwaysTasks.remove(task)
    }

    @MainThread
    fun removeTaskInOnes(task: Runnable) {
        onceTasks.remove(task)
    }

    @Synchronized
    private fun doAfterNetworkConnected() {
        while (onceTasks.size > 0) {
            val onceTask = onceTasks.first()
            onceTasks.remove(onceTask)
            onceTask.run()
        }

        for (task in alwaysTasks) {
            task.run()
        }
    }
}