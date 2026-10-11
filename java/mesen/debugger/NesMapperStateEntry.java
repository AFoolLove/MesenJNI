package mesen.debugger;

/**
 * mapper 自定义状态项，对应 Core 的 {@code MapperStateEntry}。
 *
 * <p>{@link #address} / {@link #name} 是固定 40 字节的原始缓冲（只前若干字节有效），
 * 保持 byte[] 形态是为了不改动 core 的表示。</p>
 */
public class NesMapperStateEntry
{
    /** 原始值（{@code Long.MIN_VALUE} 表示未使用）。 */
    public long rawValue = Long.MIN_VALUE;

    /** 值类型（{@code Mesen.MAPPER_STATE_*}）。 */
    public int type;

    public byte[] address = new byte[40];
    public byte[] name = new byte[40];

    @Override
    public String toString()
    {
        return "NesMapperStateEntry[raw=" + rawValue + ", type=" + type + "]";
    }
}