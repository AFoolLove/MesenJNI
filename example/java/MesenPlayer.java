import mesen.AudioDevice;
import mesen.KeyManager;
import mesen.Mesen;
import mesen.MesenConfig;
import mesen.MesenCore;
import mesen.NotificationListener;
import mesen.RenderingDevice;
import mesen.RomInfo;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 桌面 Java 示例：Swing 窗口 + 键盘操作 + 音频，全部通过回调接口实现。
 *
 * <p>与 {@code android/} 那份对比着看：{@code mesen.AudioDevice} /
 * {@code mesen.RenderingDevice} 这两个内置实现类用的是 Java SE 的
 * {@code javax.sound.sampled} / {@code java.awt.image}，<b>桌面能直接用</b>，
 * 而 Android 上要自己实现。</p>
 *
 * <p>编译 / 运行（Windows，工作目录为工程根）：</p>
 * <pre>
 * javac -encoding UTF-8 -cp "lib/gson-2.9.0.jar" -d build/classes \
 *       java/mesen/*.java java/mesen/interfaces/*.java example/java/*.java
 * java -Djava.library.path=bin/win-x64/Release \
 *      -cp "build/classes;lib/gson-2.9.0.jar" MesenPlayer roms/game.nes
 * </pre>
 *
 * <p>操作：方向键 = 十字键，X = A，Z = B，Enter = Start，Shift = Select。</p>
 */
public class MesenPlayer
{
    /** 键盘按键 -> NES 手柄按钮。 */
    private static final int[][] BINDINGS = {
        { KeyEvent.VK_UP,    Mesen.BUTTON_UP },
        { KeyEvent.VK_DOWN,  Mesen.BUTTON_DOWN },
        { KeyEvent.VK_LEFT,  Mesen.BUTTON_LEFT },
        { KeyEvent.VK_RIGHT, Mesen.BUTTON_RIGHT },
        { KeyEvent.VK_X,     Mesen.BUTTON_A },
        { KeyEvent.VK_Z,     Mesen.BUTTON_B },
        { KeyEvent.VK_ENTER, Mesen.BUTTON_START },
        { KeyEvent.VK_SHIFT, Mesen.BUTTON_SELECT },
    };

    // 四个内置实现类：通知 / 渲染 / 音频 / 输入
    private final NotificationListener events = new NotificationListener();
    private final RenderingDevice renderer = new RenderingDevice();
    private final AudioDevice audio = new AudioDevice();
    private final KeyManager keys = new KeyManager();

    private final VideoPanel video = new VideoPanel();
    private final JLabel status = new JLabel("未加载 ROM");
    private final JButton pauseButton = new JButton("暂停");

    private final String homeFolder = new File("./home").getAbsolutePath();

    public static void main(String[] args)
    {
        String rom = args.length > 0 ? args[0] : null;
        SwingUtilities.invokeLater(() -> new MesenPlayer(rom));
    }

    public MesenPlayer(String romPath)
    {
        startCore();
        buildUi();

        if(romPath != null) {
            loadRom(romPath);
        } else {
            chooseRom();
        }
    }

    // ---- 核心初始化 ------------------------------------------------------

    private void startCore()
    {
        MesenCore.initDll();
        // headless：softwareRenderer=true（有帧输出），noAudio=false（要声音），
        // noVideo=false（要视频），noInput=true（输入由 Java 侧提供）
        MesenCore.initializeEmu(homeFolder, /*softwareRenderer*/true,
            /*noAudio*/false, /*noVideo*/false, /*noInput*/true);

        // 先把输入配好，再注册/加载
        configureInput();

        MesenCore.registerNotificationListener(events);
        MesenCore.registerRenderingDevice(renderer);
        MesenCore.registerAudioDevice(audio);
        MesenCore.registerKeyManager(keys);
    }

    /**
     * 配置手柄：<b>先指定控制器类型，再建立「按键 -> 键码」映射</b>。
     * 只设映射是不够的——类型不设的话核不会创建手柄设备，按键完全没反应。
     */
    private static void configureInput()
    {
        MesenConfig.setControllerType(Mesen.CONSOLE_NES, 0, Mesen.CONTROLLER_NES);
        for(int[] b : BINDINGS) {
            MesenConfig.setControllerKey(Mesen.CONSOLE_NES, 0, b[1], b[0]);
        }
    }

