import mesen.MesenCore;
import mesen.Mesen;
import mesen.MesenConfig;
import mesen.RomInfo;

import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * 实时预览 MesenCore.copyFrameBuffer() 生成帧画面的 Swing 示例。
 *
 * 用法:
 *   javac -encoding UTF-8 -cp build/classes -d build/classes java/SwingPreview.java
 *   java -Djava.library.path=bin/win-x64/Release -cp build/classes SwingPreview [rom 路径]
 *
 * 不带 ROM 路径时会弹出文件选择框。
 *
 * 要点:
 *   1. 帧是 32-bit ARGB，对应 BufferedImage.TYPE_INT_ARGB。
 *   2. 直接取 BufferedImage 底层 int[] 传给 copyFrameBuffer，实现零拷贝。
 *   3. 音频是双声道交错 int16 PCM，用 readAudioSamples 读回后写入 SourceDataLine 播放。
 *   4. 用 60fps 的 Swing Timer 驱动拷贝 + repaint + 音频推送（repaint 必须在 EDT）。
 */
public class SwingPreview
{
    private final JPanel videoPanel;
    private final JLabel statusLabel;
    private final JButton pauseResumeButton;

    private BufferedImage image;
    private int[] pixels;
    private int frameWidth;
    private int frameHeight;

    private SourceDataLine audioLine;
    private AudioFormat audioFormat;
    private boolean audioEnabled = true;
    private String audioStatus = "音频: 未初始化";
    private long totalSamplesRead = 0;
    private int audioPeak = 0;
    private boolean audioDebugPrinted = false;

    private MesenConfig config;
    private Path configFile;

    public static void main(String[] args)
    {
        String romPath = args.length > 0 ? args[0] : null;
        SwingUtilities.invokeLater(() -> new SwingPreview(romPath));
    }

