package mesen.debugger;

/** 查看器渲染缓冲区的尺寸。 */
public class ViewerSize
{
    public int width;
    public int height;

    @Override
    public String toString()
    {
        return String.format("ViewerSize[%dx%d]", width, height);
    }
}
