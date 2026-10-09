package mesen;

/**
 * Java 实现的通知监听器（对应 Core 的 INotificationListener）。
 * 通过 MesenCore.registerNotificationListener 注册。
 */
public interface NotificationListener
{
    /** 收到一条通知，type 为 ConsoleNotificationType 的序数。 */
    void processNotification(int type);
}
