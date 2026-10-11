import mesen.Mesen;
import mesen.MesenCore;

/**
 * 录像（AVI / WAV / 操作回放）回归测试。
 *
 * <p>用 MetalMax 跑几秒，各录一小段，检查文件真的生成、状态位正确。</p>
 */
public class RecordSmokeTest
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
        MesenCore.initDll();
        MesenCore.initializeEmu("./home", true, true, false, true);

        check(MesenCore.loadRom("roms/MetalMax.nes", null), "loadRom()");
        MesenCore.resume();
        Thread.sleep(300);

        // ---- AVI ----
        System.out.println("== AVI 录制 ==");
        check(!MesenCore.aviIsRecording(), "初始未在录制");
        MesenCore.aviRecord("temp/record.avi", Mesen.VIDEO_CODEC_ZMBV, 0, false, false);
        check(MesenCore.aviIsRecording(), "aviRecord() 后 isRecording = true");
        Thread.sleep(600);
        MesenCore.aviStop();
        check(!MesenCore.aviIsRecording(), "aviStop() 后 isRecording = false");
        java.io.File avi = new java.io.File("temp/record.avi");
        check(avi.exists() && avi.length() > 0, "AVI 文件 -> "
            + (avi.exists() ? avi.length() + " 字节" : "未生成"));

        // ---- WAV ----
        System.out.println("== WAV 录制 ==");
        check(!MesenCore.waveIsRecording(), "初始未在录制");
        MesenCore.waveRecord("temp/record.wav");
        check(MesenCore.waveIsRecording(), "waveRecord() 后 isRecording = true");
        Thread.sleep(600);
        MesenCore.waveStop();
        check(!MesenCore.waveIsRecording(), "waveStop() 后 isRecording = false");
        java.io.File wav = new java.io.File("temp/record.wav");
        check(wav.exists() && wav.length() > 0, "WAV 文件 -> "
            + (wav.exists() ? wav.length() + " 字节" : "未生成"));

        // ---- Movie 录制 ----
        System.out.println("== 操作回放录制 ==");
        check(!MesenCore.movieRecording() && !MesenCore.moviePlaying(), "初始未录制/未播放");
        MesenCore.movieRecord("temp/record.mmo", "MesenJNI", "自动化测试", Mesen.MOVIE_FROM_CURRENT_STATE);
        Thread.sleep(200);
        check(MesenCore.movieRecording(), "movieRecord() 后 recording = true");
        Thread.sleep(600);
        MesenCore.movieStop();
        Thread.sleep(200);
        check(!MesenCore.movieRecording(), "movieStop() 后 recording = false");
        java.io.File movie = new java.io.File("temp/record.mmo");
        check(movie.exists() && movie.length() > 0, "MMO 文件 -> "
            + (movie.exists() ? movie.length() + " 字节" : "未生成"));

        // ---- Movie 回放 ----
        System.out.println("== 操作回放播放 ==");
        MesenCore.moviePlay("temp/record.mmo");
        Thread.sleep(400);
        check(MesenCore.moviePlaying(), "moviePlay() 后 playing = true");
        MesenCore.movieStop();
        Thread.sleep(200);
        check(!MesenCore.moviePlaying(), "movieStop() 后 playing = false");

        MesenCore.stop();
        MesenCore.release();

        if(failures == 0) {
            System.out.println("RECORD SMOKE TEST PASSED");
        } else {
            System.out.println("RECORD SMOKE TEST FAILED (" + failures + " 项)");
            System.exit(1);
        }
        System.exit(0);
    }
}
