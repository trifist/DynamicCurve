package com.iflytek.wpcheng.dynamiccurvetest;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {
    private CurveView mBezierView;
    private boolean mIsStop = true;
    private Button mButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mButton = (Button) this.findViewById(R.id.button);
        mBezierView = (CurveView) this.findViewById(R.id.bezier);
        mBezierView.setZOrderMediaOverlay(true);
        mBezierView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        mButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                if (mIsStop) {
                    mBezierView.startRecordAndDraw();
                    mButton.setText("stop");
                    mIsStop = false;
                } else {
                    mBezierView.stopRecordAndDraw();
                    mButton.setText("start");
                    mIsStop = true;
                }
            }
        });

    }
}
