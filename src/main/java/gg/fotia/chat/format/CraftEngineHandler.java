package gg.fotia.chat.format;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * CraftEngine integration helper for image tags and emoji replacement.
 */
public class CraftEngineHandler {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static final int DEFAULT_MAX_EMOJIS = 32;

    private final FotiaChat plugin;

    private volatile boolean emojiReflectionInitialized = false;
    private volatile boolean emojiReflectionAvailable = false;

    private Method ceInstanceMethod;
    private Method ceFontManagerMethod;
    private Method ceAdaptPlayerMethod;

    private Method ceReplaceMiniMessageEmojiMethod;
    private Method emojiTextResultReplacedMethod;
    private Method emojiTextResultTextMethod;

    private Method ceReplaceJsonEmojiMethod;
    private int jsonEmojiMethodArity = 0;
    private Method emojiJsonResultReplacedMethod;
    private Method emojiJsonResultTextMethod;

    private Method ceReplaceComponentEmojiMethod;
    private int componentEmojiMethodArity = 0;
    private Method emojiComponentResultChangedMethod;
    private Method emojiComponentResultNewTextMethod;

    private Method ceMaxEmojisPerParseMethod;

    public CraftEngineHandler(FotiaChat plugin) {
        this.plugin = plugin;
    }

    public String processMessage(Player player, String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String result = processImageTags(message);
        return processEmojiKeywords(player, result);
    }

    public String processImageTags(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        if (!MessageUtil.isCraftEngineEnabled()) {
            return removeImageTags(message);
        }

        return message;
    }

    public String processEmojiKeywords(Player player, String message) {
        if (message == null || message.isEmpty() || player == null) {
            return message;
        }
        if (!MessageUtil.isCraftEngineEnabled()) {
            return message;
        }

        ensureReflectionInitialized();
        if (!emojiReflectionAvailable || ceReplaceMiniMessageEmojiMethod == null) {
            return message;
        }

        try {
            Object cePlayer = ceAdaptPlayerMethod.invoke(null, player);
            Object ceInstance = ceInstanceMethod.invoke(null);
            if (ceInstance == null) {
                return message;
            }

            Object fontManager = ceFontManagerMethod.invoke(ceInstance);
            if (fontManager == null) {
                return message;
            }

            Object result = ceReplaceMiniMessageEmojiMethod.invoke(fontManager, message, cePlayer);
            if (result == null || emojiTextResultReplacedMethod == null || emojiTextResultTextMethod == null) {
                return message;
            }

            Object replacedObj = emojiTextResultReplacedMethod.invoke(result);
            if (!(replacedObj instanceof Boolean replaced) || !replaced) {
                return message;
            }

            Object textObj = emojiTextResultTextMethod.invoke(result);
            return textObj instanceof String text ? text : message;
        } catch (Throwable throwable) {
            disableEmojiReflection(throwable);
            return message;
        }
    }

