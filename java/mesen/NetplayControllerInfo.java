package mesen;

/** 网表对战里的控制器端口标识，对应 Core 的 {@code NetplayControllerInfo}。 */
public class NetplayControllerInfo
{
    public int port;
    public int subPort;

    public NetplayControllerInfo()
    {
    }

    public NetplayControllerInfo(int port, int subPort)
    {
        this.port = port;
        this.subPort = subPort;
    }

    @Override
    public String toString()
    {
        return "NetplayControllerInfo[port=" + port + ", subPort=" + subPort + "]";
    }
}