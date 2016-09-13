package com.iflytek.wpcheng.dynamiccurvetest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.LinkedList;

/**
 * Created by wpcheng on 2016/9/2.
 */
public class CurveView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "BezierView";
    private static int SAMPLE_RATE_IN_HZ = 8000;
    private SurfaceHolder holder;
    private RecordAndDrawThread workThread;
    private boolean isRecordingAndDrawing = false;
    private boolean surfaceEnable = false;
    private int surfaceHeight;
    private int surfaceWidth;

    //曲线相关参数
    private int A = 64;
    private double C1 = 0.005d;
    private double C2 = 0.001d;
    private double W1 = 0.08d;
    private double W2 = 0.008d;
    private double P = 0d;
    private double P1 = 0d;
    private double P2 = 0d;

    //录音噪音参数
    private double noiseThreshold = 20d;

    //开始绘制的时间,用于曲线的时间参数
    private long startTime;

    //曲线点的集合
    private LinkedList<Point> points = new LinkedList<Point>();

    //一次说话的最大音量
    //private double maxVolumnPerTime = 0;

    public CurveView(Context context) {
        super(context);
        holder = getHolder();
        holder.addCallback(this);
    }

    public CurveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        holder = getHolder();
        holder.addCallback(this);
    }

    public CurveView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        holder = getHolder();
        holder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        surfaceEnable = true;
        surfaceHeight = getHeight();
        surfaceWidth = getWidth();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        surfaceEnable = false;
        isRecordingAndDrawing = false;
    }

    public boolean startRecordAndDraw() {
        if (surfaceEnable && !isRecordingAndDrawing) {
            workThread = new RecordAndDrawThread();
            workThread.startWork();
            return true;
        }
        Log.d(TAG, "surface is not avaliable or already drawing!");
        return false;
    }

    public void stopRecordAndDraw() {
        workThread.stopWork();
    }


    class RecordAndDrawThread extends Thread {
        private AudioRecord mAudioRecord;
        private int mBufferSize;

        public RecordAndDrawThread() {
            mBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_IN_HZ,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE_IN_HZ, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, mBufferSize);
        }

        @Override
        public void run() {
            mAudioRecord.startRecording();
            byte[] bytes = new byte[mBufferSize];
            //记录上一次有音频输入时的音量
            double previousVolumn = 0;
            while (isRecordingAndDrawing) {
                double volumn = obtainVolumn(bytes);
                if (volumn == -1) {
                    Log.d(TAG, "recorder error!");
                    return;
                } else if(volumn != 0) {
                    //如果volumn不为0，说明有新的音频录入，这时候重新计时来画图，也就是重新开始衰减效果。
                    startTime = System.currentTimeMillis();
                    //如果volumn不为0，就把这个有效音量计入到previousVolumn中。
                    previousVolumn = volumn;
                }
                draw(previousVolumn);
            }
        }

        private double obtainVolumn(byte[] bytes) {
            int r = mAudioRecord.read(bytes, 0, mBufferSize);
            if (r == AudioRecord.ERROR_INVALID_OPERATION) {
                Log.i(TAG, "AudioRecord.ERROR_INVALID_OPERATION");
                return -1;
            }
            if (r == AudioRecord.ERROR_BAD_VALUE) {
                Log.i(TAG, "AudioRecord.ERROR_BAD_VALUE");
                return -1;
            }
            if (r == 0) {
                return -1;
            }
            int v = 0;
            for (int i = 0; i < bytes.length; i++) {
                v += bytes[i] * bytes[i];
            }
            //音量大小（无单位）
            double value = Math.sqrt(v) / r * 10;

            //如果value小于20，则认为是噪音，不予以显示
            if (value <= noiseThreshold) {
                value = 0;
            }
            //放大显示效果
            value *= 10;
            Log.i(TAG, "音量大小:" + value);
            return value;
        }

        private void draw(double volumn) {
            Canvas canvas = holder.lockCanvas();
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setColor(Color.WHITE);

            //清空画布
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            canvas.drawPaint(paint);

            //开始绘制
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));

            //计算点阵
            for (int x = 0; x < surfaceWidth; x += 5) {
                long t = System.currentTimeMillis() - startTime;
                int y = h(x, t);
                Point p = new Point(x, surfaceHeight / 2 - y);
                points.push(p);
            }

            //连接点阵
            Point start = points.pollLast();
            Point end = points.pollLast();
            while (end != null) {
                //正
                canvas.drawLine(start.x, start.y, end.x, end.y, paint);
                //反
                canvas.drawLine(start.x, surfaceHeight-start.y, end.x, surfaceHeight-end.y, paint);
                start = end;
                end = points.pollLast();
            }

            holder.unlockCanvasAndPost(canvas);
        }

        private int h(int x, long t) {
            double result = 0d;
            double part1 = A * Math.exp(-C1 * x - C2 * t);
            double part2 = Math.sin(W1 * x + P1);
            double part3 = Math.cos(W2 * t + P2);
            double part4 = Math.sin(W2 * t - W1 * x + P);

            //驻波
            //result = part1 * part2 * part3;
            //行波
            result = part1 * part4;
            return (int) result;
        }

        public void stopWork() {
            isRecordingAndDrawing = false;
            if (mAudioRecord != null) {
                mAudioRecord.stop();
                mAudioRecord.release();
                mAudioRecord = null;
            }
        }

        public void startWork() {
            isRecordingAndDrawing = true;
            startTime = System.currentTimeMillis();
            start();
        }

    }
}