    // ---- UI --------------------------------------------------------------

    private void buildUi()
    {
        pauseButton.setEnabled(false);
        pauseButton.addActionListener(e -> togglePause());

        JButton loadButton = new JButton("加载 ROM...");
        loadButton.addActionListener(e -> chooseRom());

        JButton saveStateButton = new JButton("保存存档");
        saveStateButton.addActionListener(e ->
            MesenCore.saveStateFile(new File(homeFolder, "quicksave.mst").getAbsolutePath()));

        JButton screenshotButton = new JButton("截图");
        screenshotButton.addActionListener(e -> MesenCore.takeScreenshot());

        JPanel controls = new JPanel(new FlowLayout());
        controls.add(loadButton);
        controls.add(pauseButton);
        controls.add(saveStateButton);
        controls.add(screenshotButton);

        JFrame frame = new JFrame("Mesen 桌面示例");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(status, BorderLayout.NORTH);
        frame.add(video, BorderLayout.CENTER);
        frame.add(controls, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        frame.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                shutdown();
            }
        });

        // 键盘 -> IKeyManager：按下/抬起直接改键状态
        video.setFocusable(true);
        video.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                setKey(e.getKeyCode(), true);
            }

            @Override
            public void keyReleased(KeyEvent e)
            {
                setKey(e.getKeyCode(), false);
            }
        });

        // 60fps 重绘：把最新帧画到面板上
        video.setRenderer(renderer);
        new Timer(1000 / 60, e -> {
            video.repaint();
            updateStatus();
        }).start();
    }

    private void setKey(int keyCode, boolean pressed)
    {
        for(int[] b : BINDINGS) {
            if(b[0] == keyCode) {
                keys.setKeyState(keyCode, pressed);
                return;
            }
        }
    }

    private void updateStatus()
    {
        if(!MesenCore.isRunning()) {
            return;
        }
        status.setText(String.format("%s | %d 帧 | 通知 %d 条(最后 %d) | 停止码 %d",
            MesenCore.isPaused() ? "已暂停" : "运行中",
            renderer.getFrameNumber(),
            events.getCount(), events.getLastType(),
            MesenCore.getStopCode()));
    }

    // ---- ROM / 生命周期 --------------------------------------------------

    private void chooseRom()
    {
        JFileChooser chooser = new JFileChooser();
        if(chooser.showOpenDialog(video) == JFileChooser.APPROVE_OPTION) {
            loadRom(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void loadRom(String path)
    {
        if(!MesenCore.loadRom(path, null)) {
            JOptionPane.showMessageDialog(video, "加载 ROM 失败: " + path,
                "错误", JOptionPane.ERROR_MESSAGE);
            status.setText("加载失败");
            return;
        }

        RomInfo info = MesenCore.getRomInfo();
        status.setText(String.format("已加载: %s | 机种 %d | 格式 %d | 数据库 ID %d",
            new File(info.romPath).getName(), info.consoleType, info.format, info.databaseId));

        pauseButton.setEnabled(true);
        pauseButton.setText("暂停");
        video.requestFocusInWindow();
        MesenCore.resume();
    }

    private void togglePause()
    {
        if(MesenCore.isPaused()) {
            MesenCore.resume();
            pauseButton.setText("暂停");
        } else {
            MesenCore.pause();
            pauseButton.setText("继续");
        }
    }

    private void shutdown()
    {
        MesenCore.stop();
        audio.close();
        MesenCore.release();
        System.exit(0);
    }

    /** 等比居中显示渲染回调送来的帧。 */
    private static class VideoPanel extends JPanel
    {
        private RenderingDevice device;

        VideoPanel()
        {
            setPreferredSize(new Dimension(512, 480));
            setBackground(Color.BLACK);
        }

        void setRenderer(RenderingDevice device)
        {
            this.device = device;
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, getWidth(), getHeight());

            if(device == null) {
                return;
            }
            BufferedImage frame = device.getBufferedImage();
            if(frame == null) {
                return;
            }
            double scale = Math.min((double)getWidth() / frame.getWidth(),
                                    (double)getHeight() / frame.getHeight());
            int w = (int)(frame.getWidth() * scale);
            int h = (int)(frame.getHeight() * scale);
            g.drawImage(frame, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
        }
    }
}
