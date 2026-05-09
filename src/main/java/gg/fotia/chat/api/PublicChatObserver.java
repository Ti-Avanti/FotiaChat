package gg.fotia.chat.api;

import gg.fotia.chat.channel.Channel;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * 公共聊天观察者。
 * 用于在 FotiaChat 成功分发一条公聊消息后通知依赖插件。
 */
public interface PublicChatObserver {

    void onPublicChat(Player sender, Channel channel, String plainMessage, Component formattedMessage);
}
