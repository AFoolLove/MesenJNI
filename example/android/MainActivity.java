package com.example.mesenjni;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import mesen.Mesen;

/**
 * 最小可用示例：把一个 ROM 跑起来，画面显示在 {@link MesenView} 上，
 * 底部一排按钮映射到 NES 手柄。
 */
public class MainActivity extends Activity
{
    private MesenEmulator emulator;
    private MesenView view;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        emulator = new MesenEmulator();
        view = new MesenView(this);
        // 每帧回调在 core 渲染线程；setFrame 内部 postInvalidateOnAnimation 会切回 UI 线程
        emulator.setFrameListener(view::setFrame);

        FrameLayout screen = new FrameLayout(this);
        screen.addView(view, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.HORIZONTAL);
        pad.addView(gameButton("A", Mesen.BUTTON_A));
        pad.addView(gameButton("B", Mesen.BUTTON_B));
        pad.addView(gameButton("Start", Mesen.BUTTON_START));
        pad.addView(gameButton("Select", Mesen.BUTTON_SELECT));

        LinearLayout dpad = new LinearLayout(this);
        dpad.setOrientation(LinearLayout.HORIZONTAL);
        dpad.addView(gameButton("Up", Mesen.BUTTON_UP));
        dpad.addView(gameButton("Down", Mesen.BUTTON_DOWN));
        dpad.addView(gameButton("Left", Mesen.BUTTON_LEFT));
        dpad.addView(gameButton("Right", Mesen.BUTTON_RIGHT));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.addView(pad);
        bottom.addView(dpad);

        FrameLayout root = new FrameLayout(this);
        root.addView(screen, new FrameLayout.LayoutParams(-1, -1));
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM));
        setContentView(root);

        startEmulator();
    }

    /** 按住 = 按下，抬起/取消 = 松开。 */
    private View gameButton(String text, int buttonId)
    {
        Button b = new Button(this);
        b.setText(text);
        b.setOnTouchListener((v, e) -> {
            switch(e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    emulator.setButton(buttonId, true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    emulator.setButton(buttonId, false);
                    return true;
                default:
                    return false;
            }
        });
        return b;
    }

    private void startEmulator()
    {
        try {
            File rom = copyAssetToFiles("game.nes");
            File home = new File(getFilesDir(), "mesen-home");
            if(!home.exists() && !home.mkdirs()) {
                throw new IllegalStateException("无法创建 home 目录");
            }
            if(!emulator.start(home.getAbsolutePath(), rom.getAbsolutePath(), /*enableAudio*/true)) {
                Toast.makeText(this, "ROM 加载失败", Toast.LENGTH_LONG).show();
            }
        } catch(Exception e) {
            Toast.makeText(this, "启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private File copyAssetToFiles(String assetName) throws Exception
    {
        File out = new File(getFilesDir(), assetName);
        try(InputStream in = getAssets().open(assetName);
            FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
        }
        return out;
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        emulator.pause();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if(emulator.isPaused()) {
            emulator.resume();
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        emulator.stop();
    }
}
