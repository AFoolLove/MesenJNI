package mesen.interfaces;

/**
 * 通知监听器，对应 Core 的 {@code INotificationListener}。
 *
 * <p>通过 {@link mesen.MesenCore#registerNotificationListener(INotificationListener)}
 * 注册后，模拟器在发生事件时会回调 {@link #processNotification(int)}。
 * 事件类型见 {@link mesen.Mesen} 的 {@code NOTIFICATION_*} 常量。</p>
 *
 * <p>线程：回调运行在 Core 内部线程（非 EDT）。</p>
 */
public interface INotificationListener
{
    /** 收到一条通知，type 为 ConsoleNotificationType 的序数。 */
    void processNotification(int type);
}
