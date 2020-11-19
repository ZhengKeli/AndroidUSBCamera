package com.serenegiant.usb

import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.text.TextUtils
import android.view.Surface
import android.view.SurfaceHolder
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import kotlin.math.abs


class UVCCamera(usbDevice: UsbDevice, usbDeviceConnection: UsbDeviceConnection) {

    private val mNativePtr: Long

    private var mSupportedSize: String

    init {
        mNativePtr = nativeCreate().takeIf { it != 0L }
                ?: throw UnsupportedOperationException("Camera open failed!")

        val splitName = usbDevice.deviceName.split('/')
        val busNum = splitName[splitName.size - 2].toInt()
        val devNum = splitName[splitName.size - 1].toInt()
        val usbfs = splitName.subList(1, splitName.size - 2).joinToString("/", "/")

        val rc = nativeConnect(mNativePtr, usbDevice.vendorId, usbDevice.productId,
                usbDeviceConnection.fileDescriptor, busNum, devNum, usbfs)
        if (rc != 0) throw UnsupportedOperationException("Camera open failed with code $rc!")

        mSupportedSize = nativeGetSupportedSize(mNativePtr)
        nativeSetPreviewSize(mNativePtr, DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT,
                DEFAULT_PREVIEW_MIN_FPS, DEFAULT_PREVIEW_MAX_FPS, DEFAULT_PREVIEW_FORMAT, DEFAULT_BANDWIDTH)
    }

    @Synchronized
    fun close() {
        stopPreview()
        nativeRelease(mNativePtr)
        nativeDestroy(mNativePtr)

        mProcSupports = 0
        mControlSupports = mProcSupports
        mCurrentFrameFormat = -1
        mCurrentBandwidthFactor = 0f
    }

    private var mControlSupports: Long = 0 // 相机控件支持的功能标志
    private var mProcSupports: Long = 0 // 处理单元支持的功能标志

    private var mCurrentFrameFormat = FRAME_FORMAT_MJPEG
    private var mCurrentWidth = DEFAULT_PREVIEW_WIDTH
    private var mCurrentHeight = DEFAULT_PREVIEW_HEIGHT
    private var mCurrentBandwidthFactor = DEFAULT_BANDWIDTH
    private var mCurrentSizeList: List<Size>? = null


    fun setStatusCallback(callback: IStatusCallback?) {
        nativeSetStatusCallback(mNativePtr, callback)
    }

    fun setButtonCallback(callback: IButtonCallback?) {
        nativeSetButtonCallback(mNativePtr, callback)
    }

    fun setFrameCallback(callback: IFrameCallback?, pixelFormat: Int) {
        nativeSetFrameCallback(mNativePtr, callback, pixelFormat)
    }


    @Synchronized
    fun startPreview() {
        nativeStartPreview(mNativePtr)
    }

    @Synchronized
    fun stopPreview() {
        setFrameCallback(null, 0)
        nativeStopPreview(mNativePtr)
    }

    @Synchronized
    fun startCapture(surface: Surface) {
        nativeSetCaptureDisplay(mNativePtr, surface)
    }

    @Synchronized
    fun stopCapture() {
        nativeSetCaptureDisplay(mNativePtr, null)
    }


    fun setPreviewSize(
            width: Int, height: Int,
            min_fps: Int = DEFAULT_PREVIEW_MIN_FPS,
            max_fps: Int = DEFAULT_PREVIEW_MAX_FPS,
            frameFormat: Int = mCurrentFrameFormat,
            bandwidthFactor: Float = mCurrentBandwidthFactor) {

        require(!(width == 0 || height == 0)) { "invalid preview size" }
        val rc = nativeSetPreviewSize(mNativePtr, width, height, min_fps, max_fps, frameFormat, bandwidthFactor)
        require(rc == 0) { "Failed to set preview size" }

        mCurrentFrameFormat = frameFormat
        mCurrentWidth = width
        mCurrentHeight = height
        mCurrentBandwidthFactor = bandwidthFactor
    }

    val supportedSizeList: List<Size>
        get() {
            val type = if (mCurrentFrameFormat > 0) 6 else 4
            return getSupportedSize(type, mSupportedSize)
        }
    val supportedSize: String?
        @Synchronized
        get() = if (!TextUtils.isEmpty(mSupportedSize)) mSupportedSize else nativeGetSupportedSize(mNativePtr).also { mSupportedSize = it }
    val previewSize: Size?
        get() {
            var result: Size? = null
            val list = supportedSizeList
            for (sz in list) {
                if (sz.width == mCurrentWidth
                        || sz.height == mCurrentHeight) {
                    result = sz
                    break
                }
            }
            return result
        }

