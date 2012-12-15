
package net.nessness.android.sample.showmycodec;

import android.content.Context;
import android.media.MediaFormat;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class FlacHeader {

    private static final String TAG = FlacHeader.class.getSimpleName();

    private final Context mContext;
    private final String mFileName;

    public FlacHeader(Context ctx, String fileName) {
        mContext = ctx.getApplicationContext();
        mFileName = fileName;
    }

    public void addHeader(MediaFormat format) throws IOException {
        String origName = mFileName;
        String inName = origName + ".tmp";

        File parent = mContext.getFilesDir();
        File inFile = new File(parent, inName);
        if (inFile.exists()) {
            inFile.delete();
        }
        new File(parent, origName).renameTo(inFile);

        FileInputStream in = mContext.openFileInput(inName);
        FileOutputStream out = mContext.openFileOutput(origName, Context.MODE_PRIVATE);

        // marker
        String marker = "fLaC";
        Log.v("", "" + Integer.toHexString((marker.getBytes()[2] & 0xff))); // 61
        out.write(marker.getBytes());

        // meta data
        byte[] metaHeader;
        byte[] metaData;

        metaHeader = new byte[] {
                0x1 << 3 | 0, 0, 0, 34
        };
        metaData = metaDataStreamInfo(format);
        dump(metaData);

        out.write(metaHeader);
        out.write(metaData);

        byte[] buf = new byte[1024];
        int r;
        while ((r = in.read(buf)) > 0) {
            out.write(buf, 0, r);
        }

        in.close();
        out.close();
    }

    private byte[] metaDataStreamInfo(MediaFormat format) {
        Log.v(TAG, format.toString());
        int hz = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int ch = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) - 1; /* ch -1 */
        int total = format.getInteger("total_size");
        int bit = 16 - 1; /* 16bits per sample -1 */
        return new byte[] {
                0x01, 0x00, /* min block size (?) */
                0x10, 0x00, /* max block size (?) */
                0x00, 0x00, 0x01, /* min frame size, unknown */
                0x10, 0x00, 0x00, /* max frame size, unknown */
                (byte) ((hz >> 12) & 0xff), /* sample rate (hz) 20bits */
                (byte) ((hz >> 4) & 0xff),
                (byte) (((hz << 4) & 0xff) /* 4bits */| ((ch << 1) & 0xff) /* 3bits */| ((bit >> 4) & 0xff) /* 1bit */),
                (byte) ((bit << 4) & 0xff | 0), /* bits(low 4bit) + total samples in stream(first 4bit...?) */
                (byte) ((total >> 24) & 0xff), (byte) ((total >> 16) & 0xff), (byte) ((total >> 8) & 0xff), (byte) (total & 0xff), /* 32bit, 0 -> unknown */
                0, 0, 0, 0, 0, 0, 0, 0, /* md5 signature of unencoded audio data */
                0, 0, 0, 0, 0, 0, 0, 0,
        };
    }

    public static void dump(byte[] b) {
        dump(b, 0, b.length);
    }

    public static void dump(byte[] b, int offset, int length) {
        final StringBuilder s = new StringBuilder();
        final char spc = ' ';
        final char r = '\n';
        final char z = '0';
        final int n = Math.min(b.length, offset + length);
        final int ret = offset % 0x10;
        final int sep = offset % 0x04;
        String h;
        for (int i = offset; i < n; i++) {
            h = Integer.toHexString(b[i] & 0xff);
            if (i % 0x10 == ret) {
                s.append(r);
            }
            if (i % 0x04 == sep) {
                s.append(spc);
            }
            if (h.length() == 1) {
                s.append(z);
            }
            s.append(h).append(spc);
        }
        Log.v("dump", s.toString());
    }
}
