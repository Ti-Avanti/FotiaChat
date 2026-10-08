package gg.fotia.chat.menu;

import org.bukkit.entity.Player;

/** 隔离可选显示引擎，未安装 FutureUI 时不加载其 API 类型。 */
public interface ColorMenuBridge extends AutoCloseable {
    enum OpenResult { OPENED, REJECTED, UNAVAILABLE }
    OpenResult open(Player player);
    @Override void close();
}
