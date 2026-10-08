package gg.fotia.chat;

import gg.fotia.chat.announcement.AnnouncementManager;
import gg.fotia.chat.api.AddonManager;
import gg.fotia.chat.api.ChatPlaceholderProvider;
import gg.fotia.chat.api.FotiaChatAPI;
import gg.fotia.chat.api.PublicChatInterceptor;
import gg.fotia.chat.api.PublicChatInterceptorRegistry;
import gg.fotia.chat.api.PublicChatObserver;
import gg.fotia.chat.api.ChatPipeline;
import gg.fotia.chat.api.ChatRouteInterceptor;
import gg.fotia.chat.api.ChatRouteInterceptorRegistry;
import gg.fotia.chat.api.VirtualChatDispatcher;
import gg.fotia.chat.channel.ChannelManager;
import gg.fotia.chat.color.ColorManager;
import gg.fotia.chat.command.*;
import gg.fotia.chat.crossserver.CrossServerManager;
import gg.fotia.chat.filter.FilterManager;
import gg.fotia.chat.format.ChatFormatter;
import gg.fotia.chat.ignore.IgnoreManager;
import gg.fotia.chat.itemdisplay.ItemDisplayManager;
import gg.fotia.chat.listener.ChatListener;
import gg.fotia.chat.listener.MenuListener;
import gg.fotia.chat.listener.PlayerListener;
import gg.fotia.chat.manager.ConfigManager;
import gg.fotia.chat.manager.MessageManager;
import gg.fotia.chat.menu.MenuManager;
import gg.fotia.chat.mute.MuteManager;
import gg.fotia.chat.pipeline.DefaultChatPipeline;
import gg.fotia.chat.privatemsg.PrivateMessageManager;
import gg.fotia.chat.storage.DatabaseManager;
import gg.fotia.chat.update.UpdateChecker;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

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
    private IgnoreManager ignoreManager;
    private VirtualChatDispatcher virtualChatDispatcher;
    private UpdateChecker updateChecker;
    private final List<PublicChatObserver> publicChatObservers = new CopyOnWriteArrayList<>();
    private final List<ChatPlaceholderProvider> chatPlaceholderProviders = new CopyOnWriteArrayList<>();
    private final PublicChatInterceptorRegistry publicChatInterceptors =
            new PublicChatInterceptorRegistry(exception ->
                    getLogger().warning("公共聊天拦截器处理失败: " + exception.getMessage()));
    private final ChatRouteInterceptorRegistry chatRouteInterceptors =
            new ChatRouteInterceptorRegistry(exception ->
                    getLogger().warning("组件聊天路由处理失败: " + exception.getMessage()));
    private ChatPipeline chatPipeline;

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
        this.chatPipeline = new DefaultChatPipeline(this);

        // 初始化屏蔽管理器
        this.ignoreManager = new IgnoreManager(this);
        this.ignoreManager.load();
        this.virtualChatDispatcher = new VirtualChatDispatcher(this);

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

        // 初始化更新检测器
        this.updateChecker = new UpdateChecker(this);

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

        // 注册屏蔽命令
        ChatIgnoreCommand chatIgnoreCommand = new ChatIgnoreCommand(this);
        getCommand("chatignore").setExecutor(chatIgnoreCommand);
        getCommand("chatignore").setTabCompleter(chatIgnoreCommand);

        for (String name : List.of("channel", "chatcolor", "mute", "unmute", "chatignore")) {
            org.bukkit.command.PluginCommand command = getCommand(name);
            command.setExecutor(new StorageAwareCommandExecutor(this, command.getExecutor()));
        }

        // 初始化Addon管理器（在命令注册之后，这样Addon可以覆盖占位命令）
        this.addonManager = new AddonManager(this);
        this.addonManager.loadAddons();

        updateChecker.checkOnStartup();

        getLogger().info("FotiaChat 已启用!");
    }

    @Override
    public void onDisable() {
        runCleanup("关闭颜色菜单", () -> {
            if (menuManager != null) menuManager.close();
        });
        // 各清理步骤独立隔离：第三方 Addon 抛 Error 也不能阻断数据保存与连接关闭
        runCleanup("卸载Addon", () -> {
            if (addonManager != null) {
                addonManager.unloadAddons();
            }
        });
        runCleanup("清理公共聊天扩展", () -> {
            publicChatInterceptors.clear();
            chatRouteInterceptors.clear();
            publicChatObservers.clear();
            chatPlaceholderProviders.clear();
        });
        runCleanup("停止公告任务", () -> {
            if (announcementManager != null) {
                announcementManager.stopAllTasks();
            }
        });
        runCleanup("禁用跨服通信", () -> {
            if (crossServerManager != null) {
                crossServerManager.disable();
            }
        });
        runCleanup("保存禁言数据", () -> {
            if (muteManager != null) {
                muteManager.stop();
            }
        });
        runCleanup("保存屏蔽数据", () -> {
            if (ignoreManager != null) {
                ignoreManager.stop();
            }
        });
        runCleanup("停止物品展示", () -> {
            if (itemDisplayManager != null) {
                itemDisplayManager.stop();
            }
        });
        runCleanup("关闭数据库连接", () -> {
            if (databaseManager != null) {
                databaseManager.close();
            }
        });
        instance = null;
        getLogger().info("FotiaChat 已禁用!");
    }

    private void runCleanup(String action, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (Throwable throwable) {
            getLogger().severe(action + "失败: " + throwable.getMessage());
        }
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

    public IgnoreManager getIgnoreManager() {
        return ignoreManager;
    }

    public VirtualChatDispatcher getVirtualChatDispatcher() {
        return virtualChatDispatcher;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public void registerPublicChatObserver(PublicChatObserver observer) {
        if (observer != null) {
            publicChatObservers.add(observer);
        }
    }

    public void unregisterPublicChatObserver(PublicChatObserver observer) {
        publicChatObservers.remove(observer);
    }

    public void registerPublicChatInterceptor(PublicChatInterceptor interceptor) {
        publicChatInterceptors.register(interceptor);
    }

    public void unregisterPublicChatInterceptor(PublicChatInterceptor interceptor) {
        publicChatInterceptors.unregister(interceptor);
    }

    public void registerChatRouteInterceptor(ChatRouteInterceptor interceptor) {
        chatRouteInterceptors.register(interceptor);
    }

    public void unregisterChatRouteInterceptor(ChatRouteInterceptor interceptor) {
        chatRouteInterceptors.unregister(interceptor);
    }

    public ChatPipeline getChatPipeline() {
        return chatPipeline;
    }

    public void registerChatPlaceholderProvider(ChatPlaceholderProvider provider) {
        if (provider != null) {
            chatPlaceholderProviders.add(provider);
        }
    }

    public void unregisterChatPlaceholderProvider(ChatPlaceholderProvider provider) {
        chatPlaceholderProviders.remove(provider);
    }

    public Map<String, String> resolveChatPlaceholders(org.bukkit.entity.Player player) {
        if (player == null || chatPlaceholderProviders.isEmpty()) {
            return Map.of();
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        for (ChatPlaceholderProvider provider : chatPlaceholderProviders) {
            try {
                Map<String, String> provided = provider.provide(player);
                if (provided != null) {
                    resolved.putAll(provided);
                }
            } catch (Throwable throwable) {
                getLogger().warning("聊天占位符提供器处理失败: " + throwable.getMessage());
            }
        }
        return resolved;
    }

    public boolean interceptPublicChat(org.bukkit.entity.Player sender,
                                       gg.fotia.chat.channel.Channel channel,
                                       String plainMessage) {
        return publicChatInterceptors.intercept(sender, channel, plainMessage);
    }

    public boolean interceptRoutedChat(org.bukkit.entity.Player sender,
                                       gg.fotia.chat.channel.Channel channel,
                                       String plainMessage,
                                       net.kyori.adventure.text.Component component) {
        return chatRouteInterceptors.intercept(sender, channel, plainMessage, component);
    }

    public void notifyPublicChatObservers(org.bukkit.entity.Player sender,
                                          gg.fotia.chat.channel.Channel channel,
                                          String plainMessage,
                                          net.kyori.adventure.text.Component formattedMessage) {
        for (PublicChatObserver observer : publicChatObservers) {
            try {
                observer.onPublicChat(sender, channel, plainMessage, formattedMessage);
            } catch (Throwable throwable) {
                // 观察者来自外部 Addon，Error 也不能拖垮聊天分发
                getLogger().warning("通知公聊观察者失败: " + throwable.getMessage());
            }
        }
    }

    public void reload() {
        gg.fotia.chat.util.MessageUtil.refreshIntegrationCache();
        configManager.loadConfig();
        messageManager.loadMessages();
        channelManager.loadChannels();
        colorManager.load();
        filterManager.load();
        muteManager.load();
        ignoreManager.load();
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
