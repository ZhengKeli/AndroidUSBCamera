package com.serenegiant.media;


import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

public interface IRecorder {

    interface RecorderCallback {
        void onPrepared(IRecorder recorder);

        void onStarted(IRecorder recorder);

        void onStopped(IRecorder recorder);

        void onError(Exception e);
    }

    /**
     * キャプチャしていない
     */
    int STATE_UNINITIALIZED = 0;
    /**
     * キャプチャ初期化済(Muxerセット済)
     */
    int STATE_INITIALIZED = 1;
    /**
     * キャプチャ準備完了(prepare済)
     */
    int STATE_PREPARED = 2;
    /**
     * キャプチャ開始中
     */
    int STATE_STARTING = 3;
    /**
     * キャプチャ中
     */
    int STATE_STARTED = 4;
    /**
     * キャプチャ停止要求中
     */
    int STATE_STOPPING = 5;

    /**
     * キャプチャ終了
     */
//	public static final int STATE_STOPPED = 6;

    @IntDef({STATE_UNINITIALIZED, STATE_INITIALIZED, STATE_PREPARED,
            STATE_STARTING, STATE_STARTED, STATE_STOPPING})
    @Retention(RetentionPolicy.SOURCE)
    @interface RecorderState {
    }

    void setMuxer(final IMuxer muxer);

    /**
     * Encoderの準備
     * 割り当てられているMediaEncoderの下位クラスのインスタンスの#prepareを呼び出す
     *
     * @throws IOException
     */
    void prepare();

    /**
     * キャプチャ開始要求
     * 割り当てられているEncoderの下位クラスのインスタンスの#startRecordingを呼び出す
     */
    void startRecording() throws IllegalStateException;

    /**
     * キャプチャ終了要求
     * 割り当てられているEncoderの下位クラスの#stopRecordingを呼び出す
     */
    void stopRecording();

    Surface getInputSurface();

    Encoder getVideoEncoder();

    Encoder getAudioEncoder();

    /**
     * Muxerが出力開始しているかどうかを返す
     *
     * @return
     */
    boolean isStarted();

    /**
     * エンコーダーの初期化が終わって書き込み可能になったかどうかを返す
     *
     * @return
     */
    boolean isReady();

    /**
     * 終了処理中かどうかを返す
     *
     * @return
     */
    boolean isStopping();

    /**
     * 終了したかどうかを返す
     *
     * @return
     */
    boolean isStopped();

    int getState();

    IMuxer getMuxer();

    @Nullable
    String getOutputPath();

    @Nullable
    DocumentFile getOutputFile();

    void frameAvailableSoon();

    /**
     * 関連するリソースを開放する
     */
    void release();

    void addEncoder(final Encoder encoder);

    void removeEncoder(final Encoder encoder);

    boolean start(final Encoder encoder);

    void stop(final Encoder encoder);

    int addTrack(final Encoder encoder, final MediaFormat format);

    void writeSampleData(final int trackIndex,
                         final ByteBuffer byteBuf, final MediaCodec.BufferInfo bufferInfo);
}