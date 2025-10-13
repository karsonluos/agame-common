package com.robining.games.frame.utils

import com.robining.games.frame.startup.StartUpContext
import java.io.File
import kotlin.jvm.Throws

object FileUtil {
    fun copyAssetToFile(assetPath: String, targetFile: File): File {
        targetFile.ensureExistOrThrow()
        val ist = StartUpContext.context.assets.open(assetPath)
        ist.use {
            val ost = targetFile.outputStream()
            ost.use {
                ist.copyTo(it)
            }
        }

        return targetFile
    }
}

fun File.ensureExist(): Boolean {
    parentFile?.let {
        if (!it.exists()) {
            if (!it.mkdirs()) {
                return false
            }
        }
    }
    if (!this.exists()) {
        return this.createNewFile()
    }

    return true
}

@Throws(IllegalStateException::class)
fun File.ensureExistOrThrow() {
    if (!ensureExist()) {
        throw IllegalStateException("cannot create file: $absolutePath")
    }
}