    public SwingPreview(String romPath)
    {
        // 1) 初始化无头模拟器：软件渲染 + 音频捕获、无 OS 输入
        MesenCore.initDll();
        String homeFolder = new File("./preview-home").getAbsolutePath();
        MesenCore.initializeEmu(homeFolder, /*softwareRenderer*/true, /*noAudio*/false, /*noVideo*/false, /*noInput*/true);

        // 加载并应用配置（不存在则用默认值，含默认音量 100）
        configFile = Path.of(homeFolder, "mesen.json");
        config = MesenConfig.load(configFile);
        config.apply();

        startAudio();

        // 2) 构建 UI
        videoPanel = new JPanel()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, getWidth(), getHeight());
                if(image == null) {
                    return;
                }
                // 等比缩放居中，避免拉伸变形
                double scale = Math.min((double)getWidth() / frameWidth, (double)getHeight() / frameHeight);
                int dw = (int)(frameWidth * scale);
                int dh = (int)(frameHeight * scale);
                int x = (getWidth() - dw) / 2;
                int y = (getHeight() - dh) / 2;
                g.drawImage(image, x, y, dw, dh, null);
            }
        };
        videoPanel.setPreferredSize(new Dimension(512, 480));
        videoPanel.setBackground(Color.BLACK);

        statusLabel = new JLabel("未加载 ROM");

        pauseResumeButton = new JButton("暂停");
        pauseResumeButton.setEnabled(false);
        pauseResumeButton.addActionListener(e -> togglePause());

        JButton loadButton = new JButton("加载 ROM...");
        loadButton.addActionListener(e -> chooseRom());

        JButton screenshotButton = new JButton("截图");
        screenshotButton.addActionListener(e -> MesenCore.takeScreenshot());

        JCheckBox fastForward = new JCheckBox("加速 (MaximumSpeed)");
        fastForward.addActionListener(e ->
            MesenCore.setEmulationFlag(Mesen.FLAG_MAXIMUM_SPEED, fastForward.isSelected()));

        JCheckBox audioToggle = new JCheckBox("音频", true);
        audioToggle.addActionListener(e -> audioEnabled = audioToggle.isSelected());

        JButton saveConfigButton = new JButton("保存配置");
        saveConfigButton.addActionListener(e -> saveConfig());

        JButton defaultsButton = new JButton("恢复默认");
        defaultsButton.addActionListener(e -> {
            config.initializeDefaults();
            config.apply();
            statusLabel.setText("已恢复默认配置（含默认音量 100）");
        });

        JPanel controls = new JPanel(new FlowLayout());
        controls.add(loadButton);
        controls.add(pauseResumeButton);
        controls.add(screenshotButton);
        controls.add(fastForward);
        controls.add(audioToggle);
        controls.add(saveConfigButton);
        controls.add(defaultsButton);

        JFrame frame = new JFrame("Mesen 实时预览 (Swing)");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(statusLabel, BorderLayout.NORTH);
        frame.add(videoPanel, BorderLayout.CENTER);
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

        // 3) 渲染循环：约 60fps 拷贝最新帧并重绘
        Timer timer = new Timer(1000 / 60, e -> renderFrame());
        timer.start();

        // 4) 加载 ROM（命令行参数优先，否则弹文件选择框）
        if(romPath != null) {
            loadRom(romPath);
        } else {
            chooseRom();
        }
    }

    private void renderFrame()
    {
        int w = MesenCore.getFrameWidth();
        int h = MesenCore.getFrameHeight();
        if(w <= 0 || h <= 0) {
            return;
        }

        // 分辨率变化时重建缓冲区（换 ROM / 不同机种）
        if(image == null || w != frameWidth || h != frameHeight) {
            frameWidth = w;
            frameHeight = h;
            image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            pixels = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
        }

        // 零拷贝：直接写入 BufferedImage 底层数组
        MesenCore.copyFrameBuffer(pixels);
        videoPanel.repaint();
        pumpAudio();

        String state = MesenCore.isRunning()
            ? (MesenCore.isPaused() ? "已暂停" : "运行中")
            : "已停止";
        statusLabel.setText(String.format(
            "%s | %dx%d | 停止码: %d | %s | 样本: %d | 峰值: %d",
            state, frameWidth, frameHeight, MesenCore.getStopCode(), audioStatus, totalSamplesRead, audioPeak));
    }

    private void togglePause()
    {
        if(MesenCore.isPaused()) {
            MesenCore.resume();
            pauseResumeButton.setText("暂停");
        } else {
            MesenCore.pause();
            pauseResumeButton.setText("继续");
        }
    }

    private void chooseRom()
    {
        JFileChooser chooser = new JFileChooser();
        if(chooser.showOpenDialog(videoPanel) == JFileChooser.APPROVE_OPTION) {
            loadRom(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void loadRom(String path)
    {
        if(!MesenCore.loadRom(path, null)) {
            JOptionPane.showMessageDialog(
                videoPanel, "加载 ROM 失败: " + path, "错误", JOptionPane.ERROR_MESSAGE);
            statusLabel.setText("加载失败");
            return;
        }

        pauseResumeButton.setEnabled(true);
        pauseResumeButton.setText("暂停");

        RomInfo info = MesenCore.getRomInfo();
        statusLabel.setText(String.format("已加载: %s (机种 %d)", info.romPath, info.consoleType));

        MesenCore.resume();
    }

    private void startAudio()
    {
        // 逐个尝试常见采样率，选一个声卡支持的；并把 core 的输出采样率改成它，
        // 这样音频由模拟器端重采样，Java 侧无需手动重采样。
        int[] rates = { config.sampleRate, 44100, 22050, 16000 };
        for(int rate : rates) {
            try {
                AudioFormat fmt = new AudioFormat(rate, 16, 2, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                audioLine = (SourceDataLine)AudioSystem.getLine(info);
                audioLine.open(fmt, rate * 4); // 约 1 秒缓冲（字节）
                audioLine.start();

                audioFormat = fmt;
                if(rate != config.sampleRate) {
                    config.sampleRate = rate;
                    config.apply(); // 让模拟器按该采样率输出
                }
                audioStatus = "音频 " + rate + "Hz 已开启";
                break;
            } catch(Exception e) {
                audioLine = null;
                audioStatus = "音频不可用: " + e.getMessage();
            }
        }
        System.out.println("[音频] " + audioStatus);
    }

    private void pumpAudio()
    {
        if(audioLine == null || !audioEnabled) {
            return;
        }

        short[] samples = new short[4096];
        int n = MesenCore.readAudioSamples(samples);
        if(n <= 0) {
            return;
        }
        totalSamplesRead += n;

        // 计算峰值，用于判断读到的样本是真实声音还是静音
        int peak = 0;
        for(int i = 0; i < n; i++) {
            int s = samples[i] < 0 ? -samples[i] : samples[i];
            if(s > peak) {
                peak = s;
            }
        }
        if(peak > audioPeak) {
            audioPeak = peak;
        }

        if(!audioDebugPrinted && n >= 20) {
            StringBuilder sb = new StringBuilder("[音频] 前10样本: ");
            for(int i = 0; i < 10; i++) {
                sb.append(samples[i]).append(' ');
            }
            System.out.println(sb);
            audioDebugPrinted = true;
        }

        // 转成小端字节序写入 SourceDataLine
        byte[] bytes = new byte[n * 2];
        for(int i = 0; i < n; i++) {
            bytes[i * 2] = (byte)(samples[i] & 0xFF);
            bytes[i * 2 + 1] = (byte)((samples[i] >> 8) & 0xFF);
        }
        audioLine.write(bytes, 0, bytes.length);
    }

    private void saveConfig()
    {
        try {
            config.save(configFile);
            statusLabel.setText("配置已保存: " + configFile);
        } catch(IOException e) {
            statusLabel.setText("配置保存失败: " + e.getMessage());
        }
    }

    private void shutdown()
    {
        saveConfig(); // 退出时自动保存配置
        if(audioLine != null) {
            audioLine.stop();
            audioLine.close();
        }
        MesenCore.stop();
        MesenCore.release();
        System.exit(0);
    }
}
