package mesen.interfaces;

/**
 * 鼠标管理器，对应 Core 的 {@code IMouseManager}。
 */
public interface IMouseManager
{
    /** 返回 {@code [x, y, left, right, middle, button4, button5]}。 */
    int[] getSystemMouseState();

    /** 捕获鼠标到指定区域。 */
    boolean captureMouse(int x, int y, int width, int height);

    /** 释放鼠标捕获。 */
    void releaseMouse();

    /** 设置系统鼠标位置。 */
    void setSystemMousePosition(int x, int y);

    /** 设置光标样式（0=隐藏，1=箭头，2=十字）。 */
    void setCursorImage(int cursor);

    /** 屏幕缩放比例。 */
    double getPixelScale();
}
