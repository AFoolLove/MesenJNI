package mesen;

/**
 * 作弊码（输入形式），对应 Core 的 {@code CheatCode}。
 *
 * <p>{@link #type} 取 {@code Mesen.CHEAT_*}。</p>
 */
public class CheatCode
{
    /** 作弊码类型（{@code Mesen.CHEAT_*}）。 */
    public int type;

    /** 作弊码文本（core 那边缓冲只有 16 字节，超长会被截断）。 */
    public String code = "";

    public CheatCode()
    {
    }

    public CheatCode(int type, String code)
    {
        this.type = type;
        this.code = code;
    }

    @Override
    public String toString()
    {
        return "CheatCode[type=" + type + ", code=" + code + "]";
    }
}
