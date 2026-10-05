package gg.fotia.chat.api;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** 所有供应器、校验器及回调均在服务端主线程执行，不得执行阻塞 I/O。 */
public record ManagedSystemShout(
        UUID id, String backgroundId, int durationSeconds, long expiresAtMillis,
        BooleanSupplier valid, Predicate<UUID> viewerValid,
        Supplier<Map<UUID, SystemShoutService.Message>> messages, Lifecycle lifecycle, String source, String category) {
    public ManagedSystemShout(UUID id, String backgroundId, int durationSeconds, long expiresAtMillis,
                              BooleanSupplier valid, Predicate<UUID> viewerValid,
                              Supplier<Map<UUID, SystemShoutService.Message>> messages, Lifecycle lifecycle) {
        this(id, backgroundId, durationSeconds, expiresAtMillis, valid, viewerValid, messages, lifecycle, "fotiachat", "shout.system");
    }
    public ManagedSystemShout categorized(String source, String category) {
        return new ManagedSystemShout(id, backgroundId, durationSeconds, expiresAtMillis, valid, viewerValid, messages, lifecycle, source, category);
    }
    public ManagedSystemShout filterViewers(Predicate<UUID> filter) {
        return new ManagedSystemShout(id, backgroundId, durationSeconds, expiresAtMillis, valid, viewerValid.and(filter), messages, lifecycle, source, category);
    }
    public ManagedSystemShout {
        Objects.requireNonNull(id);
        Objects.requireNonNull(backgroundId);
        Objects.requireNonNull(valid);
        Objects.requireNonNull(viewerValid);
        Objects.requireNonNull(messages);
        Objects.requireNonNull(lifecycle);
        Objects.requireNonNull(source); Objects.requireNonNull(category);
        if (durationSeconds < 1 || durationSeconds > 60) throw new IllegalArgumentException("durationSeconds must be 1..60");
    }

    public interface Lifecycle {
        /** 仅在该观看者的 HUD 弹窗实际创建后触发。 */
        default void shown(UUID viewer, long endsAtMillis) {}
        default void hidden(UUID viewer) {}
        /** 包含正常结束、过期、主动取消和失去有效性，且每个已接收请求仅调用一次。 */
        default void finished() {}
    }
}
