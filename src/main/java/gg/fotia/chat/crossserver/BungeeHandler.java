package gg.fotia.chat.crossserver;

import gg.fotia.chat.FotiaChat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.function.Consumer;

/**
 * BungeeCord消息处理器
 */
public class BungeeHandler implements PluginMessageListener {

    private final FotiaChat plugin;
    private final CrossServerManager manager;
    private Consumer<CrossServerMessage> messageHandler;
    private boolean enabled = false;

    private static final String CHANNEL = "BungeeCord";
    private static final String SUBCHANNEL = "FotiaChat";

    public BungeeHandler(FotiaChat plugin, CrossServerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * 启用BungeeCord通信
     */
    public void enable() {
        if (enabled) return;

        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        enabled = true;

        plugin.getLogger().info("BungeeCord跨服通信已启用");
    }

    /**
     * 禁用BungeeCord通信
     */
    public void disable() {
        if (!enabled) return;

        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        enabled = false;

        plugin.getLogger().info("BungeeCord跨服通信已禁用");
    }

    /**
     * 发送跨服消息
     */
    public void sendMessage(CrossServerMessage message) {
        if (!enabled) return;

        Player player = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (player == null) {
            plugin.getLogger().warning("无法发送BungeeCord消息: 没有在线玩家");
            return;
        }

        try {
            ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
            DataOutputStream msgOut = new DataOutputStream(msgBytes);
            msgOut.writeUTF("Forward");
            msgOut.writeUTF("ALL");
            msgOut.writeUTF(SUBCHANNEL);

            ByteArrayOutputStream dataBytes = new ByteArrayOutputStream();
            DataOutputStream dataOut = new DataOutputStream(dataBytes);
            dataOut.writeUTF(message.serialize());

            byte[] data = dataBytes.toByteArray();
            msgOut.writeShort(data.length);
            msgOut.write(data);

            player.sendPluginMessage(plugin, CHANNEL, msgBytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().severe("发送BungeeCord消息失败: " + e.getMessage());
        }
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!channel.equals(CHANNEL)) return;

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String subchannel = in.readUTF();

            if (!subchannel.equals(SUBCHANNEL)) return;

            short len = in.readShort();
            byte[] msgBytes = new byte[len];
            in.readFully(msgBytes);

            DataInputStream msgIn = new DataInputStream(new ByteArrayInputStream(msgBytes));
            String data = msgIn.readUTF();

            CrossServerMessage crossMessage = CrossServerMessage.deserialize(data);
            if (crossMessage != null && messageHandler != null) {
                // 忽略来自本服务器的消息
                if (!crossMessage.getServerName().equals(manager.getServerName())) {
                    Bukkit.getScheduler().runTask(plugin, () -> messageHandler.accept(crossMessage));
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("处理BungeeCord消息失败: " + e.getMessage());
        }
    }

    /**
     * 设置消息处理器
     */
    public void setMessageHandler(Consumer<CrossServerMessage> handler) {
        this.messageHandler = handler;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
