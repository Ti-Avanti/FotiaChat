package gg.fotia.chat.api;

import java.util.Map;
import java.util.UUID;

/** Premium 在 Bukkit ServicesManager 注册的系统喊话接口；所有调用必须在主线程。 */
public interface SystemShoutService {
    record Message(String title, String body) {
        public Message {
            java.util.Objects.requireNonNull(title);
            java.util.Objects.requireNonNull(body);
        }
    }

    /**
     * 进入 Premium 的 BetterHud 喊话队列，不经过玩家冷却、聊天路由或跨服广播。
     * recipients 是当前服务器接收者及其已本地化的 MiniMessage 文本；播放时再次检查本服在线状态。
     * durationSeconds 为 1～60 秒。后台不可用、背景不存在或队列已满时返回 false。
     */
    boolean broadcastLocal(String backgroundId, Map<UUID, Message> recipients, int durationSeconds);
    /** 分类用于个人接收控制；旧版 Premium 拒绝此请求，避免把荣誉播报误归类。 */
    default boolean broadcastLocal(String source, String category, String backgroundId, Map<UUID, Message> recipients, int durationSeconds) { return false; }

    /** 支持可取消、限时等待及动态观看者校验的系统播报；旧版扩展默认拒绝，不降级为无校验广播。 */
    default boolean submitLocal(ManagedSystemShout request) { return false; }

    /** 同时支持取消排队中和显示中的请求；必须在主线程调用。 */
    default boolean cancelLocal(UUID requestId) { return false; }
}
