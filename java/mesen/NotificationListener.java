package mesen;

import mesen.interfaces.INotificationListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link INotificationListener} 的默认实现：把收到的通知收集起来。
 */
public class NotificationListener implements INotificationListener
{
    private final CopyOnWriteArrayList<Integer> events = new CopyOnWriteArrayList<>();
    private volatile int lastType = -1;

    @Override
    public void processNotification(int type)
    {
        lastType = type;
        events.add(type);
    }

    /** 最后一次通知的类型（{@code -1} 表示还没收到）。 */
    public int getLastType()
    {
        return lastType;
    }

    /** 收到的通知总数。 */
    public int getCount()
    {
        return events.size();
    }

    /** 收到的所有通知类型（按时间顺序）。 */
    public List<Integer> getEvents()
    {
        return events;
    }

    /** 清空已收集的通知。 */
    public void clear()
    {
        events.clear();
    }
}
