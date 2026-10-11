package mesen;

/** 着色器参数值（名称 + 值），对应 Core 的 {@code ShaderParam}。 */
public class ShaderParam
{
    /** 参数名（core 那边缓冲 200 字节）。 */
    public String name = "";

    public double value;

    public ShaderParam()
    {
    }

    public ShaderParam(String name, double value)
    {
        this.name = name;
        this.value = value;
    }

    @Override
    public String toString()
    {
        return name + "=" + value;
    }
}
