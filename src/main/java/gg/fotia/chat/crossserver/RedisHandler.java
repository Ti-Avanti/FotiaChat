package gg.fotia.chat.crossserver;

import gg.fotia.chat.FotiaChat;
import org.bukkit.Bukkit;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Redis消息处理器
 * 注意: 需要添加Redis依赖才能使用完整功能
 * 当前为占位实现，实际使用需要引入Jedis或Lettuce
 */
public class RedisHandler {

    private final FotiaChat plugin;
    private final CrossServerManager manager;
    private Consumer<CrossServerMessage> messageHandler;
    private boolean enabled = false;

    private String host;
    private int port;
    private String password;
    private String channelName;

    private ExecutorService executor;
    private volatile boolean running = false;

    // Redis连接相关 (需要Jedis依赖)
    // private JedisPool jedisPool;
    // private Jedis subscriberJedis;

    public RedisHandler(FotiaChat plugin, CrossServerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * 启用Redis通信
     */
    public void enable(String host, int port, String password) {
        if (enabled) return;

        this.host = host;
        this.port = port;
        this.password = password;
        this.channelName = "fotiachat:messages";

        try {
            // 检查Redis依赖是否可用
            Class.forName("redis.clients.jedis.Jedis");

            // 初始化连接池
            initializeRedis();

            // 启动订阅线程
            startSubscriber();

            enabled = true;
            plugin.getLogger().info("Redis跨服通信已启用 (" + host + ":" + port + ")");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("Redis依赖未找到，跨服通信功能不可用");
            plugin.getLogger().warning("请在pom.xml中添加Jedis依赖");
            enabled = false;
        } catch (Exception e) {
            plugin.getLogger().severe("Redis连接失败: " + e.getMessage());
            enabled = false;
        }
    }

    /**
     * 初始化Redis连接
     */
    private void initializeRedis() {
        // 实际实现需要Jedis依赖
        // JedisPoolConfig config = new JedisPoolConfig();
        // config.setMaxTotal(10);
        // config.setMaxIdle(5);
        // if (password != null && !password.isEmpty()) {
        //     jedisPool = new JedisPool(config, host, port, 2000, password);
        // } else {
        //     jedisPool = new JedisPool(config, host, port);
        // }
    }

    /**
     * 启动订阅线程
     */
    private void startSubscriber() {
        if (executor != null) {
            executor.shutdownNow();
        }

        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FotiaChat-Redis-Subscriber");
            t.setDaemon(true);
            return t;
        });

        running = true;
        executor.submit(this::subscribeLoop);
    }

    /**
     * 订阅循环
     */
    private void subscribeLoop() {
        // 实际实现需要Jedis依赖
        // while (running) {
        //     try {
        //         subscriberJedis = jedisPool.getResource();
        //         subscriberJedis.subscribe(new JedisPubSub() {
        //             @Override
        //             public void onMessage(String channel, String message) {
        //                 handleMessage(message);
        //             }
        //         }, channelName);
        //     } catch (Exception e) {
        //         if (running) {
        //             plugin.getLogger().warning("Redis订阅断开，5秒后重连...");
        //             try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
        //         }
        //     }
        // }
    }

    /**
     * 处理接收到的消息
     */
    private void handleMessage(String data) {
        CrossServerMessage message = CrossServerMessage.deserialize(data);
        if (message != null && messageHandler != null) {
            // 忽略来自本服务器的消息
            if (!message.getServerName().equals(manager.getServerName())) {
                Bukkit.getScheduler().runTask(plugin, () -> messageHandler.accept(message));
            }
        }
    }

    /**
     * 禁用Redis通信
     */
    public void disable() {
        if (!enabled) return;

        running = false;

        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }

        // 关闭连接
        // if (subscriberJedis != null) {
        //     subscriberJedis.close();
        // }
        // if (jedisPool != null) {
        //     jedisPool.close();
        // }

        enabled = false;
        plugin.getLogger().info("Redis跨服通信已禁用");
    }

    /**
     * 发送跨服消息
     */
    public void sendMessage(CrossServerMessage message) {
        if (!enabled) return;

        // 实际实现需要Jedis依赖
        // try (Jedis jedis = jedisPool.getResource()) {
        //     jedis.publish(channelName, message.serialize());
        // } catch (Exception e) {
        //     plugin.getLogger().severe("发送Redis消息失败: " + e.getMessage());
        // }

        // 占位日志
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[Debug] Redis消息发送: " + message.getType());
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
