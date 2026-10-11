package mesen;

import mesen.NetplayControllerInfo;

/** 网表对战里一个控制器端口的占用情况，对应 Core 的 {@code NetplayControllerUsageInfo}。 */
public class NetplayControllerUsageInfo
{
    /** 端口标识。 */
    public NetplayControllerInfo port = new NetplayControllerInfo();

    /** 控制器类型（{@code Mesen.CONTROLLER_*}）。 */
    public int type;

    /** 是否已被占用。 */
    public boolean inUse;

    @Override
    public String toString()
    {
        return "NetplayControllerUsageInfo[" + port + ", type=" + type + ", inUse=" + inUse + "]";
    }
}