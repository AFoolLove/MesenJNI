import mesen.KeyManager;
import mesen.Mesen;
import mesen.MesenConfig;
import mesen.MesenCore;

/** 验证按键注入是否能传到游戏中（与 Android 示例相同的映射方式）。 */
public class InputVerifyTest
{
    public static void main(String[] args) throws Exception
    {
        MesenCore.initDll();
        MesenCore.initializeEmu("./home", true, true, false, true);

        KeyManager km = new KeyManager();
        MesenCore.registerKeyManager(km);

        // 不显式指定手柄类型，验证核的兜底（游戏不在 DB 里时默认标准手柄）
        // MesenConfig.setControllerType(Mesen.CONSOLE_NES, 0, Mesen.CONTROLLER_NES);
        // MesenConfig.setControllerType(Mesen.CONSOLE_NES, 1, Mesen.CONTROLLER_NES);

        // 和 Android 示例一致：按钮 -> 虚拟键码
        MesenConfig.setControllerKey(Mesen.CONSOLE_NES, 0, Mesen.BUTTON_A, 1);
        MesenConfig.setControllerKey(Mesen.CONSOLE_NES, 0, Mesen.BUTTON_B, 2);
        MesenConfig.setControllerKey(Mesen.CONSOLE_NES, 0, Mesen.BUTTON_START, 3);
        MesenConfig.setControllerKey(Mesen.CONSOLE_NES, 0, Mesen.BUTTON_SELECT, 4);
        MesenConfig.setControllerKey(Mesen.CONSOLE_NES, 0, Mesen.BUTTON_UP, 5);
        MesenConfig.setControllerKey(Mesen.CONSOLE_NES, 0, Mesen.BUTTON_DOWN, 6);
        MesenConfig.setControllerKey(Mesen.CONSOLE_NES, 0, Mesen.BUTTON_LEFT, 7);
        MesenConfig.setControllerKey(Mesen.CONSOLE_NES, 0, Mesen.BUTTON_RIGHT, 8);

        MesenCore.loadRom("roms/MetalMax.nes", null);
        MesenCore.resume();
        Thread.sleep(1500);

        System.out.println(">>> press A (key 1)");
        km.setKeyState(1, true);
        Thread.sleep(1200);
        System.out.println(">>> release A");
        km.setKeyState(1, false);
        Thread.sleep(300);

        System.out.println("pressed keys now: " + java.util.Arrays.toString(km.getPressedKeys()));
        System.out.println("--- core log ---");
        System.out.println(MesenCore.getLog());
        MesenCore.stop();
        MesenCore.release();
        System.exit(0);
    }
}
