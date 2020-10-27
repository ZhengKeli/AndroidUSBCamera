package com.jiangdg.usbcamera.activity

import android.app.Activity
import android.app.Dialog
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import com.jiangdg.usbcamera.R
import com.jiangdg.usbcamera.UVCCameraHelper
import com.jiangdg.usbcamera.UVCCameraHelper.OnMyDevConnectListener
import com.jiangdg.usbcamera.application.MyApplication
import com.jiangdg.usbcamera.utils.FileUtils
import com.serenegiant.usb.CameraDialog.CameraDialogParent
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.common.AbstractUVCCameraHandler.*
import com.serenegiant.usb.encoder.RecordParams
import com.serenegiant.usb.widget.CameraViewInterface

class USBCameraActivity : AppCompatActivity(), CameraDialogParent, CameraViewInterface.Callback {
    lateinit var mToolbar: Toolbar
    lateinit var mSeekBrightness: SeekBar
    lateinit var mSeekContrast: SeekBar
    lateinit var mSwitchVoice: SwitchCompat
    private lateinit var mCameraHelper: UVCCameraHelper
    private lateinit var mUVCCameraView: CameraViewInterface
    private var isRequest = false
    private var isPreview = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usbcamera)
        mToolbar = findViewById(R.id.toolbar)
        mSeekBrightness = findViewById(R.id.seekbar_brightness)
        mSeekContrast = findViewById(R.id.seekbar_contrast)
        mSwitchVoice = findViewById(R.id.switch_rec_voice)
        initView()

        // step.1 initialize UVCCameraHelper
        mUVCCameraView = findViewById(R.id.camera_view)
        mUVCCameraView.setCallback(this)

        mCameraHelper = UVCCameraHelper.getInstance()
        mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_MJPEG)
        mCameraHelper.initUSBMonitor(this, mUVCCameraView, object : OnMyDevConnectListener {
            override fun onAttachDev(device: UsbDevice) {
                // request open permission
                if (!isRequest) {
                    isRequest = true
                    mCameraHelper.requestPermission(0)
                }
            }

            override fun onDettachDev(device: UsbDevice) {
                // close camera
                if (isRequest) {
                    isRequest = false
                    mCameraHelper.closeCamera()
                    showShortMsg(device.deviceName + " is out")
                }
            }

            override fun onConnectDev(device: UsbDevice, isConnected: Boolean) {
                if (!isConnected) {
                    showShortMsg("fail to connect,please check resolution params")
                    isPreview = false
                } else {
                    isPreview = true
                    showShortMsg("connecting")
                    // initialize seekbar
                    // need to wait UVCCamera initialize over
                    Thread {
                        try {
                            Thread.sleep(2500)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                        Looper.prepare()
                        if (mCameraHelper.isCameraOpened) {
                            mSeekBrightness.setProgress(mCameraHelper.getModelValue(UVCCameraHelper.MODE_BRIGHTNESS))
                            mSeekContrast.setProgress(mCameraHelper.getModelValue(UVCCameraHelper.MODE_CONTRAST))
                        }
                        Looper.loop()
                    }.start()
                }
            }

            override fun onDisConnectDev(device: UsbDevice) {
                showShortMsg("disconnecting")
            }
        })
        mCameraHelper.setOnPreviewFrameListener { nv21Yuv -> Log.d(TAG, "onPreviewResult: " + nv21Yuv.size) }
    }

    private fun initView() {
        setSupportActionBar(mToolbar)
        mSeekBrightness.max = 100
        mSeekBrightness.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (mCameraHelper.isCameraOpened) {
                    mCameraHelper.setModelValue(UVCCameraHelper.MODE_BRIGHTNESS, progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        mSeekContrast.max = 100
        mSeekContrast.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (mCameraHelper.isCameraOpened) {
                    mCameraHelper.setModelValue(UVCCameraHelper.MODE_CONTRAST, progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    override fun onStart() {
        super.onStart()
        // step.2 register USB event broadcast
        mCameraHelper.registerUSB()
    }

    override fun onStop() {
        super.onStop()
        // step.3 unregister USB event broadcast
        mCameraHelper.unregisterUSB()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_toobar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_takepic -> {
                if (!mCameraHelper.isCameraOpened) {
                    showShortMsg("sorry,camera open failed")
                    return super.onOptionsItemSelected(item)
                }
                val picPath = (UVCCameraHelper.ROOT_PATH + MyApplication.DIRECTORY_NAME + "/images/"
                        + System.currentTimeMillis() + UVCCameraHelper.SUFFIX_JPEG)
                val activity: Activity = this
                mCameraHelper.capturePicture(picPath, OnCaptureListener { path ->
                    if (TextUtils.isEmpty(path)) return@OnCaptureListener
                    Handler(mainLooper).post { Toast.makeText(activity, "save path:$path", Toast.LENGTH_SHORT).show() }
                })
            }
            R.id.menu_recording -> {
                if (!mCameraHelper.isCameraOpened) {
                    showShortMsg("sorry,camera open failed")
                    return super.onOptionsItemSelected(item)
                }
                if (!mCameraHelper.isPushing) {
                    val videoPath = (UVCCameraHelper.ROOT_PATH + MyApplication.DIRECTORY_NAME + "/videos/" + System.currentTimeMillis()
                            + UVCCameraHelper.SUFFIX_MP4)

//                    FileUtils.createfile(FileUtils.ROOT_PATH + "test666.h264");
                    // if you want to record,please create RecordParams like this
                    val params = RecordParams()
                    params.recordPath = videoPath
                    params.recordDuration = 0 // auto divide saved,default 0 means not divided
                    params.isVoiceClose = mSwitchVoice.isChecked // is close voice
                    params.isSupportOverlay = true // overlay only support armeabi-v7a & arm64-v8a
                    mCameraHelper.startPusher(params, object : OnEncodeResultListener {
                        override fun onEncodeResult(data: ByteArray, offset: Int, length: Int, timestamp: Long, type: Int) {
                            // type = 1,h264 video stream
                            if (type == 1) {
                                FileUtils.putFileStream(data, offset, length)
                            }
                            // type = 0,aac audio stream
                        }

                        override fun onRecordResult(videoPath: String) {
                            if (TextUtils.isEmpty(videoPath)) return
                            Handler(mainLooper).post { Toast.makeText(this@USBCameraActivity, "save videoPath:$videoPath", Toast.LENGTH_SHORT).show() }
                        }
                    })
                    // if you only want to push stream,please call like this
                    // mCameraHelper.startPusher(listener);
                    showShortMsg("start record...")
                    mSwitchVoice.isEnabled = false
                } else {
                    FileUtils.releaseFile()
                    mCameraHelper.stopPusher()
                    showShortMsg("stop record...")
                    mSwitchVoice.isEnabled = true
                }
            }
            R.id.menu_resolution -> {
                if (!mCameraHelper.isCameraOpened) {
                    showShortMsg("sorry,camera open failed")
                    return super.onOptionsItemSelected(item)
                }
                showResolutionListDialog()
            }
            R.id.menu_focus -> {
                if (!mCameraHelper.isCameraOpened) {
                    showShortMsg("sorry,camera open failed")
                    return super.onOptionsItemSelected(item)
                }
                mCameraHelper.startCameraFocus()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showResolutionListDialog() {
        lateinit var mDialog: Dialog
        val builder = AlertDialog.Builder(this@USBCameraActivity)
        val rootView = LayoutInflater.from(this@USBCameraActivity).inflate(R.layout.layout_dialog_list, null)
        val listView = rootView.findViewById<View>(R.id.listview_dialog) as ListView
        val adapter = ArrayAdapter<String>(this@USBCameraActivity, android.R.layout.simple_list_item_1, resolutionList)
        listView.adapter = adapter
        listView.onItemClickListener = OnItemClickListener { adapterView, _, position, _ ->
            if (!mCameraHelper.isCameraOpened) return@OnItemClickListener
            val resolution = adapterView.getItemAtPosition(position) as String
            val tmp = resolution.split("x".toRegex()).toTypedArray()
            if (tmp.size >= 2) {
                val width = tmp[0].toInt()
                val height = tmp[1].toInt()
                mCameraHelper.updateResolution(width, height)
            }
            mDialog.dismiss()
        }
        builder.setView(rootView)
        mDialog = builder.create()
        mDialog.show()
    }

    // example: {640x480,320x240,etc}
    private val resolutionList: List<String>
        get() = mCameraHelper.supportedPreviewSizes?.map { "${it.width}x${it.height}" }
                ?: emptyList()

    override fun onDestroy() {
        super.onDestroy()
        FileUtils.releaseFile()
        mCameraHelper.release()
    }

    private fun showShortMsg(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun getUSBMonitor(): USBMonitor {
        return mCameraHelper.usbMonitor
    }

    override fun onDialogResult(canceled: Boolean) {
        if (canceled) {
            showShortMsg("取消操作")
        }
    }

    val isCameraOpened: Boolean
        get() = mCameraHelper.isCameraOpened

    override fun onSurfaceCreated(view: CameraViewInterface, surface: Surface) {
        if (!isPreview && mCameraHelper.isCameraOpened) {
            mCameraHelper.startPreview(mUVCCameraView)
            isPreview = true
        }
    }

    override fun onSurfaceChanged(view: CameraViewInterface, surface: Surface, width: Int, height: Int) {}
    override fun onSurfaceDestroy(view: CameraViewInterface, surface: Surface) {
        if (isPreview && mCameraHelper.isCameraOpened) {
            mCameraHelper.stopPreview()
            isPreview = false
        }
    }

    companion object {
        private const val TAG = "Debug"
    }
}