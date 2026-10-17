package dev.kizuna.meridian.items;

import net.minecraft.util.Identifier;

import java.util.Objects;

/** 附属声明的物品类型、真实事件、效果及单次消费件数。 */
public record ItemBinding(Identifier id, Identifier item, Identifier trigger,
                          Identifier effect, int consumeCount) {
    public ItemBinding {
        // 数量由可信登记决定；零表示仅触发，不能以负数表达发放。
        Objects.requireNonNull(id, "binding id");
        Objects.requireNonNull(item, "item id");
        Objects.requireNonNull(trigger, "trigger id");
        Objects.requireNonNull(effect, "effect id");
        if (consumeCount < 0 || consumeCount > 64) {
            throw new IllegalArgumentException("单次消费件数必须位于 [0,64]");
        }
    }
}
