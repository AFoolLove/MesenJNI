package mesen;

/**
 * 渲染设备，对应 Core 的 {@code IRenderingDevice}。
 *
 * <p>通过 {@link MesenCore#registerRenderingDevice(RenderingDevice)} 注册后，模拟器把
 * 渲染好的帧推送到这里，而不是内部软件渲染器。可用于把帧画到 Swing/JavaFX/OpenGL 等。</p>
 *
 * <p><b>格式</b>：帧为 32-bit ARGB（每像素一个 int，顺序 {@code 0xAARRGGBB}）。</p>
 *
 * <p><b>缓冲复用</b>：{@link #updateFrame(int, int, int[], int)} 传入的 {@code argb}
 * 是复用的缓冲池数组，<b>回调返回后内容会被覆盖</b>。必须在方法内消费（拷贝/绘制）完毕，
 * 不要持有引用。</p>
 *
 * <p><b>线程</b>：回调运行在 Core 的渲染线程（非 EDT），操作 Swing 组件需转回 EDT。</p>
 */
public interface RenderingDevice
{
    /**
     * 收到一帧。
     *
     * @param width       帧宽（像素）
     * @param height      帧高（像素）
     * @param argb        32-bit ARGB 像素，长度 width*height（复用缓冲）
     * @param frameNumber 帧序号（从 0 递增）
     */
    void updateFrame(int width, int height, int[] argb, int frameNumber);

    /** 清空当前帧（显示黑屏）。 */
    void clearFrame();

    /**
     * 渲染 HUD 叠加层。
     *
     * @param emuHud       模拟器 HUD（调试信息等）的 32-bit ARGB 缓冲
     * @param emuHudWidth   HUD 宽
     * @param emuHudHeight  HUD 高
     * @param emuHudDirty   HUD 是否有更新
     * @param scriptHud     Lua 脚本 HUD 缓冲
     * @param scriptHudWidth / scriptHudHeight 脚本 HUD 尺寸
     * @param scriptHudDirty 脚本 HUD 是否有更新
     */
    void render(int[] emuHud, int emuHudWidth, int emuHudHeight, boolean emuHudDirty,
                int[] scriptHud, int scriptHudWidth, int scriptHudHeight, boolean scriptHudDirty);

    /** 重置渲染设备。 */
    void reset();

    /**
     * 设置全屏模式。
     *
     * @param mode   0=禁用，1=无边框，2=独占
     * @param width  宽
     * @param height 高
     */
    void setFullscreenMode(int mode, int width, int height);
}
