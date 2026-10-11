package mesen;

/**
 * 快捷键绑定，对应 Core 的 {@code ShortcutKeyInfo}。
 *
 * <p>{@link #shortcut} 取 {@code Mesen.SHORTCUT_*}；按键码 0 表示未绑定。</p>
 */
public class ShortcutKeyInfo
{
    /** 快捷键（{@code Mesen.SHORTCUT_*}）。 */
    public int shortcut;

    public int key1;
    public int key2;
    public int key3;

    public ShortcutKeyInfo()
    {
    }

    public ShortcutKeyInfo(int shortcut, int key1)
    {
        this.shortcut = shortcut;
        this.key1 = key1;
    }

    @Override
    public String toString()
    {
        return "ShortcutKeyInfo[shortcut=" + shortcut + ", keys=" + key1 + "," + key2 + "," + key3 + "]";
    }
}
