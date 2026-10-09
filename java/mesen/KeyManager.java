package mesen;

/**
 * Java 实现的键盘/输入管理器（对应 Core 的 IKeyManager）。
 * 通过 MesenCore.registerKeyManager 注册。
 */
public interface KeyManager
{
    void refreshState();
    void updateDevices();
    boolean isMouseButtonPressed(int button);
    boolean isKeyPressed(int keyCode);
    int[] getPressedKeys();
    String getKeyName(int keyCode);
    int getKeyCode(String keyName);
    boolean setKeyState(int scanCode, boolean state);
    void resetKeyState();
    void setDisabled(boolean disabled);
}
