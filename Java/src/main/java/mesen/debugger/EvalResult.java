package mesen.debugger;

/**
 * 表达式求值结果，对应 Core 的 {@code EvalResultType} + 返回值。
 */
public class EvalResult
{
    /** 求值结果。 */
    public long value;

    /** 结果类型（{@code Mesen.EVAL_*}）；{@code EVAL_INVALID} 表示表达式无效。 */
    public int type;

    /** 表达式是否求值成功。 */
    public boolean isValid()
    {
        return type == mesen.Mesen.EVAL_NUMERIC || type == mesen.Mesen.EVAL_BOOLEAN;
    }

    @Override
    public String toString()
    {
        return String.format("EvalResult[value=%d ($%X), type=%d]", value, value, type);
    }
}
