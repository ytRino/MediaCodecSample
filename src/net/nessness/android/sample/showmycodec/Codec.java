
package net.nessness.android.sample.showmycodec;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.nio.ByteBuffer;

public abstract class Codec {

    private static final String TAG = Codec.class.getSimpleName();

    protected MediaCodec mCodec;

    public Codec(MediaCodec codec) {
        mCodec = codec;
    }

    public void process() {
        ByteBuffer[] codecInputBuffers = mCodec.getInputBuffers();
        ByteBuffer[] codecOutputBuffers = mCodec.getOutputBuffers();

        BufferInfo info = new BufferInfo();
        boolean inputEos = false;
        boolean outputEos = false;
        long timeoutUs = 5000;
        while (!outputEos) {
            //Log.v(TAG, "processing... " + inputEos + ", " + outputEos);
            if (!inputEos) {

                int inputBufIndex = mCodec.dequeueInputBuffer(timeoutUs);
                if (inputBufIndex >= 0) {
                    // inputバッファにエンコードするデータを入れる
                    ByteBuffer buf = codecInputBuffers[inputBufIndex];
                    //Log.v(TAG, buf + ", " + inputBufIndex + ", " + buf.position());
                    int sampleSize = readSampleData(buf);
                    // 経過時間マイクロ秒(?)
                    long presentationTimeUs = 0;
                    if (sampleSize < 0) {
                        Log.d(TAG, "input EOS.");
                        inputEos = true;
                        sampleSize = 0;
                    } else {
                        presentationTimeUs = getSampleTime();
                    }

                    mCodec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs,
                            inputEos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                    if (!inputEos) {
                        // 次へ進める
                        advance();
                    }
                }
            }

            int res = mCodec.dequeueOutputBuffer(info, timeoutUs);

            if (res >= 0) {
                int outputBufIndex = res;
                ByteBuffer buf = codecOutputBuffers[outputBufIndex];
                readOutputBuffer(buf, info);

                mCodec.releaseOutputBuffer(outputBufIndex, false);

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "output eos.");
                    outputEos = true;
                }
            } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = mCodec.getOutputBuffers();
                Log.d(TAG, "output buffer changed.");
                outputBufferChanged();
            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat f = mCodec.getOutputFormat();
                Log.d(TAG, "output format changed. " + f);
                outputFormatChanged();
            } else {
                //Log.v(TAG, "another media codec info: " + res + ", " + info);
            }
        }
    }

    protected abstract int readSampleData(ByteBuffer buf);

    protected abstract long getSampleTime();

    protected abstract void advance();

    protected abstract void readOutputBuffer(ByteBuffer buf, BufferInfo info);

    protected void outputBufferChanged() {
    }

    protected void outputFormatChanged() {
    }
}
