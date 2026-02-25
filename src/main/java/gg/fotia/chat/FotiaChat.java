package gg.fotia.chat;

import gg.fotia.chat.announcement.AnnouncementManager;
import gg.fotia.chat.api.AddonManager;
import gg.fotia.chat.api.FotiaChatAPI;
import gg.fotia.chat.channel.ChannelManager;
import gg.fotia.chat.color.ColorManager;
import gg.fotia.chat.command.*;
import gg.fotia.chat.crossserver.CrossServerManager;
import gg.fotia.chat.filter.FilterManager;
import gg.fotia.chat.format.ChatFormatter;
import gg.fotia.chat.itemdisplay.ItemDisplayManager;
import gg.fotia.chat.listener.ChatListener;
import gg.fotia.chat.listener.MenuListener;
import gg.fotia.chat.listener.PlayerListener;
import gg.fotia.chat.manager.ConfigManager;
import gg.fotia.chat.manager.MessageManager;
import gg.fotia.chat.menu.MenuManager;
import gg.fotia.chat.mute.MuteManager;
import gg.fotia.chat.privatemsg.PrivateMessageManager;
import gg.fotia.chat.storage.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

public class FotiaChat extends JavaPlugin {

    private static FotiaChat instance;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private ChannelManager channelManager;
    private ChatFormatter chatFormatter;
    private ColorManager colorManager;
    private FilterManager filterManager;
    private MuteManager muteManager;
    private PrivateMessageManager privateMessageManager;
    private CrossServerManager crossServerManager;
    private MenuManager menuManager;
    private AnnouncementManager announcementManager;
    private AddonManager addonManager;
    private DatabaseManager databaseManager;
    private ItemDisplayManager itemDisplayManager;

    @Override
    public void onEnable() {
        instance = this;

        // 初始化配置管理器
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfig();

        // 初始化消息管理器
        this.messageManager = new MessageManager(this);
        this.messageManager.loadMessages();

        // 初始化数据库管理器
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.init();

        // 初始化频道管理器
        this.channelManager = new ChannelManager(this);
        this.channelManager.loadChannels();

        // 初始化聊天格式化器
        this.chatFormatter = new ChatFormatter(this);

        // 初始化颜色管理器
        this.colorManager = new ColorManager(this);
        this.colorManager.load();

        // 初始化屏蔽词管理器
        this.filterManager = new FilterManager(this);
        this.filterManager.load();

        // 初始化禁言管理器
        this.muteManager = new MuteManager(this);
        this.muteManager.load();

        // 初始化私聊管理器
        this.privateMessageManager = new PrivateMessageManager(this);

        // 初始化跨服通信管理器
        this.crossServerManager = new CrossServerManager(this);
        this.crossServerManager.load();

        // 初始化菜单管理器
        this.menuManager = new MenuManager(this);
        this.menuManager.load();

        // 初始化公告管理器
        this.announcementManager = new AnnouncementManager(this);
        this.announcementManager.load();

        // 初始化物品展示管理器
        this.itemDisplayManager = new ItemDisplayManager(this);
        this.itemDisplayManager.load();

        // 初始化API
        FotiaChatAPI.init(this);

        // 注册监听器
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);

        // 注册命令
        getCommand("channel").setExecutor(new ChannelCommand(this));
        getCommand("channel").setTabCompleter(new ChannelCommand(this));
        getCommand("chatcolor").setExecutor(new ChatColorCommand(this));
        getCommand("chatcolor").setTabCompleter(new ChatColorCommand(this));

        // 注册私聊命令
        MsgCommand msgCommand = new MsgCommand(this);
        getCommand("msg").setExecutor(msgCommand);
        getCommand("msg").setTabCompleter(msgCommand);

        ReplyCommand replyCommand = new ReplyCommand(this);
        getCommand("reply").setExecutor(replyCommand);
        getCommand("reply").setTabCompleter(replyCommand);

        SocialSpyCommand socialSpyCommand = new SocialSpyCommand(this);
        getCommand("socialspy").setExecutor(socialSpyCommand);
        getCommand("socialspy").setTabCompleter(socialSpyCommand);

        // 注册禁言命令
        MuteCommand muteCommand = new MuteCommand(this);
        getCommand("mute").setExecutor(muteCommand);
        getCommand("mute").setTabCompleter(muteCommand);

        UnmuteCommand unmuteCommand = new UnmuteCommand(this);
        getCommand("unmute").setExecutor(unmuteCommand);
        getCommand("unmute").setTabCompleter(unmuteCommand);

        // 注册主命令
        FotiaChatCommand fotiaChatCommand = new FotiaChatCommand(this);
        getCommand("fotiachat").setExecutor(fotiaChatCommand);
        getCommand("fotiachat").setTabCompleter(fotiaChatCommand);

        // 注册公告命令
        AnnouncementCommand announcementCommand = new AnnouncementCommand(this);
        getCommand("announcement").setExecutor(announcementCommand);
        getCommand("announcement").setTabCompleter(announcementCommand);

        // 注册Premium占位命令（当Premium模块未安装时提示用户）
        // 这些命令会在Premium模块加载时被覆盖
        PremiumPlaceholderCommand shoutPlaceholder = new PremiumPlaceholderCommand(this, "喊话");
        getCommand("shout").setExecutor(shoutPlaceholder);
        getCommand("shout").setTabCompleter(shoutPlaceholder);

        PremiumPlaceholderCommand chatbgPlaceholder = new PremiumPlaceholderCommand(this, "喊话背景");
        getCommand("chatbg").setExecutor(chatbgPlaceholder);
        getCommand("chatbg").setTabCompleter(chatbgPlaceholder);

        // 初始化Addon管理器（在命令注册之后，这样Addon可以覆盖占位命令）
        this.addonManager = new AddonManager(this);
        this.addonManager.loadAddons();

        getLogger().info("FotiaChat 已启用!");
    }

    @Override
    public void onDisable() {
        // 卸载Addon
        if (addonManager != null) {
            addonManager.unloadAddons();
        }
        // 停止公告任务
        if (announcementManager != null) {
            announcementManager.stopAllTasks();
        }
        // 禁用跨服通信
        if (crossServerManager != null) {
            crossServerManager.disable();
        }
        // 保存禁言数据
        if (muteManager != null) {
            muteManager.save();
        }
        // 关闭数据库连接
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("FotiaChat 已禁用!");
    }

    public static FotiaChat getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public ChannelManager getChannelManager() {
        return channelManager;
    }

    public ChatFormatter getChatFormatter() {
        return chatFormatter;
    }

    public ColorManager getColorManager() {
        return colorManager;
    }

    public FilterManager getFilterManager() {
        return filterManager;
    }

    public MuteManager getMuteManager() {
        return muteManager;
    }

    public PrivateMessageManager getPrivateMessageManager() {
        return privateMessageManager;
    }

    public CrossServerManager getCrossServerManager() {
        return crossServerManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public AnnouncementManager getAnnouncementManager() {
        return announcementManager;
    }

    public AddonManager getAddonManager() {
        return addonManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ItemDisplayManager getItemDisplayManager() {
        return itemDisplayManager;
    }

    public void reload() {
        configManager.loadConfig();
        messageManager.loadMessages();
        channelManager.loadChannels();
        colorManager.load();
        filterManager.load();
        muteManager.load();
        crossServerManager.load();
        menuManager.load();
        announcementManager.load();
        itemDisplayManager.load();
        // 重载Addon
        if (addonManager != null) {
            addonManager.reloadAddons();
        }
    }
}
