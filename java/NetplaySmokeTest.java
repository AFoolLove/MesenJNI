import mesen.Mesen;
import mesen.MesenCore;
import mesen.NetplayControllerInfo;
import mesen.NetplayControllerUsageInfo;

/**
 * 网表对战接口回归测试。
 *
 * <p>开一个本地服务器验证状态位，再连一个不存在的地址确认不会崩。单独一个文件，
 * 避免网络操作影响其它用例。</p>
 */
public class NetplaySmokeTest
{
    private static int failures = 0;

    private static void check(boolean ok, String message)
    {
        System.out.println((ok ? "  [OK] " : "  [FAIL] ") + message);
        if(!ok) {
            failures++;
        }
    }

    public static void main(String[] args) throws Exception
    {
        if(!new java.io.File("roms/MetalMax.nes").isFile()) {
            System.out.println("NETPLAY SMOKE TEST SKIPPED (缺少 roms/MetalMax.nes)");
            System.exit(0);
            return;
        }
        MesenCore.initDll();
        MesenCore.initializeEmu("./home", true, true, false, true);
        check(MesenCore.loadRom("roms/MetalMax.nes", null), "loadRom()");
        MesenCore.resume();
        Thread.sleep(300);

        System.out.println("== 初始状态 ==");
        check(!MesenCore.isServerRunning(), "初始未开服务器");
        check(!MesenCore.isConnected(), "初始未连接");

        System.out.println("== 服务器 ==");
        MesenCore.startServer(0, "");
        Thread.sleep(500);
        check(MesenCore.isServerRunning(), "startServer() 后 isServerRunning = true");

        NetplayControllerUsageInfo[] controllers = MesenCore.netplayGetControllerList();
        StringBuilder listText = new StringBuilder();
        if(controllers != null) {
            for(NetplayControllerUsageInfo controller : controllers) {
                listText.append(controller).append(' ');
            }
        }
        check(controllers != null, "netplayGetControllerList() -> [" + listText.toString().trim() + "]");

        NetplayControllerInfo hostPort = MesenCore.netplayGetControllerPort();
        check(hostPort != null, "netplayGetControllerPort() -> " + hostPort);

        MesenCore.netplaySelectController(new NetplayControllerInfo(0, 0));
        NetplayControllerInfo afterSelect = MesenCore.netplayGetControllerPort();
        check(afterSelect != null, "netplaySelectController(0,0) 后 -> " + afterSelect);

        MesenCore.stopServer();
        Thread.sleep(500);
        check(!MesenCore.isServerRunning(), "stopServer() 后 isServerRunning = false");

        System.out.println("== 客户端 ==");
        // 连一个不存在的服务器：只验证状态位与不崩
        MesenCore.connect("127.0.0.1", 1, "", false);
        Thread.sleep(400);
        check(!MesenCore.isConnected(), "连接不存在的服务器 -> isConnected = false");
        MesenCore.disconnect();
        check(!MesenCore.isConnected(), "disconnect() 后 isConnected = false");

        MesenCore.stop();
        MesenCore.release();

        if(failures == 0) {
            System.out.println("NETPLAY SMOKE TEST PASSED");
        } else {
            System.out.println("NETPLAY SMOKE TEST FAILED (" + failures + " 项)");
            System.exit(1);
        }
        System.exit(0);
    }
}