    /**
     * set preview surface with SurfaceHolder
     * you can use SurfaceHolder came from SurfaceView/GLSurfaceView
     *
     * @param holder
     */
    @Synchronized
    fun setPreviewDisplay(holder: SurfaceHolder) {
        setPreviewDisplay(holder.surface)
    }

    /**
     * set preview surface with SurfaceTexture.
     * this method require API >= 14
     *
     * @param texture
     */
    @Synchronized
    fun setPreviewTexture(texture: SurfaceTexture?) {
        val surface = Surface(texture)
        setPreviewDisplay(surface)
    }

    /**
     * set preview surface with Surface
     *
     * @param surface
     */
    @Synchronized
    fun setPreviewDisplay(surface: Surface) {
        nativeSetPreviewDisplay(mNativePtr, surface)
    }


    /**
     * destroy UVCCamera object
     */
    @Synchronized
    fun destroy() {
        close()
    }

    /**
     * wrong result may return when you call this just after camera open.
     * It is better to wait several hundreds milliseconds.
     */
    fun checkSupportFlag(flag: Long): Boolean {
        updateCameraParams()
        return if (flag and -0x80000000 == -0x80000000L) mProcSupports and flag == flag and 0x7ffffffF
        else mControlSupports and flag == flag
    }


    @get:Synchronized
    @set:Synchronized
    var autoFocus: Boolean
        get() {
            var result = true
            result = nativeGetAutoFocus(mNativePtr) > 0
            return result
        }
        set(autoFocus) {
            nativeSetAutoFocus(mNativePtr, autoFocus)
        }


    @get:Synchronized
    @set:Synchronized
    var focus: Int
        get() {
            nativeUpdateFocusLimit(mNativePtr)

            val range = abs(mFocusMax - mFocusMin).toFloat()
            if (range <= 0) return 0

            val absFocus = nativeGetFocus(mNativePtr)
            return ((absFocus - mFocusMin) * 100f / range).toInt()
        }
        set(focus) {
            val range = abs(mFocusMax - mFocusMin).toFloat()
            if (range > 0) nativeSetFocus(mNativePtr, (focus / 100f * range).toInt() + mFocusMin)
        }

    @Synchronized
    fun resetFocus() {
        nativeSetFocus(mNativePtr, mFocusDef)
    }


    @get:Synchronized
    @set:Synchronized
    var autoWhiteBalance: Boolean
        get() {
            var result = true
            result = nativeGetAutoWhiteBlance(mNativePtr) > 0
            return result
        }
        set(autoWhiteBalance) {
            nativeSetAutoWhiteBlance(mNativePtr, autoWhiteBalance)
        }

    @get:Synchronized
    @set:Synchronized
    var whiteBalance: Int
        get() {
            var result = 0
            nativeUpdateWhiteBlanceLimit(mNativePtr)
            val range = Math.abs(mWhiteBlanceMax - mWhiteBlanceMin).toFloat()
            if (range > 0) {
                result = ((nativeGetWhiteBlance(mNativePtr) - mWhiteBlanceMin) * 100f / range).toInt()
            }
            return result
        }
        set(whiteBalance) {
            val range = Math.abs(mWhiteBlanceMax - mWhiteBlanceMin).toFloat()
            if (range > 0) nativeSetWhiteBlance(mNativePtr, (whiteBalance / 100f * range).toInt() + mWhiteBlanceMin)
        }

    @Synchronized
    fun resetWhiteBalance() {
        nativeSetWhiteBlance(mNativePtr, mWhiteBlanceDef)
    }
    //================================================================================
    /**
     * @return brightness[%]
     */
    /**
     * @param brightness [%]
     */
    @get:Synchronized
    @set:Synchronized
    var brightness: Int
        get() {
            var result = 0
            nativeUpdateBrightnessLimit(mNativePtr)
            val range = abs(mBrightnessMax - mBrightnessMin).toFloat()
            if (range <= 0) return result
            result = ((nativeGetBrightness(mNativePtr) - mBrightnessMin) * 100f / range).toInt()
            return result
        }
        set(brightness) {
            val range = Math.abs(mBrightnessMax - mBrightnessMin).toFloat()
            if (range > 0) nativeSetBrightness(mNativePtr, (brightness / 100f * range).toInt() + mBrightnessMin)
        }

