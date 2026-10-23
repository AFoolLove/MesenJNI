package mesen;

import mesen.interfaces.IMouseManager;

/**
 * {@link IMouseManager} 的默认实现：程序化鼠标状态。
 */
public class MouseManager implements IMouseManager
{
    private final int[] state = new int[7]; // x, y, left, right, middle, b4, b5
    private double pixelScale = 1.0;
    private int cursorImage = 0;

    @Override
    public int[] getSystemMouseState()
    {
        return state.clone();
    }

    @Override
    public boolean captureMouse(int x, int y, int width, int height)
    {
        state[0] = x;
        state[1] = y;
        return true;
    }

    @Override
    public void releaseMouse()
    {
    }

    @Override
    public void setSystemMousePosition(int x, int y)
    {
        state[0] = x;
        state[1] = y;
    }

    @Override
    public void setCursorImage(int cursor)
    {
        this.cursorImage = cursor;
    }

    @Override
    public double getPixelScale()
    {
        return pixelScale;
    }

    /** 设置某个按键状态；index：0=左，1=右，2=中，3=键4，4=键5。 */
    public void setButton(int index, boolean pressed)
    {
        if(index >= 0 && index < 5) {
            state[2 + index] = pressed ? 1 : 0;
        }
    }

    /** 设置屏幕缩放比例。 */
    public void setPixelScale(double scale)
    {
        this.pixelScale = scale;
    }

    /** 当前光标样式。 */
    public int getCursorImage()
    {
        return cursorImage;
    }
}
