
package net.nessness.android.sample.showmycodec;

import android.app.Activity;
import android.content.Context;
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
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
                    startProcess(R.raw.mp3_1khz);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private void startProcess(int audioId) throws IOException {

        final AssetFileDescriptor audioFd = getResources().openRawResourceFd(audioId);
        final MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(audioFd.getFileDescriptor(), audioFd.getStartOffset(),
                audioFd.getLength());
        MediaFormat format = extractor.getTrackFormat(0);
        Log.v(TAG, format.toString());

        // デコードテスト用
        // デコードするデータによって適当に変更する必要あり mono/stereoとかformatからとれなかったりするかも
        final AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC,
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                8000/* 適当 */, AudioTrack.MODE_STREAM);
        track.play();

        //        String mime = "audio/flac";
        //        MediaFormat outFormat = MediaFormat.createAudioFormat(mime,
        //                format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
        //                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
        //        // flac用のkeyがあったのでつけてみた
        //        outFormat.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 0);

        //parseWavInfo(format, audioId);

        //        final MediaCodec codec = MediaCodec.createEncoderByType(mime);
        //        codec.configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE /* encoder flag */);

        final MediaCodec codec = MediaCodec.createDecoderByType(format
                .getString(MediaFormat.KEY_MIME));
        codec.configure(format, null, null, 0);
        codec.start();

        extractor.selectTrack(0);

        String fileName = "test";

        new SampleCodecWithExractor(this, codec, extractor, track, fileName).process();
        //new SampleCodecWithInputStream(this, codec, track, audioId, "hoge").process();

        codec.stop();
        codec.release();
        audioFd.close();

        new FlacHeader(this, fileName).addHeader(format);
    }

    private void parseWavInfo(MediaFormat format, int wavId) throws IOException {
        InputStream is = getResources().openRawResource(wavId);
        int headerSize = 0x2c;
        byte[] buf = new byte[headerSize];
        int n = 0, s = 0;
        // buf にヘッダを読み込む
        while ((n = is.read(buf, n, headerSize - s)) != -1) {
            if ((s += n) == headerSize)
                break;
        }

        format.setByteBuffer("csd-0", ByteBuffer.wrap(buf));

        Log.v(TAG, "wav header.");
        FlacHeader.dump(buf);

        int sampleRate = getInt(buf, 0x18);
        int totalSize = getInt(buf, 0x28) / 4;
        Log.d(TAG, "sample rate: " + sampleRate + ", totalSize: " + totalSize);
    }

    // リトルエンディアン
    private int getInt(byte[] buf, int start) {
        return ((((((buf[start + 3] & 0xff) << 8) | (buf[start + 2] & 0xff)) << 8) | (buf[start + 1] & 0xff)) << 8)
                | (buf[start] & 0xff);
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

    private static class SampleCodecWithExractor extends Codec {

        private MediaExtractor mExtractor;
        private AudioTrack mTrack;
        private FileOutputStream mFos;

        public SampleCodecWithExractor(Context ctx, MediaCodec codec, MediaExtractor extractor,
                AudioTrack track, String outFile) {
            super(codec);
            mExtractor = extractor;
            mTrack = track;
            try {
                mFos = ctx.openFileOutput(outFile, Context.MODE_PRIVATE);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void process() {
            super.process();
            try {
                mFos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        protected int totalSamples = 0;

        boolean first = true;

        @Override
        protected int readSampleData(ByteBuffer buf) {
            int s = mExtractor.readSampleData(buf, 0);
            totalSamples += s;

            int pos = buf.position();
            byte[] data = new byte[buf.limit() - pos];
            buf.get(data);
            buf.position(pos);
            Log.d(TAG, "sample: " + s + ", total: " + totalSamples + ", " + pos);
            FlacHeader.dump(data, 0, 0x10);
            return s;
        }

        @Override
        protected long getSampleTime() {
            return mExtractor.getSampleTime();
        }

        @Override
        protected void advance() {
            mExtractor.advance();
        }

        @Override
        protected void readOutputBuffer(ByteBuffer buf, BufferInfo info) {
            Log.v(TAG, "output: " + Integer.toBinaryString(info.flags));
            byte[] data = new byte[info.size];
            int pos = buf.position();
            buf.get(data);
            buf.position(pos);

            FlacHeader.dump(data, 0, 0x10);

            if (mTrack != null) {
                mTrack.write(data, 0, data.length);
            }
            try {
                mFos.write(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected void outputFormatChanged() {
            super.outputFormatChanged();
        }
    }

    private static class SampleCodecWithInputStream extends Codec {

        private static final String TAG = SampleCodecWithInputStream.class.getSimpleName();

        protected AudioTrack mTrack;
        protected FileOutputStream mFos;
        protected BufferedInputStream mBis;
        protected int mSampleTimeUs;

        public SampleCodecWithInputStream(Context ctx, MediaCodec codec,
                AudioTrack track, String inFile, String outFile) {
            super(codec);

            try {
                init(ctx, track, outFile);
                mBis = new BufferedInputStream(ctx.openFileInput(inFile));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        public SampleCodecWithInputStream(Context ctx, MediaCodec codec,
                AudioTrack track, int inId, String outFile) {
            super(codec);
            try {
                init(ctx, track, outFile);
                mBis = new BufferedInputStream(ctx.getResources().openRawResource(inId));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        private void init(Context ctx, AudioTrack track, String outFile)
                throws FileNotFoundException {
            mTrack = track;
            mSampleTimeUs = 0;
            mFos = ctx.openFileOutput(outFile, Context.MODE_PRIVATE);
        }

        @Override
        public void process() {
            super.process();
            try {
                mBis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        boolean first = true;

        @Override
        protected int readSampleData(ByteBuffer buf) {
            // かなりアヤシイ実装
            int sampleSize = 0;
            byte[] data = new byte[2048];
            try {
                sampleSize = mBis.read(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (sampleSize > 0) {
                int pos = buf.position();
                buf.put(data);
                buf.position(pos);
            }
            Log.v(TAG, "input: " + sampleSize + ", " + data.length + ", " + buf.position());
            return sampleSize;
        }

        @Override
        protected long getSampleTime() {
            // デコードのときに適当な数字を返して場合も普通に再生できてしまった
            //Log.v(TAG, "sampletime: " + mSampleTimeUs);
            return mSampleTimeUs;
        }

        @Override
        protected void advance() {
        }

        boolean ofirst = true;

        @Override
        protected void readOutputBuffer(ByteBuffer buf, BufferInfo info) {
            Log.v(TAG,
                    "output size: " + info.size + ", flag: " + Integer.toBinaryString(info.flags));
            byte[] data = new byte[info.size];
            int pos = buf.position();
            buf.get(data);

            FlacHeader.dump(data, 0, 0x10);

            if (mTrack != null) {
                mTrack.write(data, 0, data.length);
            }
            try {
                mFos.write(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
            buf.position(pos);
        }
    }
}
