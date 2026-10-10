package mesen;

/**
 * 键盘/输入管理器，对应 Core 的 {@code IKeyManager}。
 *
 * <p>通过 {@link MesenCore#registerKeyManager(KeyManager)} 注册后，模拟器的按键状态由
 * 这个实现提供，适合做程序化输入（无窗口/无 OS 键盘时的自定义输入源）。</p>
 *
 * <p>所有方法都有默认实现（无操作 / 空 / false），只需覆盖你关心的方法即可。</p>
 *
 * <p><b>键码</b>：keyCode 是模拟器内部的键码（scan code），由 {@link MesenCore#getKeyCode(String)}
 * 和 {@link MesenCore#getKeyName(int)} 在键名与键码之间转换；控制器按键映射用
 * {@link MesenCore#setControllerKey(int, int, int, int)} 配置。</p>
 *
 * <p><b>线程</b>：回调运行在 Core 的内部线程。</p>
 */
public interface KeyManager
{
    /** 刷新按键状态（每帧会被调用）。 */
    default void refreshState() {}

    /** 刷新输入设备列表。 */
    default void updateDevices() {}

    /**
     * 鼠标按键是否按下。
     *
     * @param button 鼠标按键编号（0=左键，1=右键，2=中键，3/4=附加键）
     */
    default boolean isMouseButtonPressed(int button) { return false; }

    /** 某个键码当前是否按下。 */
    default boolean isKeyPressed(int keyCode) { return false; }

    /** 返回当前按下的键码数组。 */
    default int[] getPressedKeys() { return new int[0]; }

    /** 键码 -> 键名。 */
    default String getKeyName(int keyCode) { return ""; }

    /** 键名 -> 键码。 */
    default int getKeyCode(String keyName) { return 0; }

    /**
     * 程序化设置某个键的按下状态（供无头输入注入）。
     *
     * @return 是否被模拟器接受（用于触发快捷键处理）
     */
    default boolean setKeyState(int scanCode, boolean state) { return false; }

    /** 复位所有按键状态。 */
    default void resetKeyState() {}

    /** 启用/禁用输入（disabled 时忽略输入）。 */
    default void setDisabled(boolean disabled) {}
}
