package mesen;

/**
 * 键盘/鼠标输入。
 */
public final class MesenInput
{
    static {
        System.loadLibrary("MesenCore");
    }

    private MesenInput() {
    }

    public static native void setKeyState(int scanCode, boolean state);
    public static native void resetKeyState();
    public static native void disableAllKeys(boolean disabled);
    public static native void updateInputDevices();
    public static native int getKeyCode(String keyName);
    public static native String getKeyName(int keyCode);
    public static native void setMousePosition(double x, double y);
    public static native void setMouseMovement(int x, int y);
    public static native boolean hasControlDevice(int controllerType);
}
