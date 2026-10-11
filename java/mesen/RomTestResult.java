package mesen;

/** 录制的测试运行结果，对应 Core 的 {@code RomTestResult}。 */
public class RomTestResult
{
    /** {@code Mesen.ROM_TEST_*}。 */
    public int state;

    /** 错误码。 */
    public int errorCode;

    /** 最后一帧的哈希。 */
    public String lastFrameHash = "";

    @Override
    public String toString()
    {
        return "RomTestResult[state=" + state + ", errorCode=" + errorCode
            + ", lastFrameHash=" + lastFrameHash + "]";
    }
}