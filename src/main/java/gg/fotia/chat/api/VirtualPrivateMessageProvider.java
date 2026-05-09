package gg.fotia.chat.api;

import java.util.List;
import java.util.UUID;

/**
 * 为 FotiaChat 提供额外的虚拟私聊目标。
 */
public interface VirtualPrivateMessageProvider {

    VirtualPrivateMessageTarget resolveByName(String name);

    VirtualPrivateMessageTarget resolveByUniqueId(UUID uniqueId);

    List<String> suggestNames(String prefix);
}