    public Component processEmojiComponent(Player player, Component component) {
        if (component == null || player == null) {
            return component;
        }
        if (!MessageUtil.isCraftEngineEnabled()) {
            return component;
        }

        ensureReflectionInitialized();
        if (!emojiReflectionAvailable) {
            return component;
        }

        try {
            Object cePlayer = ceAdaptPlayerMethod.invoke(null, player);
            Object ceInstance = ceInstanceMethod.invoke(null);
            if (ceInstance == null) {
                return component;
            }

            Object fontManager = ceFontManagerMethod.invoke(ceInstance);
            if (fontManager == null) {
                return component;
            }

            // First try component path to preserve Adventure styles end-to-end.
            if (ceReplaceComponentEmojiMethod != null
                    && emojiComponentResultChangedMethod != null
                    && emojiComponentResultNewTextMethod != null) {
                Object componentResult;
                if (componentEmojiMethodArity == 2) {
                    componentResult = ceReplaceComponentEmojiMethod.invoke(fontManager, component, cePlayer);
                } else if (componentEmojiMethodArity == 3) {
                    String raw = PLAIN_TEXT.serialize(component);
                    componentResult = ceReplaceComponentEmojiMethod.invoke(fontManager, component, cePlayer, raw);
                } else {
                    String raw = PLAIN_TEXT.serialize(component);
                    componentResult = ceReplaceComponentEmojiMethod.invoke(fontManager, component, cePlayer, raw, resolveMaxEmojisPerParse());
                }

                if (componentResult != null) {
                    Object changedObj = emojiComponentResultChangedMethod.invoke(componentResult);
                    if (changedObj instanceof Boolean changed && changed) {
                        Object newTextObj = emojiComponentResultNewTextMethod.invoke(componentResult);
                        if (newTextObj instanceof Component newComponent) {
                            return newComponent;
                        }
                    }
                }
            }

            // Fallback to JSON path (same path CraftEngine decorate event uses).
            if (ceReplaceJsonEmojiMethod != null && emojiJsonResultReplacedMethod != null && emojiJsonResultTextMethod != null) {
                String rawJson = GSON.serialize(component);
                Object jsonResult;
                if (jsonEmojiMethodArity == 2) {
                    jsonResult = ceReplaceJsonEmojiMethod.invoke(fontManager, rawJson, cePlayer);
                } else {
                    jsonResult = ceReplaceJsonEmojiMethod.invoke(fontManager, rawJson, cePlayer, resolveMaxEmojisPerParse());
                }

                if (jsonResult != null) {
                    Object replacedObj = emojiJsonResultReplacedMethod.invoke(jsonResult);
                    if (replacedObj instanceof Boolean replaced && replaced) {
                        Object textObj = emojiJsonResultTextMethod.invoke(jsonResult);
                        if (textObj instanceof String jsonText) {
                            return GSON.deserialize(jsonText);
                        }
                    }
                }
            }

            return component;
        } catch (Throwable throwable) {
            disableEmojiReflection(throwable);
            return component;
        }
    }

    private void ensureReflectionInitialized() {
        if (!emojiReflectionInitialized) {
            initEmojiReflection();
        }
    }

    private synchronized void initEmojiReflection() {
        if (emojiReflectionInitialized) {
            return;
        }
        emojiReflectionInitialized = true;

        try {
            Class<?> bukkitCraftEngineClass = Class.forName(
                    "net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine");
            Class<?> bukkitAdaptorsClass = Class.forName(
                    "net.momirealms.craftengine.bukkit.api.BukkitAdaptors");
            Class<?> cePlayerClass = Class.forName(
                    "net.momirealms.craftengine.core.entity.player.Player");
            Class<?> fontManagerInterface = Class.forName(
                    "net.momirealms.craftengine.core.font.FontManager");

            ceInstanceMethod = bukkitCraftEngineClass.getMethod("instance");
            ceFontManagerMethod = bukkitCraftEngineClass.getMethod("fontManager");
            ceAdaptPlayerMethod = bukkitAdaptorsClass.getMethod("adapt", Player.class);

            try {
                ceReplaceMiniMessageEmojiMethod = fontManagerInterface.getMethod(
                        "replaceMiniMessageEmoji", String.class, cePlayerClass);
                Class<?> emojiTextResultClass = ceReplaceMiniMessageEmojiMethod.getReturnType();
                emojiTextResultReplacedMethod = findNoArgMethod(emojiTextResultClass, "replaced", "changed");
                emojiTextResultTextMethod = findNoArgMethod(emojiTextResultClass, "text", "newText");
            } catch (NoSuchMethodException ignored) {
                ceReplaceMiniMessageEmojiMethod = null;
            }

            resolveJsonEmojiMethod(fontManagerInterface, cePlayerClass);
            resolveComponentEmojiMethod(fontManagerInterface, cePlayerClass);

            try {
                Class<?> configClass = Class.forName("net.momirealms.craftengine.core.plugin.config.Config");
                ceMaxEmojisPerParseMethod = configClass.getMethod("maxEmojisPerParse");
            } catch (Throwable ignored) {
                ceMaxEmojisPerParseMethod = null;
            }

            emojiReflectionAvailable = ceReplaceMiniMessageEmojiMethod != null
                    || ceReplaceJsonEmojiMethod != null
                    || ceReplaceComponentEmojiMethod != null;

            if (!emojiReflectionAvailable) {
                plugin.getLogger().warning("CraftEngine emoji compatibility init failed: no compatible emoji method found");
            }
        } catch (Throwable throwable) {
            disableEmojiReflection(throwable);
        }
    }

