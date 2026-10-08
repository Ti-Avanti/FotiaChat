package gg.fotia.chat.menu.futureui;

import java.util.UUID;

/** 玩家独立的浏览状态；颜色设置仍由 ColorManager 保存。 */
final class ColorMenuSession {
    final UUID token = UUID.randomUUID();
    final boolean compact;
    int page;
    String filter = "all";
    String feedback = "preview";

    ColorMenuSession(boolean compact) { this.compact = compact; }
}