    @Synchronized
    fun resetBrightness() {
        nativeSetBrightness(mNativePtr, mBrightnessDef)
    }
    //================================================================================
    /**
     * @return contrast[%]
     */
    /**
     * @param contrast [%]
     */
    @get:Synchronized
    @set:Synchronized
    var contrast: Int
        get() {
            var result = 0
            val range = Math.abs(mContrastMax - mContrastMin).toFloat()
            if (range > 0) {
                result = ((nativeGetContrast(mNativePtr) - mContrastMin) * 100f / range).toInt()
            }
            return result
        }
        set(contrast) {
            nativeUpdateContrastLimit(mNativePtr)
            val range = Math.abs(mContrastMax - mContrastMin).toFloat()
            if (range > 0) nativeSetContrast(mNativePtr, (contrast / 100f * range).toInt() + mContrastMin)
        }

    @Synchronized
    fun resetContrast() {
        nativeSetContrast(mNativePtr, mContrastDef)
    }


    @get:Synchronized
    @set:Synchronized
    var sharpness: Int
        get() {
            var result = 0
            nativeUpdateSharpnessLimit(mNativePtr)
            val range = Math.abs(mSharpnessMax - mSharpnessMin).toFloat()
            if (range > 0) {
                result = ((nativeGetSharpness(mNativePtr) - mSharpnessMin) * 100f / range).toInt()
            }
            return result
        }
        set(sharpness) {
            val range = abs(mSharpnessMax - mSharpnessMin).toFloat()
            if (range > 0) nativeSetSharpness(mNativePtr, (sharpness / 100f * range).toInt() + mSharpnessMin)
        }

    @Synchronized
    fun resetSharpness() {
        nativeSetSharpness(mNativePtr, mSharpnessDef)
    }


    @get:Synchronized
    @set:Synchronized
    var gain: Int
        get() = getGain(nativeGetGain(mNativePtr))
        set(gain) {
            val range = abs(mGainMax - mGainMin).toFloat()
            if (range > 0) nativeSetGain(mNativePtr, (gain / 100f * range).toInt() + mGainMin)
        }

    @Synchronized
    fun getGain(gain_abs: Int): Int {
        var result = 0
        nativeUpdateGainLimit(mNativePtr)
        val range = abs(mGainMax - mGainMin).toFloat()
        if (range > 0) {
            result = ((gain_abs - mGainMin) * 100f / range).toInt()
        }
        return result
    }

    @Synchronized
    fun resetGain() {
        nativeSetGain(mNativePtr, mGainDef)
    }


    @get:Synchronized
    @set:Synchronized
    var gamma: Int
        get() {
            var result = 0
            nativeUpdateGammaLimit(mNativePtr)
            val range = Math.abs(mGammaMax - mGammaMin).toFloat()
            if (range > 0) {
                result = ((nativeGetGamma(mNativePtr) - mGammaMin) * 100f / range).toInt()
            }
            return result
        }
        set(gamma) {
            val range = Math.abs(mGammaMax - mGammaMin).toFloat()
            if (range > 0) nativeSetGamma(mNativePtr, (gamma / 100f * range).toInt() + mGammaMin)
        }

    @Synchronized
    fun resetGamma() {
        nativeSetGamma(mNativePtr, mGammaDef)
    }


    @get:Synchronized
    @set:Synchronized
    var saturation: Int
        get() {
            var result = 0
            nativeUpdateSaturationLimit(mNativePtr)
            val range = abs(mSaturationMax - mSaturationMin).toFloat()
            if (range > 0) {
                result = ((nativeGetSaturation(mNativePtr) - mSaturationMin) * 100f / range).toInt()
            }
            return result
        }
        set(saturation) {
            val range = Math.abs(mSaturationMax - mSaturationMin).toFloat()
            if (range > 0) nativeSetSaturation(mNativePtr, (saturation / 100f * range).toInt() + mSaturationMin)
        }

    @Synchronized
    fun resetSaturation() {
        nativeSetSaturation(mNativePtr, mSaturationDef)
    }

    @get:Synchronized
    @set:Synchronized
    var hue: Int
        get() {
            var result = 0
            nativeUpdateHueLimit(mNativePtr)
            val range = abs(mHueMax - mHueMin).toFloat()
            if (range <= 0) return result
            result = ((nativeGetHue(mNativePtr) - mHueMin) * 100f / range).toInt()
            return result
        }
        set(hue) {
            val range = abs(mHueMax - mHueMin).toFloat()
            if (range > 0) nativeSetHue(mNativePtr, (hue / 100f * range).toInt() + mHueMin)
        }

    @Synchronized
    fun resetHue() {
        nativeSetHue(mNativePtr, mSaturationDef)
    }

