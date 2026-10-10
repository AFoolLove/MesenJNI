package com.example.mesenjni;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/** 等比例居中显示 Mesen 帧（软件渲染）。 */
public class MesenView extends View
{
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect dst = new Rect();
    private volatile Bitmap frame;

    public MesenView(Context c)
    {
        super(c);
    }

    public MesenView(Context c, AttributeSet a)
    {
        super(c, a);
    }

    /** 可在 core 的渲染线程调用：换帧并请求重绘。 */
    public void setFrame(Bitmap bitmap)
    {
        frame = bitmap;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);
        Bitmap b = frame;
        if(b == null) {
            canvas.drawColor(0xFF000000);
            return;
        }
        float scale = Math.min((float)getWidth() / b.getWidth(), (float)getHeight() / b.getHeight());
        int w = Math.round(b.getWidth() * scale);
        int h = Math.round(b.getHeight() * scale);
        int left = (getWidth() - w) / 2;
        int top = (getHeight() - h) / 2;
        dst.set(left, top, left + w, top + h);
        canvas.drawBitmap(b, null, dst, paint);
    }
}
