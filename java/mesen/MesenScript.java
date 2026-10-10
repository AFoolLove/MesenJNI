package mesen;

/**
 * Lua 脚本。
 */
public final class MesenScript
{
    static {
        System.loadLibrary("MesenCore");
    }

    private MesenScript() {
    }

    /**
     * 加载并执行一段 Mesen Lua 脚本（源码全文）。
     *
     * @param name     脚本名（一般传文件名）
     * @param path     脚本所在目录（脚本内相对路径访问用）
     * @param content  Lua 源码全文
     * @param scriptId -1 表示新建脚本；传已有 id 则替换该脚本
     * @return 脚本 id（失败返回负数）
     */
    public static native int loadScript(String name, String path, String content, int scriptId);

    /** 卸载指定 id 的 Lua 脚本。 */
    public static native void removeScript(int scriptId);

    /** 取指定脚本的日志输出（print 等）。 */
    public static native String getScriptLog(int scriptId);
}
