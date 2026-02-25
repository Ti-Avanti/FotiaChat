package gg.fotia.chat.listener;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.itemdisplay.ItemDisplayGuiManager;
import gg.fotia.chat.menu.Menu;
import gg.fotia.chat.menu.MenuIcon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.List;

/**
 * 菜单监听器
 */
public class MenuListener implements Listener {

    private final FotiaChat plugin;

    public MenuListener(FotiaChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // 检查是否是物品展示GUI
        if (event.getInventory().getHolder() instanceof ItemDisplayGuiManager.ItemDisplayHolder) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getInventory().getHolder() instanceof Menu menu)) {
            return;
        }

        // 取消默认行为
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= menu.getSize()) {
            return;
        }

        MenuIcon icon = menu.getIconAt(slot);
        if (icon == null) {
            return;
        }

        // 获取点击类型
        String clickType = getClickTypeName(event.getClick());
        List<String> actions = icon.getActions(clickType);

        if (!actions.isEmpty()) {
            plugin.getMenuManager().executeActions(player, actions);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
        // 物品展示GUI也禁止拖拽
        if (event.getInventory().getHolder() instanceof ItemDisplayGuiManager.ItemDisplayHolder) {
            event.setCancelled(true);
        }
    }

    private String getClickTypeName(ClickType clickType) {
        return switch (clickType) {
            case LEFT -> "left";
            case RIGHT -> "right";
            case SHIFT_LEFT -> "shift_left";
            case SHIFT_RIGHT -> "shift_right";
            case MIDDLE -> "middle";
            case DROP -> "drop";
            case CONTROL_DROP -> "ctrl_drop";
            case DOUBLE_CLICK -> "double";
            default -> "left";
        };
    }
}
