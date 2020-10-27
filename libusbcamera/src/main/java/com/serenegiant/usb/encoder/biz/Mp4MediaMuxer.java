package com.serenegiant.usb.encoder.biz;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class Mp4MediaMuxer {
    private String mFilePath;
    private MediaMuxer mMuxer;
    private long durationMillis;
    private int index = 0;
    private int mVideoTrackIndex = -1;
    private int mAudioTrackIndex = -1;
    private long mBeginMillis;
    private MediaFormat mVideoFormat;
    private MediaFormat mAudioFormat;

    // 文件路径；文件时长
    public Mp4MediaMuxer(String path, long durationMillis) {
        String mFilePath;
        this.durationMillis = durationMillis;
        if (durationMillis != 0) {
            mFilePath = path + "-" + index++ + ".mp4";
        } else {
            mFilePath = path + ".mp4";
        }
        MediaMuxer mux = null;
        try {
            mux = new MediaMuxer(mFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            mMuxer = mux;
        }
    }

    public synchronized void addTrack(MediaFormat format, boolean isVideo) {
        // start the muxer
        int track = mMuxer.addTrack(format);
        if (isVideo) {
            mVideoFormat = format;
            mVideoTrackIndex = track;
            // 当音频轨道添加
            // 或者开启静音就start
            mMuxer.start();
            mBeginMillis = System.currentTimeMillis();
        } else {
            mAudioFormat = format;
            mAudioTrackIndex = track;
            if (mVideoTrackIndex != -1) {
                mMuxer.start();
                mBeginMillis = System.currentTimeMillis();
            }
        }
    }

    public synchronized void pumpStream(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo, boolean isVideo) {
        if (mVideoTrackIndex == -1) {
            return;
        }
        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            // The codec config data was pulled out and fed to the muxer when we got
            // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
        } else {
            if (bufferInfo.size != 0) {
                if (isVideo && mVideoTrackIndex == -1) {
                    throw new RuntimeException("muxer hasn't started");
                }

                // adjust the ByteBuffer values to match BufferInfo (not needed?)
                outputBuffer.position(bufferInfo.offset);
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                mMuxer.writeSampleData(isVideo ? mVideoTrackIndex : mAudioTrackIndex, outputBuffer, bufferInfo);
            }
        }


        if (durationMillis != 0 && System.currentTimeMillis() - mBeginMillis >= durationMillis) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
            mVideoTrackIndex = mAudioTrackIndex = -1;
            try {
                mMuxer = new MediaMuxer(mFilePath + "-" + ++index + ".mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                addTrack(mVideoFormat, true);
                addTrack(mAudioFormat, false);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void release() {
        if (mMuxer == null) return;
        if (mVideoTrackIndex == -1) return;
        try {
            mMuxer.stop();
            mMuxer.release();
        } catch (IllegalStateException ex) {
            ex.printStackTrace();
        }

        if (System.currentTimeMillis() - mBeginMillis <= 1500) {
            new File(mFilePath + "-" + index + ".mp4").delete();
        }
        mAudioTrackIndex = mVideoTrackIndex = -1;
    }
}
