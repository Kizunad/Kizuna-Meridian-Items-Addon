package dev.kizuna.meridian.items;

import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 完整校验后发布的不可变目录；注册不执行物品或身体操作。 */
public final class ItemRegistry<C> {
    private final String revision;
    private final Map<Identifier, ItemBinding> bindings;
    private final Map<Identifier, ItemEffect<C>> effects;

    private ItemRegistry(String revision, Map<Identifier, ItemBinding> bindings,
                         Map<Identifier, ItemEffect<C>> effects) {
        this.revision = revision;
        this.bindings = Map.copyOf(bindings);
        this.effects = Map.copyOf(effects);
    }

    public static <C> ItemRegistry<C> load(String revision, List<Contribution<C>> contributions) {
        // 先在局部候选中检查所有来源与引用，避免加载顺序决定覆盖结果。
        ItemProtocol.requireText(revision, "目录修订", 128);
        var bindings = new HashMap<Identifier, ItemBinding>();
        var effects = new HashMap<Identifier, ItemEffect<C>>();
        var namespaces = new HashSet<String>();
        var routes = new HashSet<Route>();
        for (var contribution : List.copyOf(contributions)) {
            var namespace = contribution.namespace();
            if (!namespaces.add(namespace)) {
                throw new IllegalArgumentException("重复的登记来源：" + namespace);
            }
            contribution.effects().forEach((id, effect) -> {
                requireOwner(namespace, id);
                if (effects.putIfAbsent(id, effect) != null) {
                    throw new IllegalArgumentException("重复的效果：" + id);
                }
            });
            for (var binding : contribution.bindings()) {
                requireOwner(namespace, binding.id());
                if (bindings.putIfAbsent(binding.id(), binding) != null) {
                    throw new IllegalArgumentException("重复的绑定：" + binding.id());
                }
                if (!routes.add(new Route(binding.item(), binding.trigger()))) {
                    throw new IllegalArgumentException("物品与触发名已有绑定：" + binding.item());
                }
            }
        }
        for (var binding : bindings.values()) {
            if (!Registries.ITEM.containsId(binding.item())
                    || Registries.ITEM.get(binding.item()) == Items.AIR) {
                throw new IllegalArgumentException("绑定引用了未注册物品：" + binding.item());
            }
            if (!effects.containsKey(binding.effect())) {
                throw new IllegalArgumentException("绑定缺少效果提供者：" + binding.effect());
            }
            if (binding.consumeCount() > Registries.ITEM.get(binding.item()).getMaxCount()) {
                throw new IllegalArgumentException("消费数量超过物品的单栈上限：" + binding.id());
            }
        }
        return new ItemRegistry<>(revision, bindings, effects);
    }

    private static void requireOwner(String namespace, Identifier id) {
        // 附属可以引用其他附属的物品及效果，但只能定义自身命名空间中的内容。
        if (!namespace.equals(id.getNamespace())) {
            throw new IllegalArgumentException("登记来源不拥有命名空间：" + id);
        }
    }

    public String revision() {
        return revision;
    }

    public Map<Identifier, ItemBinding> bindings() {
        return bindings;
    }

    public Optional<ItemBinding> find(Identifier item, Identifier trigger) {
        return bindings.values().stream()
                .filter(binding -> binding.item().equals(item) && binding.trigger().equals(trigger))
                .findFirst();
    }

    ItemEffect<C> effect(ItemBinding binding) {
        return effects.get(binding.effect());
    }

    private record Route(Identifier item, Identifier trigger) {}

    public record Contribution<C>(String namespace, List<ItemBinding> bindings,
                                  Map<Identifier, ItemEffect<C>> effects) {
        public Contribution {
            // 贡献包在收集时即固定内容，调用方后续修改列表不会改变已加载目录。
            Objects.requireNonNull(namespace, "namespace");
            if (!namespace.matches("[a-z0-9_.-]+")) {
                throw new IllegalArgumentException("无效的登记命名空间");
            }
            bindings = List.copyOf(bindings);
            effects = Map.copyOf(effects);
        }
    }
}
