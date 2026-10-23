package mesen.interfaces;

/**
 * 键盘/输入管理器，对应 Core 的 {@code IKeyManager}。
 */
public interface IKeyManager
{
    void refreshState();
    void updateDevices();

    /** 鼠标按键是否按下（0=左，1=右，2=中，3/4=附加）。 */
    boolean isMouseButtonPressed(int button);

    /** 某个键码是否按下。 */
    boolean isKeyPressed(int keyCode);

    /** 当前按下的键码数组。 */
    int[] getPressedKeys();

    /** 键码 -> 键名。 */
    String getKeyName(int keyCode);

    /** 键名 -> 键码。 */
    int getKeyCode(String keyName);

    /** 程序化设置按键状态。 */
    boolean setKeyState(int scanCode, boolean state);

    /** 复位所有按键。 */
    void resetKeyState();

    /** 启用/禁用输入。 */
    void setDisabled(boolean disabled);
}
