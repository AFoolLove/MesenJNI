package mesen;

/**
 * 通知监听器，对应 Core 的 {@code INotificationListener}。
 *
 * <p>通过 {@link MesenCore#registerNotificationListener(NotificationListener)} 注册后，
 * 模拟器在发生事件（游戏加载/暂停/停止、帧完成等）时会回调
 * {@link #processNotification(int)}。</p>
 *
 * <p><b>线程</b>：回调运行在 Core 的内部线程（非 EDT），实现里不要直接操作 Swing 组件，
 * 需要时用 {@code SwingUtilities.invokeLater} 转回 EDT。</p>
 *
 * <p>示例：</p>
 * <pre>
 * MesenCore.registerNotificationListener(new NotificationListener() {
 *     public void processNotification(int type) {
 *         switch(type) {
 *             case Mesen.NOTIFICATION_GAME_LOADED:
 *                 System.out.println("游戏已加载");
 *                 break;
 *             case Mesen.NOTIFICATION_EMULATION_STOPPED:
 *                 System.out.println("模拟已停止");
 *                 break;
 *         }
 *     }
 * });
 * </pre>
 */
public interface NotificationListener
{
    /**
     * 收到一条通知。
     *
     * @param type 通知类型，取值为 {@link Mesen} 里的 {@code NOTIFICATION_*} 常量。
     */
    void processNotification(int type);
}
