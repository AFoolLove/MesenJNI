package mesen;

/** 着色器配置，对应 Core 的 {@code InteropShaderConfig}。 */
public class ShaderConfig
{
    /** 配置版本号。 */
    public int configVersion;

    /** 着色器文件路径（空 = 不使用着色器）。 */
    public String shaderFile = "";

    /** 参数值列表。 */
    public ShaderParam[] params = new ShaderParam[0];

    @Override
    public String toString()
    {
        return "ShaderConfig[v" + configVersion + ", file=" + shaderFile
            + ", params=" + (params == null ? 0 : params.length) + "]";
    }
}
