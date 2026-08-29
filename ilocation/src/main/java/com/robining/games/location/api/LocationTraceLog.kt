package com.robining.games.location.api

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.util.concurrent.Executors

object LocationTraceLog {
    private const val TAG = "LocationTrace"
    private const val DIRECTORY = "diagnostics"
    private const val FILE_NAME = "location-trace.log"
    private const val MAX_BYTES = 512 * 1024L
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "location-trace-writer").apply { isDaemon = true }
    }

    fun info(context: Context, message: String) = write(context, Log.INFO, message)
    fun warn(context: Context, message: String) = write(context, Log.WARN, message)
    fun error(context: Context, message: String) = write(context, Log.ERROR, message)
    fun path(context: Context): File = File(logDirectory(context), FILE_NAME)

    private fun write(context: Context, priority: Int, message: String) {
        Log.println(priority, TAG, message)
        val appContext = context.applicationContext
        writer.execute {
            runCatching {
                val file = path(appContext)
                file.parentFile?.mkdirs()
                if (file.length() >= MAX_BYTES) {
                    val previous = File(file.parentFile, "$FILE_NAME.1")
                    if (previous.exists()) previous.delete()
                    file.renameTo(previous)
                }
                FileWriter(file, true).use { output ->
                    output.append(System.currentTimeMillis().toString())
                    output.append(" ").append(if (priority == Log.ERROR) "E" else if (priority == Log.WARN) "W" else "I")
                    output.append(" pid=").append(Process.myPid().toString())
                    output.append(" ").append(message).append('\n')
                }
            }.onFailure { Log.w(TAG, "Unable to append location trace", it) }
        }
    }

    private fun logDirectory(context: Context): File =
        context.getExternalFilesDir(DIRECTORY) ?: File(context.filesDir, DIRECTORY)
}

