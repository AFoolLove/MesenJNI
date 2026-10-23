package mesen.interfaces;

/**
 * 渲染设备，对应 Core 的 {@code IRenderingDevice}。
 *
 * <p>注册后模拟器把渲染好的 32-bit ARGB 帧推送到这里。
 * {@code updateFrame} 的 {@code argb} 是复用缓冲，回调返回后会被覆盖，
 * 必须在方法内消费完毕。</p>
 */
public interface IRenderingDevice
{
    /**
     * 收到一帧。
     *
     * @param width       帧宽
     * @param height      帧高
     * @param argb        32-bit ARGB（长度 width*height，复用缓冲）
     * @param frameNumber 帧序号
     */
    void updateFrame(int width, int height, int[] argb, int frameNumber);

    /** 清空当前帧。 */
    void clearFrame();

    /** 渲染 HUD 叠加层（emuHud/scriptHud 为 32-bit ARGB）。 */
    void render(int[] emuHud, int emuHudWidth, int emuHudHeight, boolean emuHudDirty,
                int[] scriptHud, int scriptHudWidth, int scriptHudHeight, boolean scriptHudDirty);

    /** 重置渲染设备。 */
    void reset();

    /** 设置全屏模式（0=禁用，1=无边框，2=独占）。 */
    void setFullscreenMode(int mode, int width, int height);
}