    private void resolveJsonEmojiMethod(Class<?> fontManagerInterface, Class<?> cePlayerClass) {
        ceReplaceJsonEmojiMethod = null;
        jsonEmojiMethodArity = 0;
        emojiJsonResultReplacedMethod = null;
        emojiJsonResultTextMethod = null;

        Method method = null;
        int arity = 0;

        try {
            method = fontManagerInterface.getMethod("replaceJsonEmoji", String.class, cePlayerClass);
            arity = 2;
        } catch (NoSuchMethodException ignored) {
            // try next signature
        }

        if (method == null) {
            try {
                method = fontManagerInterface.getMethod("replaceJsonEmoji", String.class, cePlayerClass, int.class);
                arity = 3;
            } catch (NoSuchMethodException ignored) {
                // unsupported
            }
        }

        if (method == null) {
            return;
        }

        ceReplaceJsonEmojiMethod = method;
        jsonEmojiMethodArity = arity;

        Class<?> resultClass = method.getReturnType();
        emojiJsonResultReplacedMethod = findNoArgMethod(resultClass, "replaced", "changed");
        emojiJsonResultTextMethod = findNoArgMethod(resultClass, "text", "newText");
    }

    private void resolveComponentEmojiMethod(Class<?> fontManagerInterface, Class<?> cePlayerClass) {
        ceReplaceComponentEmojiMethod = null;
        componentEmojiMethodArity = 0;
        emojiComponentResultChangedMethod = null;
        emojiComponentResultNewTextMethod = null;

        Method method = null;
        int arity = 0;

        try {
            method = fontManagerInterface.getMethod("replaceComponentEmoji", Component.class, cePlayerClass);
            arity = 2;
        } catch (NoSuchMethodException ignored) {
            // try next signature
        }

        if (method == null) {
            try {
                method = fontManagerInterface.getMethod("replaceComponentEmoji", Component.class, cePlayerClass, String.class);
                arity = 3;
            } catch (NoSuchMethodException ignored) {
                // try next signature
            }
        }

        if (method == null) {
            try {
                method = fontManagerInterface.getMethod("replaceComponentEmoji", Component.class, cePlayerClass, String.class, int.class);
                arity = 4;
            } catch (NoSuchMethodException ignored) {
                // unsupported
            }
        }

        if (method == null) {
            return;
        }

        ceReplaceComponentEmojiMethod = method;
        componentEmojiMethodArity = arity;

        Class<?> resultClass = method.getReturnType();
        emojiComponentResultChangedMethod = findNoArgMethod(resultClass, "changed", "replaced");
        emojiComponentResultNewTextMethod = findNoArgMethod(resultClass, "newText", "text");
    }

    private Method findNoArgMethod(Class<?> targetClass, String... methodNames) {
        for (String name : methodNames) {
            try {
                return targetClass.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // try next
            }
        }
        return null;
    }

    private int resolveMaxEmojisPerParse() {
        if (ceMaxEmojisPerParseMethod != null) {
            try {
                Object value = ceMaxEmojisPerParseMethod.invoke(null);
                if (value instanceof Integer intValue && intValue > 0) {
                    return intValue;
                }
            } catch (Throwable ignored) {
                // fallback
            }
        }
        return DEFAULT_MAX_EMOJIS;
    }

    private void disableEmojiReflection(Throwable throwable) {
        emojiReflectionAvailable = false;
        plugin.getLogger().warning("CraftEngine emoji compatibility init failed: " + throwable.getMessage());
    }

    private String removeImageTags(String message) {
        return message.replaceAll("<image:[^>]+>", "");
    }

    public boolean containsImageTag(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        return message.contains("<image:");
    }

    public boolean hasImagePermission(Player player) {
        return player.hasPermission("fotiachat.image");
    }
}