    //================================================================================
    var powerlineFrequency: Int
        get() = nativeGetPowerlineFrequency(mNativePtr)
        set(frequency) {
            nativeSetPowerlineFrequency(mNativePtr, frequency)
        }
    //================================================================================
    /**
     * @return zoom[%]
     */// 			   Log.d(TAG, "setZoom:zoom=" + zoom + " ,value=" + z);
    /**
     * this may not work well with some combination of camera and device
     *
     * @param zoom [%]
     */
    @get:Synchronized
    @set:Synchronized
    var zoom: Int
        get() {
            var result = 0
            nativeUpdateZoomLimit(mNativePtr)
            val range = abs(mZoomMax - mZoomMin).toFloat()
            if (range > 0) {
                result = ((nativeGetZoom(mNativePtr) - mZoomMin) * 100f / range).toInt()
            }
            return result
        }
        set(zoom) {
            val range = abs(mZoomMax - mZoomMin).toFloat()
            if (range > 0) {
                val z = (zoom / 100f * range).toInt() + mZoomMin
                // 			   Log.d(TAG, "setZoom:zoom=" + zoom + " ,value=" + z);
                nativeSetZoom(mNativePtr, z)
            }
        }

    @Synchronized
    fun resetZoom() {
        nativeSetZoom(mNativePtr, mZoomDef)
    }

    //================================================================================
    @Synchronized
    fun updateCameraParams() {
        if (mControlSupports == 0L || mProcSupports == 0L) {
            // サポートしている機能フラグを取得
            if (mControlSupports == 0L) mControlSupports = nativeGetCtrlSupports(mNativePtr)
            if (mProcSupports == 0L) mProcSupports = nativeGetProcSupports(mNativePtr)
            // 設定値を取得
            if (mControlSupports != 0L && mProcSupports != 0L) {
                nativeUpdateBrightnessLimit(mNativePtr)
                nativeUpdateContrastLimit(mNativePtr)
                nativeUpdateSharpnessLimit(mNativePtr)
                nativeUpdateGainLimit(mNativePtr)
                nativeUpdateGammaLimit(mNativePtr)
                nativeUpdateSaturationLimit(mNativePtr)
                nativeUpdateHueLimit(mNativePtr)
                nativeUpdateZoomLimit(mNativePtr)
                nativeUpdateWhiteBlanceLimit(mNativePtr)
                nativeUpdateFocusLimit(mNativePtr)
            }
        }
    }


