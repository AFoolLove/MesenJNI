package mesen;

/**
 * 鼠标管理器，对应 Core 的 {@code IMouseManager}。
 *
 * <p>通过 {@link MesenCore#registerMouseManager(MouseManager)} 注册后，模拟器的鼠标状态
 * 与光标/指针行为由这个实现提供。主要用于光枪、鼠标类控制器，以及系统鼠标指针控制。</p>
 *
 * <p><b>线程</b>：回调运行在 Core 的内部线程。</p>
 */
public interface MouseManager
{
    /**
     * 返回系统鼠标状态，数组 7 个元素：
     * {@code [x, y, left, right, middle, button4, button5]}（按键用 1/0 表示）。
     */
    int[] getSystemMouseState();

    /**
     * 捕获鼠标到指定区域。
     *
     * @return 是否捕获成功
     */
    boolean captureMouse(int x, int y, int width, int height);

    /** 释放鼠标捕获。 */
    void releaseMouse();

    /** 设置系统鼠标位置。 */
    void setSystemMousePosition(int x, int y);

    /** 设置光标样式（0=隐藏，1=箭头，2=十字）。 */
    void setCursorImage(int cursor);

    /** 返回屏幕缩放比例（高 DPI 下通常 &gt; 1.0）。 */
    double getPixelScale();
}
