package mesen;

/**
 * 着色器文件里声明的一个参数定义，对应 Core 的 {@code ShaderParamDefinition}。
 */
public class ShaderParamDefinition
{
    public String name = "";
    public String description = "";
    public double min;
    public double max;
    public double initial;
    public double step;

    @Override
    public String toString()
    {
        return String.format("ShaderParamDefinition[%s, %s, min=%s max=%s initial=%s step=%s]",
            name, description, min, max, initial, step);
    }
}
