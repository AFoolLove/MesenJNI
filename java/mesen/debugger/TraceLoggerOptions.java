package mesen.debugger;

/**
 * 执行跟踪选项，对应 Core 的 {@code TraceLoggerOptions}。
 *
 * <p>{@link #condition} 是过滤表达式（空 = 全记录），{@link #format} 决定
 * {@link TraceRow#logOutput} 的内容。语法见 Mesen 文档的「Trace Logger」。</p>
 */
public class TraceLoggerOptions
{
    /** 是否开启跟踪。 */
    public boolean enabled;

    /** 是否按调用层级缩进。 */
    public boolean indentCode;

    /** 是否用标签代替地址。 */
    public boolean useLabels;

    /** 过滤条件表达式（空 = 全记录）。 */
    public String condition = "";

    /** 输出格式字符串。 */
    public String format = "";
}
