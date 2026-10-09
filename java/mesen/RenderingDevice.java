package mesen;

/**
 * Java 实现的渲染设备（对应 Core 的 IRenderingDevice）。
 * 通过 MesenCore.registerRenderingDevice 注册，Core 会把帧回调到这里。
 */
public interface RenderingDevice
{
    /** 收到一帧；argb 为 32-bit ARGB，长度 width*height。 */
    void updateFrame(int width, int height, int[] argb, int frameNumber);

    void clearFrame();

    /** 渲染 HUD 叠加层。emuHud/scriptHud 为 32-bit ARGB。 */
    void render(int[] emuHud, int emuHudWidth, int emuHudHeight, boolean emuHudDirty,
                int[] scriptHud, int scriptHudWidth, int scriptHudHeight, boolean scriptHudDirty);

    void reset();
    void setFullscreenMode(int mode, int width, int height);
}
