package gg.fotia.chat.menu;

/**
 * 菜单动作类型枚举
 */
public enum MenuAction {

    /**
     * 关闭菜单
     */
    CLOSE,

    /**
     * 播放音效
     */
    SOUND,

    /**
     * 执行命令（玩家）
     */
    COMMAND,

    /**
     * 执行控制台命令
     */
    CONSOLE_COMMAND,

    /**
     * 发送消息
     */
    MESSAGE,

    /**
     * 打开另一个菜单
     */
    OPEN_MENU,

    /**
     * 设置聊天颜色
     */
    SET_COLOR,

    /**
     * 切换频道
     */
    SWITCH_CHANNEL,

    /**
     * 刷新菜单
     */
    REFRESH
}
