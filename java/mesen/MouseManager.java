package mesen;

/**
 * Java 实现的鼠标管理器（对应 Core 的 IMouseManager）。
 * 通过 MesenCore.registerMouseManager 注册。
 */
public interface MouseManager
{
    /** 返回 {x, y, left, right, middle, button4, button5}（1/0）。 */
    int[] getSystemMouseState();

    boolean captureMouse(int x, int y, int width, int height);
    void releaseMouse();
    void setSystemMousePosition(int x, int y);
    void setCursorImage(int cursor);
    double getPixelScale();
}
