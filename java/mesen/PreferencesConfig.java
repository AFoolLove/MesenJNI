package mesen;

/**
 * 偏好设置，对应 Core 的 {@code PreferencesConfig}。
 *
 * <p>三个目录覆盖项为空字符串时表示「用默认值」。</p>
 */
public class PreferencesConfig
{
    public boolean ShowFps;
    public boolean ShowFrameCounter;
    public boolean ShowGameTimer;
    public boolean ShowLagCounter;
    public boolean ShowDebugInfo;
    public boolean DisableOsd;

    /** 窗口失焦时是否仍接受输入。 */
    public boolean AllowBackgroundInput;

    public boolean PauseOnMovieEnd;
    public boolean ShowMovieIcons;
    public boolean ShowTurboRewindIcons;
    public boolean DisableGameSelectionScreen;

    /** {@code Mesen.HUD_SIZE_FIXED} 或 {@code Mesen.HUD_SIZE_SCALED}。 */
    public int HudSize;

    /** 自动存档间隔（秒）。 */
    public int AutoSaveStateDelay = 5;

    /** 倒带缓冲大小（帧）。 */
    public int RewindBufferSize = 300;

    // 注意：core 的 PreferencesConfig 还有 3 个 const char* 目录覆盖项
    // （SaveFolderOverride / SaveStateFolderOverride / ScreenshotFolderOverride）。
    // 它们是指针字段，SetPreferences 只做整体赋值、不拷贝字符串，从 Java 传进去
    // 会悬空，所以这里不暴露；要改目录用 MesenConfig 的对应接口。

    @Override
    public String toString()
    {
        return "PreferencesConfig[ShowFps=" + ShowFps + ", AllowBackgroundInput=" + AllowBackgroundInput
            + ", RewindBufferSize=" + RewindBufferSize + "]";
    }
}
