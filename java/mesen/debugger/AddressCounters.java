package mesen.debugger;

/**
 * 单个地址的访问计数，对应 Core 的 {@code AddressCounters}。
 */
public class AddressCounters
{
    /** 最后一次读取的时间戳。 */
    public long readStamp;

    /** 最后一次写入的时间戳。 */
    public long writeStamp;

    /** 最后一次执行的时间戳。 */
    public long execStamp;

    /** 读取次数。 */
    public int readCounter;

    /** 写入次数。 */
    public int writeCounter;

    /** 执行次数。 */
    public int execCounter;

    @Override
    public String toString()
    {
        return String.format("AddressCounters[read=%d, write=%d, exec=%d]",
            readCounter, writeCounter, execCounter);
    }
}
