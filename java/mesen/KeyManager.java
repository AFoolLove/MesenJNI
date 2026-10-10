package mesen;

import mesen.interfaces.IKeyManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link IKeyManager} 的默认实现：程序化按键状态表。
 *
 * <p>通过 {@link #setKeyState(int, boolean)} 注入按键，再由
 * {@link #isKeyPressed(int)} / {@link #getPressedKeys()} 查询。</p>
 */
public class KeyManager implements IKeyManager
{
    private final ConcurrentHashMap<Integer, Boolean> keys = new ConcurrentHashMap<>();
    private volatile boolean disabled = false;

    @Override
    public void refreshState()
    {
    }

    @Override
    public void updateDevices()
    {
    }

    @Override
    public boolean isMouseButtonPressed(int button)
    {
        return false;
    }

    @Override
    public boolean isKeyPressed(int keyCode)
    {
        return !disabled && Boolean.TRUE.equals(keys.get(keyCode));
    }

    @Override
    public int[] getPressedKeys()
    {
        return keys.entrySet().stream()
            .filter(Map.Entry::getValue)
            .mapToInt(Map.Entry::getKey)
            .toArray();
    }

    @Override
    public String getKeyName(int keyCode)
    {
        return "";
    }

    @Override
    public int getKeyCode(String keyName)
    {
        return 0;
    }

    @Override
    public boolean setKeyState(int scanCode, boolean state)
    {
        if(disabled) {
            return false;
        }
        if(state) {
            keys.put(scanCode, true);
        } else {
            keys.remove(scanCode);
        }
        return true;
    }

    @Override
    public void resetKeyState()
    {
        keys.clear();
    }

    @Override
    public void setDisabled(boolean disabled)
    {
        this.disabled = disabled;
    }
}
