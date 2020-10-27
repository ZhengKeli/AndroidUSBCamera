package com.jiangdg.usbcamera.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import com.jiangdg.usbcamera.R
import java.util.*


class SplashActivity : AppCompatActivity() {
    companion object {
        private val REQUIRED_PERMISSION_LIST = arrayOf(Manifest.permission.RECORD_AUDIO)
        private const val REQUEST_CODE = 1
    }

    private val mMissPermissions: MutableList<String> = ArrayList()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkAndRequestPermissions()
        } else {
            startMainActivity()
        }
    }

    private fun checkAndRequestPermissions() {
        mMissPermissions.clear()
        for (permission in REQUIRED_PERMISSION_LIST) {
            val result = checkSelfPermission(this, permission)
            if (result != PERMISSION_GRANTED) {
                mMissPermissions.add(permission)
            }
        }
        // check permissions has granted
        if (mMissPermissions.isEmpty()) {
            startMainActivity()
        } else {
            val permissions = mMissPermissions.toTypedArray()
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            for (i in grantResults.indices.reversed()) {
                if (grantResults[i] == PERMISSION_GRANTED) {
                    mMissPermissions.remove(permissions[i])
                }
            }
        }
        // Get permissions success or not
        if (mMissPermissions.isEmpty()) {
            startMainActivity()
        } else {
            Toast.makeText(this@SplashActivity, "get permissions failed,exiting...", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun startMainActivity() {
        startActivity(Intent(this@SplashActivity, USBCameraActivity::class.java))
        finish()
    }


}