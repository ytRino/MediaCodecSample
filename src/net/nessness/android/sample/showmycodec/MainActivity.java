
package net.nessness.android.sample.showmycodec;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listAvailableCodecs();

        new Thread() {
            @Override
            public void run() {
                try {
                    process(R.raw.nare);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private void process(int audioId) throws IOException {

        AssetFileDescriptor audioFd = getResources().openRawResourceFd(audioId);
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(audioFd.getFileDescriptor(), audioFd.getStartOffset(),
                audioFd.getLength());
        MediaFormat format = extractor.getTrackFormat(0);

        Log.v(TAG, format.toString());

        String mime = format.getString(MediaFormat.KEY_MIME);
        //String mime = "audio/flac";
        //MediaFormat format = MediaFormat.createAudioFormat(mime, 44100, 2);
        //MediaCodec codec = MediaCodec.createEncoderByType(mime);
        //MediaCodec codec = MediaCodec.createByCodecName("OMX.google.flac.encoder");
        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        // エンコードする場合はエンコードフラグをつける
        //codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.configure(format, null, null, 0);
        codec.start();

        ByteBuffer[] codecInputBuffers = codec.getInputBuffers();
        ByteBuffer[] codecOutputBuffers = codec.getOutputBuffers();

        // エンコード結果書きだそうと思った
        //        File file = new File(getFilesDir(), "test.flac");
        //        file.getParentFile().mkdirs();
        //        Log.d(TAG, "file: " + file.getAbsolutePath());
        //        FileOutputStream fos = new FileOutputStream(file);

        extractor.selectTrack(0);

        // nurupo
        //int sampleRate = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        // mp3デコードテスト用
        AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC,
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                8000/* 適当 */, AudioTrack.MODE_STREAM);
        track.play();

        BufferInfo info = new BufferInfo();
        boolean inputEos = false;
        boolean outputEos = false;
        long timeoutUs = 1000;
        while (!outputEos) {
            Log.v(TAG, "encoding... " + inputEos + ", " + outputEos);
            if (!inputEos) {

                int inputBufIndex = codec.dequeueInputBuffer(timeoutUs);
                if (inputBufIndex >= 0) {
                    // inputバッファにエンコードするデータを入れる
                    ByteBuffer buf = codecInputBuffers[inputBufIndex];
                    Log.v(TAG, buf + ", " + inputBufIndex + ", " + buf.position());
                    int sampleSize = extractor.readSampleData(buf, 0);
                    // 長さマイクロ秒
                    long presentationTimeUs = 0;
                    if (sampleSize < 0) {
                        Log.d(TAG, "input EOS.");
                        inputEos = true;
                        sampleSize = 0;
                    } else {
                        presentationTimeUs = extractor.getSampleTime();
                    }

                    codec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs,
                            inputEos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                    if (!inputEos) {
                        // 次へ進める
                        extractor.advance();
                    }
                }
            }

            int res = codec.dequeueOutputBuffer(info, timeoutUs);

            if (res >= 0) {
                int outputBufIndex = res;
                //fos.write(codecOutputBuffers[outputBufIndex].);
                ByteBuffer buf = codecOutputBuffers[outputBufIndex];
                byte[] dst = new byte[info.size];
                int oldPosition = buf.position();
                Log.v(TAG, info.size + ", " + buf.limit() + ", " + oldPosition);
                buf.get(dst);
                buf.position(oldPosition);

                // mp3デコードテスト用
                track.write(dst, 0, dst.length);

                codec.releaseOutputBuffer(outputBufIndex, false);

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "output eos.");
                    outputEos = true;
                }
            } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                Log.d(TAG, "output buffer changed.");
                codecOutputBuffers = codec.getOutputBuffers();
            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat f = codec.getOutputFormat();
                Log.d(TAG, "output format changed. " + f);
                //track.setPlaybackRate(f.getInteger(MediaFormat.KEY_SAMPLE_RATE));
            }
        }
        codec.stop();
        codec.release();
        audioFd.close();
    }

    private void listAvailableCodecs() {
        final TextView tv = ((TextView) findViewById(R.id.codecs));
        tv.setMovementMethod(ScrollingMovementMethod.getInstance());

        final int count = MediaCodecList.getCodecCount();
        for (int i = 0; i < count; i++) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            String codec = info.getName() + "\n  "
                    + TextUtils.join("\n  ", info.getSupportedTypes())
                    + "\n";
            tv.append(codec);
            //Log.v(TAG, codec);
        }
    }

}
