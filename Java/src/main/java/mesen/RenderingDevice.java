package mesen;

import mesen.interfaces.IRenderingDevice;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * {@link IRenderingDevice} 的默认实现：把最新帧拷贝到 {@link BufferedImage}。
 */
public class RenderingDevice implements IRenderingDevice
{
    private BufferedImage image;
    private int[] pixels;
    private volatile int frameNumber = -1;

    @Override
    public void updateFrame(int width, int height, int[] argb, int frameNumber)
    {
        if(image == null || width != image.getWidth() || height != image.getHeight()) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            pixels = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
        }
        System.arraycopy(argb, 0, pixels, 0, width * height);
        this.frameNumber = frameNumber;
    }

    @Override
    public void clearFrame()
    {
    }

    @Override
    public void render(int[] emuHud, int emuHudWidth, int emuHudHeight, boolean emuHudDirty,
                       int[] scriptHud, int scriptHudWidth, int scriptHudHeight, boolean scriptHudDirty)
    {
    }

    @Override
    public void reset()
    {
    }

    @Override
    public void setFullscreenMode(int mode, int width, int height)
    {
    }

    /** 最新帧（{@code TYPE_INT_ARGB}）；还没有帧时为 {@code null}。 */
    public BufferedImage getBufferedImage()
    {
        return image;
    }

    /** 最新帧序号（{@code -1} 表示还没有帧）。 */
    public int getFrameNumber()
    {
        return frameNumber;
    }
}
