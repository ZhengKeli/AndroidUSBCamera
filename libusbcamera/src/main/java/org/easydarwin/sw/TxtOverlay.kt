package org.easydarwin.sw

import android.content.Context

object TxtOverlay {
    @JvmStatic
    private var ttfPath: String? = null

    @JvmStatic
    private var ctx: Long = 0

    init {
        System.loadLibrary("TxtOverlay")
    }

    @JvmStatic
    private external fun txtOverlayInit(width: Int, height: Int, fonts: String): Long

    @JvmStatic
    private external fun txtOverlay(ctx: Long, data: ByteArray, txt: String)

    @JvmStatic
    private external fun txtOverlayRelease(ctx: Long)

    @JvmStatic
    fun install(context: Context) {
        val appContext = context.applicationContext
        val file = appContext.getFileStreamPath("SIMYOU.ttf")
        if (file.exists()) return

        val assets = appContext.assets
        assets.open("zk/SIMYOU.ttf").use { inputStream ->
            appContext.openFileOutput("SIMYOU.ttf", Context.MODE_PRIVATE).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        ttfPath = file.absolutePath
    }

    @JvmStatic
    fun init(width: Int, height: Int) {
        val ttfPath = ttfPath
        require(ttfPath != null) { "the font file must be exists, please call install before!" }
        ctx = txtOverlayInit(width, height, ttfPath)
    }

    @JvmStatic
    fun overlay(data: ByteArray, txt: String) {
        if (ctx == 0L) return
        txtOverlay(ctx, data, txt)
    }

    @JvmStatic
    fun release() {
        if (ctx == 0L) return
        txtOverlayRelease(ctx)
        ctx = 0
    }
}