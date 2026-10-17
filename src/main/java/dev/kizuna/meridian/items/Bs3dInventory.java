package dev.kizuna.meridian.items;

import dev.kizuna.backpack3d.item.ContainerItems;
import dev.kizuna.backpack3d.protocol.StackCodec;
import dev.kizuna.backpack3d.protocol.pb.VolumeProtocol.Item;
import dev.kizuna.backpack3d.protocol.pb.VolumeProtocol.SavedInventory;
import dev.kizuna.backpack3d.service.ContainerTree;
import dev.kizuna.backpack3d.service.TransferService;
import dev.kizuna.meridian.items.protocol.pb.ItemMessages.FailureCode;
import dev.kizuna.meridian.items.protocol.pb.ItemMessages.ItemSource;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** BS3D 真实个人库存的隔离副本及消费候选；不是另一份库存或持久化入口。 */
public final class Bs3dInventory {
    private final SavedInventory data;
    private final List<ItemStack> slots;

    public Bs3dInventory(SavedInventory data, List<ItemStack> slots) {
        // 由宿主在事务内提供真实来源；拒绝旧布局，迁移仍归 BS3D 所有。
        this.data = Objects.requireNonNull(data, "inventory data");
        this.slots = copySlots(slots);
        if (data.getFormatVersion() != TransferService.SAVE_VERSION) {
            throw new IllegalArgumentException("需要 BS3D 当前真实容器存档布局");
        }
        TransferService.validate(data);
        new ContainerTree(data, this.slots);
    }

    public SavedInventory data() {
        return data;
    }

    public List<ItemStack> slots() {
        return List.copyOf(copySlots(slots));
    }

    public ItemStack resolve(ItemSource source) {
        // 通过上游容器树定位实例；只允许个人槽位和随身子树，不推断地面访问权。
        ItemProtocol.validateSource(source);
        if (source.hasPlayerSlot()) {
            int slot = source.getPlayerSlot();
            if (slot >= slots.size() || slots.get(slot).isEmpty()) {
                throw new ItemUseRejected(FailureCode.ITEM_CHANGED);
            }
            return slots.get(slot).copy();
        }
        var tree = new ContainerTree(data, copySlots(slots));
        return tree.items().stream()
                .filter(item -> item.getId().equals(source.getStoredItemId()))
                .findFirst()
                .map(item -> StackCodec.decode(item.getStackNbt()))
                .orElseThrow(() -> new ItemUseRejected(FailureCode.ITEM_CHANGED));
    }

    public Bs3dInventory consume(ItemSource source, int count) {
        // 始终先定位真实栈；零消费仍要求物品存在，正消费仅改变该实例的数量。
        if (count < 0 || count > 64) {
            throw new IllegalArgumentException("消费件数必须位于 [0,64]");
        }
        var stack = resolve(source);
        if (count == 0) {
            return this;
        }
        if (ContainerItems.definition(stack) != null) {
            throw new ItemUseRejected(FailureCode.CONTAINER_CONSUMPTION_UNSUPPORTED);
        }
        if (stack.getCount() < count) {
            throw new ItemUseRejected(FailureCode.INSUFFICIENT_ITEMS);
        }
        stack.decrement(count);
        var nextSlots = copySlots(slots);
        var nextData = data.toBuilder().setRevision(Math.addExact(data.getRevision(), 1));
        if (source.hasPlayerSlot()) {
            nextSlots.set(source.getPlayerSlot(), stack.isEmpty() ? ItemStack.EMPTY : stack);
        } else {
            // 只重写包含目标的路径，保留位置、旋转、实例 ID、NBT 和未改动的子树。
            String target = source.getStoredItemId();
            boolean found = false;
            for (int slot = 0; slot < nextSlots.size() && !found; slot++) {
                var replacement = replace(nextSlots.get(slot), target, stack);
                found = replacement.found();
                if (found) {
                    nextSlots.set(slot, replacement.stack());
                }
            }
            for (int index = 0; index < data.getWornCount() && !found; index++) {
                var worn = data.getWorn(index);
                var replacement = replace(StackCodec.decode(worn.getStackNbt()), target, stack);
                found = replacement.found();
                if (found) {
                    nextData.setWorn(index, worn.toBuilder()
                            .setStackNbt(StackCodec.encode(replacement.stack())));
                }
            }
            if (!found) {
                throw new IllegalStateException("已定位的个人物品没有可写回的根容器");
            }
        }
        return new Bs3dInventory(nextData.build(), nextSlots);
    }

    private static Replacement replace(ItemStack root, String target, ItemStack remaining) {
        // 校验、深度和身份唯一性已交给 ContainerTree；这里只通过公开 API 更新候选内容。
        if (ContainerItems.definition(root) == null) {
            return new Replacement(root, false);
        }
        var items = new ArrayList<Item>(ContainerItems.contents(root).getItemsList());
        for (int index = 0; index < items.size(); index++) {
            var item = items.get(index);
            if (item.getId().equals(target)) {
                if (remaining.isEmpty()) {
                    items.remove(index);
                } else {
                    items.set(index, item.toBuilder().setStackNbt(StackCodec.encode(remaining)).build());
                }
            } else {
                var child = replace(StackCodec.decode(item.getStackNbt()), target, remaining);
                if (!child.found()) {
                    continue;
                }
                items.set(index, item.toBuilder().setStackNbt(StackCodec.encode(child.stack())).build());
            }
            var next = root.copy();
            ContainerItems.setContents(next, items);
            return new Replacement(next, true);
        }
        return new Replacement(root, false);
    }

    private static List<ItemStack> copySlots(List<ItemStack> slots) {
        // ItemStack 可变，不能只复制 List 后把权威栈引用泄露给附属。
        var result = new ArrayList<ItemStack>(slots.size());
        for (var stack : slots) {
            result.add(stack.copy());
        }
        return result;
    }

    private record Replacement(ItemStack stack, boolean found) {}
}