    companion object {
        const val DEFAULT_PREVIEW_WIDTH = 640
        const val DEFAULT_PREVIEW_HEIGHT = 480
        const val DEFAULT_PREVIEW_MIN_FPS = 1
        const val DEFAULT_PREVIEW_MAX_FPS = 31
        const val DEFAULT_BANDWIDTH = 1.0f

        const val FRAME_FORMAT_YUYV = 0
        const val FRAME_FORMAT_MJPEG = 1
        const val DEFAULT_PREVIEW_FORMAT = FRAME_FORMAT_YUYV

        const val PIXEL_FORMAT_RAW = 0
        const val PIXEL_FORMAT_YUV = 1
        const val PIXEL_FORMAT_RGB565 = 2
        const val PIXEL_FORMAT_RGBX = 3
        const val PIXEL_FORMAT_YUV420SP = 4 // NV12
        const val PIXEL_FORMAT_NV21 = 5 // = YVU420SemiPlanar,NV21，但是保存到jpg颜色失真

        //--------------------------------------------------------------------------------
        const val CTRL_SCANNING = 0x00000001 // D0:  Scanning Mode
        const val CTRL_AE = 0x00000002 // D1:  Auto-Exposure Mode
        const val CTRL_AE_PRIORITY = 0x00000004 // D2:  Auto-Exposure Priority
        const val CTRL_AE_ABS = 0x00000008 // D3:  Exposure Time (Absolute)
        const val CTRL_AR_REL = 0x00000010 // D4:  Exposure Time (Relative)
        const val CTRL_FOCUS_ABS = 0x00000020 // D5:  Focus (Absolute)
        const val CTRL_FOCUS_REL = 0x00000040 // D6:  Focus (Relative)
        const val CTRL_IRIS_ABS = 0x00000080 // D7:  Iris (Absolute)
        const val CTRL_IRIS_REL = 0x00000100 // D8:  Iris (Relative)
        const val CTRL_ZOOM_ABS = 0x00000200 // D9:  Zoom (Absolute)
        const val CTRL_ZOOM_REL = 0x00000400 // D10: Zoom (Relative)
        const val CTRL_PANTILT_ABS = 0x00000800 // D11: PanTilt (Absolute)
        const val CTRL_PANTILT_REL = 0x00001000 // D12: PanTilt (Relative)
        const val CTRL_ROLL_ABS = 0x00002000 // D13: Roll (Absolute)
        const val CTRL_ROLL_REL = 0x00004000 // D14: Roll (Relative)
        const val CTRL_FOCUS_AUTO = 0x00020000 // D17: Focus, Auto
        const val CTRL_PRIVACY = 0x00040000 // D18: Privacy
        const val CTRL_FOCUS_SIMPLE = 0x00080000 // D19: Focus, Simple
        const val CTRL_WINDOW = 0x00100000 // D20: Window
        const val PU_BRIGHTNESS = -0x7fffffff // D0: Brightness
        const val PU_CONTRAST = -0x7ffffffe // D1: Contrast
        const val PU_HUE = -0x7ffffffc // D2: Hue
        const val PU_SATURATION = -0x7ffffff8 // D3: Saturation
        const val PU_SHARPNESS = -0x7ffffff0 // D4: Sharpness
        const val PU_GAMMA = -0x7fffffe0 // D5: Gamma
        const val PU_WB_TEMP = -0x7fffffc0 // D6: White Balance Temperature
        const val PU_WB_COMPO = -0x7fffff80 // D7: White Balance Component
        const val PU_BACKLIGHT = -0x7fffff00 // D8: Backlight Compensation
        const val PU_GAIN = -0x7ffffe00 // D9: Gain
        const val PU_POWER_LF = -0x7ffffc00 // D10: Power Line Frequency
        const val PU_HUE_AUTO = -0x7ffff800 // D11: Hue, Auto
        const val PU_WB_TEMP_AUTO = -0x7ffff000 // D12: White Balance Temperature, Auto
        const val PU_WB_COMPO_AUTO = -0x7fffe000 // D13: White Balance Component, Auto
        const val PU_DIGITAL_MULT = -0x7fffc000 // D14: Digital Multiplier
        const val PU_DIGITAL_LIMIT = -0x7fff8000 // D15: Digital Multiplier Limit
        const val PU_AVIDEO_STD = -0x7fff0000 // D16: Analog Video Standard
        const val PU_AVIDEO_LOCK = -0x7ffe0000 // D17: Analog Video Lock Status
        const val PU_CONTRAST_AUTO = -0x7ffc0000 // D18: Contrast, Auto

        // uvc_status_class from libuvc.h
        const val STATUS_CLASS_CONTROL = 0x10
        const val STATUS_CLASS_CONTROL_CAMERA = 0x11
        const val STATUS_CLASS_CONTROL_PROCESSING = 0x12

        // uvc_status_attribute from libuvc.h
        const val STATUS_ATTRIBUTE_VALUE_CHANGE = 0x00
        const val STATUS_ATTRIBUTE_INFO_CHANGE = 0x01
        const val STATUS_ATTRIBUTE_FAILURE_CHANGE = 0x02
        const val STATUS_ATTRIBUTE_UNKNOWN = 0xff
        fun getSupportedSize(type: Int, supportedSize: String?): List<Size> {
            val result: MutableList<Size> = ArrayList()
            if (!TextUtils.isEmpty(supportedSize)) try {
                val json = JSONObject(supportedSize)
                val formats = json.getJSONArray("formats")
                val format_nums = formats.length()
                for (i in 0 until format_nums) {
                    val format = formats.getJSONObject(i)
                    if (format.has("type") && format.has("size")) {
                        val format_type = format.getInt("type")
                        if (format_type == type || type == -1) {
                            addSize(format, format_type, 0, result)
                        }
                    }
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return result
        }

        @Throws(JSONException::class)
        private fun addSize(format: JSONObject, formatType: Int, frameType: Int, size_list: MutableList<Size>) {
            val size = format.getJSONArray("size")
            val size_nums = size.length()
            for (j in 0 until size_nums) {
                val sz = size.getString(j).split("x".toRegex()).toTypedArray()
                try {
                    size_list.add(Size(formatType, frameType, j, sz[0].toInt(), sz[1].toInt()))
                } catch (e: Exception) {
                    break
                }
            }
        }

        @JvmStatic
        private external fun nativeRelease(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetStatusCallback(mNativePtr: Long, callback: IStatusCallback?): Int

        @JvmStatic
        private external fun nativeSetButtonCallback(mNativePtr: Long, callback: IButtonCallback?): Int

        @JvmStatic
        private external fun nativeSetFrameCallback(mNativePtr: Long, callback: IFrameCallback?, pixelFormat: Int): Int

        @JvmStatic
        private external fun nativeSetPreviewSize(id_camera: Long, width: Int, height: Int, min_fps: Int, max_fps: Int, mode: Int, bandwidth: Float): Int

        @JvmStatic
        private external fun nativeGetSupportedSize(id_camera: Long): String

        @JvmStatic
        private external fun nativeStartPreview(id_camera: Long): Int

        @JvmStatic
        private external fun nativeStopPreview(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetPreviewDisplay(id_camera: Long, surface: Surface): Int

        @JvmStatic
        private external fun nativeSetCaptureDisplay(id_camera: Long, surface: Surface?): Int

        @JvmStatic
        private external fun nativeGetCtrlSupports(id_camera: Long): Long

        @JvmStatic
        private external fun nativeGetProcSupports(id_camera: Long): Long

        @JvmStatic
        private external fun nativeSetScanningMode(id_camera: Long, scanning_mode: Int): Int

        @JvmStatic
        private external fun nativeGetScanningMode(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetExposureMode(id_camera: Long, exposureMode: Int): Int

        @JvmStatic
        private external fun nativeGetExposureMode(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetExposurePriority(id_camera: Long, priority: Int): Int

        @JvmStatic
        private external fun nativeGetExposurePriority(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetExposure(id_camera: Long, exposure: Int): Int

        @JvmStatic
        private external fun nativeGetExposure(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetExposureRel(id_camera: Long, exposure_rel: Int): Int

        @JvmStatic
        private external fun nativeGetExposureRel(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetAutoFocus(id_camera: Long, autofocus: Boolean): Int

        @JvmStatic
        private external fun nativeGetAutoFocus(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetFocus(id_camera: Long, focus: Int): Int

        @JvmStatic
        private external fun nativeGetFocus(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetFocusRel(id_camera: Long, focus_rel: Int): Int

        @JvmStatic
        private external fun nativeGetFocusRel(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetIris(id_camera: Long, iris: Int): Int

        @JvmStatic
        private external fun nativeGetIris(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetIrisRel(id_camera: Long, iris_rel: Int): Int

        @JvmStatic
        private external fun nativeGetIrisRel(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetPan(id_camera: Long, pan: Int): Int

        @JvmStatic
        private external fun nativeGetPan(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetPanRel(id_camera: Long, pan_rel: Int): Int

        @JvmStatic
        private external fun nativeGetPanRel(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetTilt(id_camera: Long, tilt: Int): Int

        @JvmStatic
        private external fun nativeGetTilt(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetTiltRel(id_camera: Long, tilt_rel: Int): Int

        @JvmStatic
        private external fun nativeGetTiltRel(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetRoll(id_camera: Long, roll: Int): Int

        @JvmStatic
        private external fun nativeGetRoll(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetRollRel(id_camera: Long, roll_rel: Int): Int

        @JvmStatic
        private external fun nativeGetRollRel(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetAutoWhiteBlance(id_camera: Long, autoWhiteBalance: Boolean): Int

        @JvmStatic
        private external fun nativeGetAutoWhiteBlance(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetAutoWhiteBlanceCompo(id_camera: Long, autoWhiteBalanceCompo: Boolean): Int

        @JvmStatic
        private external fun nativeGetAutoWhiteBlanceCompo(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetWhiteBlance(id_camera: Long, whiteBalance: Int): Int

        @JvmStatic
        private external fun nativeGetWhiteBlance(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetWhiteBlanceCompo(id_camera: Long, whiteBalance_compo: Int): Int

        @JvmStatic
        private external fun nativeGetWhiteBlanceCompo(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetBacklightComp(id_camera: Long, backlight_comp: Int): Int

        @JvmStatic
        private external fun nativeGetBacklightComp(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetBrightness(id_camera: Long, brightness: Int): Int

        @JvmStatic
        private external fun nativeGetBrightness(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetContrast(id_camera: Long, contrast: Int): Int

        @JvmStatic
        private external fun nativeGetContrast(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetAutoContrast(id_camera: Long, autocontrast: Boolean): Int

        @JvmStatic
        private external fun nativeGetAutoContrast(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetSharpness(id_camera: Long, sharpness: Int): Int

        @JvmStatic
        private external fun nativeGetSharpness(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetGain(id_camera: Long, gain: Int): Int

        @JvmStatic
        private external fun nativeGetGain(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetGamma(id_camera: Long, gamma: Int): Int

        @JvmStatic
        private external fun nativeGetGamma(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetSaturation(id_camera: Long, saturation: Int): Int

        @JvmStatic
        private external fun nativeGetSaturation(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetHue(id_camera: Long, hue: Int): Int

        @JvmStatic
        private external fun nativeGetHue(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetAutoHue(id_camera: Long, autohue: Boolean): Int

        @JvmStatic
        private external fun nativeGetAutoHue(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetPowerlineFrequency(id_camera: Long, frequency: Int): Int

        @JvmStatic
        private external fun nativeGetPowerlineFrequency(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetZoom(id_camera: Long, zoom: Int): Int

        @JvmStatic
        private external fun nativeGetZoom(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetZoomRel(id_camera: Long, zoom_rel: Int): Int

        @JvmStatic
        private external fun nativeGetZoomRel(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetDigitalMultiplier(id_camera: Long, multiplier: Int): Int

        @JvmStatic
        private external fun nativeGetDigitalMultiplier(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetDigitalMultiplierLimit(id_camera: Long, multiplier_limit: Int): Int

        @JvmStatic
        private external fun nativeGetDigitalMultiplierLimit(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetAnalogVideoStandard(id_camera: Long, standard: Int): Int

        @JvmStatic
        private external fun nativeGetAnalogVideoStandard(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetAnalogVideoLoackState(id_camera: Long, state: Int): Int

        @JvmStatic
        private external fun nativeGetAnalogVideoLoackState(id_camera: Long): Int

        @JvmStatic
        private external fun nativeSetPrivacy(id_camera: Long, privacy: Boolean): Int

        @JvmStatic
        private external fun nativeGetPrivacy(id_camera: Long): Int

        init {
            System.loadLibrary("jpeg-turbo1500")
            System.loadLibrary("usb100")
            System.loadLibrary("uvc")
            System.loadLibrary("UVCCamera")
        }
    }

    // native code
    private external fun nativeCreate(): Long
    private external fun nativeDestroy(id_camera: Long)
    private external fun nativeConnect(id_camera: Long, venderId: Int, productId: Int, fileDescriptor: Int, busNum: Int, devAddr: Int, usbfs: String?): Int

    private external fun nativeUpdateScanningModeLimit(id_camera: Long): Int
    private var mScanningModeMin = 0
    private var mScanningModeMax = 0
    private var mScanningModeDef = 0

    private external fun nativeUpdateExposureModeLimit(id_camera: Long): Int

    private external fun nativeUpdateExposureLimit(id_camera: Long): Int
    private var mExposureModeMin = 0
    private var mExposureModeMax = 0
    private var mExposureModeDef = 0

    private external fun nativeUpdateExposurePriorityLimit(id_camera: Long): Int
    private var mExposurePriorityMin = 0
    private var mExposurePriorityMax = 0
    private var mExposurePriorityDef = 0

    private external fun nativeUpdateExposureRelLimit(id_camera: Long): Int
    private var mExposureMin = 0
    private var mExposureMax = 0
    private var mExposureDef = 0

    private external fun nativeUpdateAutoFocusLimit(id_camera: Long): Int
    private var mAutoFocusMin = 0
    private var mAutoFocusMax = 0
    private var mAutoFocusDef = 0

    private external fun nativeUpdateFocusLimit(id_camera: Long): Int
    private var mFocusMin = 0
    private var mFocusMax = 0
    private var mFocusDef = 0

    private external fun nativeUpdateFocusRelLimit(id_camera: Long): Int
    private var mFocusRelMin = 0
    private var mFocusRelMax = 0
    private var mFocusRelDef = 0

    private var mFocusSimpleMin = 0
    private var mFocusSimpleMax = 0
    private var mFocusSimpleDef = 0

    private external fun nativeUpdateIrisLimit(id_camera: Long): Int
    private var mIrisMin = 0
    private var mIrisMax = 0
    private var mIrisDef = 0

    private external fun nativeUpdateIrisRelLimit(id_camera: Long): Int
    private var mIrisRelMin = 0
    private var mIrisRelMax = 0
    private var mIrisRelDef = 0

    private external fun nativeUpdatePanLimit(id_camera: Long): Int
    private var mPanMin = 0
    private var mPanMax = 0
    private var mPanDef = 0

    private external fun nativeUpdatePanRelLimit(id_camera: Long): Int
    private var mPanRelMin = 0
    private var mPanRelMax = 0
    private var mPanRelDef = 0

    private external fun nativeUpdateTiltLimit(id_camera: Long): Int
    private var mTiltMin = 0
    private var mTiltMax = 0
    private var mTiltDef = 0

    private external fun nativeUpdateTiltRelLimit(id_camera: Long): Int
    private var mTiltRelMin = 0
    private var mTiltRelMax = 0
    private var mTiltRelDef = 0

    private external fun nativeUpdateRollRelLimit(id_camera: Long): Int
    private var mRollMin = 0
    private var mRollMax = 0
    private var mRollDef = 0

    private external fun nativeUpdateRollLimit(id_camera: Long): Int
    private var mRollRelMin = 0
    private var mRollRelMax = 0
    private var mRollRelDef = 0

    private external fun nativeUpdateAutoWhiteBlanceLimit(id_camera: Long): Int
    private var mAutoWhiteBalanceMin = 0
    private var mAutoWhiteBalanceMax = 0
    private var mAutoWhiteBalanceDef = 0

    private external fun nativeUpdateAutoWhiteBlanceCompoLimit(id_camera: Long): Int
    private var mAutoWhiteBalanceCompoMin = 0
    private var mAutoWhiteBalanceCompoMax = 0
    private var mAutoWhiteBalanceCompoDef = 0

    private external fun nativeUpdateWhiteBlanceLimit(id_camera: Long): Int
    private var mWhiteBlanceMin = 0
    private var mWhiteBlanceMax = 0
    private var mWhiteBlanceDef = 0

    private external fun nativeUpdateWhiteBlanceCompoLimit(id_camera: Long): Int
    private var mWhiteBalanceCompoMin = 0
    private var mWhiteBalanceCompoMax = 0
    private var mWhiteBalanceCompoDef = 0

    private var mWhiteBalanceRelMin = 0
    private var mWhiteBalanceRelMax = 0
    private var mWhiteBalanceRelDef = 0

    private external fun nativeUpdateBacklightCompLimit(id_camera: Long): Int
    private var mBacklightCompMin = 0
    private var mBacklightCompMax = 0
    private var mBacklightCompDef = 0

    private external fun nativeUpdateBrightnessLimit(id_camera: Long): Int
    private var mBrightnessMin = 0
    private var mBrightnessMax = 0
    private var mBrightnessDef = 0

    private external fun nativeUpdateContrastLimit(id_camera: Long): Int
    private external fun nativeUpdateAutoContrastLimit(id_camera: Long): Int
    private var mContrastMin = 0
    private var mContrastMax = 0
    private var mContrastDef = 0

    private external fun nativeUpdateSharpnessLimit(id_camera: Long): Int
    private var mSharpnessMin = 0
    private var mSharpnessMax = 0
    private var mSharpnessDef = 0

    private external fun nativeUpdateGainLimit(id_camera: Long): Int
    private var mGainMin = 0
    private var mGainMax = 0
    private var mGainDef = 0

    private external fun nativeUpdateGammaLimit(id_camera: Long): Int
    private var mGammaMin = 0
    private var mGammaMax = 0
    private var mGammaDef = 0

    private external fun nativeUpdateSaturationLimit(id_camera: Long): Int
    private var mSaturationMin = 0
    private var mSaturationMax = 0
    private var mSaturationDef = 0

    private external fun nativeUpdateHueLimit(id_camera: Long): Int
    private external fun nativeUpdateAutoHueLimit(id_camera: Long): Int
    private var mHueMin = 0
    private var mHueMax = 0
    private var mHueDef = 0

    private external fun nativeUpdateZoomLimit(id_camera: Long): Int
    private var mZoomMin = 0
    private var mZoomMax = 0
    private var mZoomDef = 0

    private external fun nativeUpdateZoomRelLimit(id_camera: Long): Int
    private var mZoomRelMin = 0
    private var mZoomRelMax = 0
    private var mZoomRelDef = 0

    private external fun nativeUpdatePowerlineFrequencyLimit(id_camera: Long): Int
    private var mPowerlineFrequencyMin = 0
    private var mPowerlineFrequencyMax = 0
    private var mPowerlineFrequencyDef = 0

    private external fun nativeUpdateDigitalMultiplierLimit(id_camera: Long): Int
    private var mMultiplierMin = 0
    private var mMultiplierMax = 0
    private var mMultiplierDef = 0

    private external fun nativeUpdateDigitalMultiplierLimitLimit(id_camera: Long): Int
    private var mMultiplierLimitMin = 0
    private var mMultiplierLimitMax = 0
    private var mMultiplierLimitDef = 0

    private external fun nativeUpdateAnalogVideoStandardLimit(id_camera: Long): Int
    private var mAnalogVideoStandardMin = 0
    private var mAnalogVideoStandardMax = 0
    private var mAnalogVideoStandardDef = 0

    private external fun nativeUpdateAnalogVideoLockStateLimit(id_camera: Long): Int
    private var mAnalogVideoLockStateMin = 0
    private var mAnalogVideoLockStateMax = 0
    private var mAnalogVideoLockStateDef = 0

    private external fun nativeUpdatePrivacyLimit(id_camera: Long): Int
    private var mPrivacyMin = 0
    private var mPrivacyMax = 0
    private var mPrivacyDef = 0
}