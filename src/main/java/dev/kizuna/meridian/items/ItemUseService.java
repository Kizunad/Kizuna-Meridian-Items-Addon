package dev.kizuna.meridian.items;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kizuna.backpack3d.protocol.StackCodec;
import dev.kizuna.meridian.core.BodyId;
import dev.kizuna.meridian.items.protocol.pb.ItemMessages.*;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.Optional;

/** 服务端触发编排；物品数量、效果及提交回执都由同一可信目录和事务决定。 */
public final class ItemUseService<C> {
    private static final System.Logger LOGGER = System.getLogger(ItemUseService.class.getName());
    private final ItemRegistry<C> registry;
    private final ItemUseAccess<C> access;
    private final ItemUseStore<C> store;

    public ItemUseService(ItemRegistry<C> registry, ItemUseAccess<C> access, ItemUseStore<C> store) {
        // 没有授权策略或联合存储时不能建立可执行入口。
        this.registry = Objects.requireNonNull(registry, "registry");
        this.access = Objects.requireNonNull(access, "access");
        this.store = Objects.requireNonNull(store, "联合事务宿主不可缺省");
    }

    public UseResponse handle(C trustedContext, Identifier trustedTrigger, byte[] payload) {
        // 此方法供宿主既有路由调用，不注册通道，也不把包内数据当作真实触发事件。
        if (payload == null || payload.length > ItemProtocol.MAX_REQUEST_BYTES) {
            return failed("", FailureCode.INVALID_REQUEST);
        }
        try {
            return trigger(trustedContext, trustedTrigger, UseRequest.parseFrom(payload));
        } catch (InvalidProtocolBufferException invalid) {
            return failed("", FailureCode.INVALID_REQUEST);
        }
    }

    public UseResponse trigger(C trustedContext, Identifier trustedTrigger, UseRequest request) {
        // 非法请求不进入宿主；可信身份确认后才允许读取库存、身体或历史回执。
        try {
            ItemProtocol.validate(request);
        } catch (IllegalArgumentException invalid) {
            return failed("", FailureCode.INVALID_REQUEST);
        }
        if (trustedContext == null || trustedTrigger == null) {
            return failed(request.getRequestId(), FailureCode.ACCESS_DENIED);
        }
        try {
            var caller = access.authorize(trustedContext, trustedTrigger, request);
            if (caller.isEmpty()) {
                throw new ItemUseRejected(FailureCode.ACCESS_DENIED);
            }
            ItemProtocol.requireText(caller.get(), "可信调用者", 256);
            return store.transact(trustedContext, caller.get(), new BodyId(request.getBodyId()),
                    transaction -> execute(trustedContext, trustedTrigger, caller.get(), request, transaction));
        } catch (ItemUseRejected rejected) {
            // 必须在 transact 外转成失败结果，否则暂存过的身体可能被正常返回误提交。
            return response(request.getRequestId()).setFailure(rejected.failure()).build();
        } catch (RuntimeException failure) {
            LOGGER.log(System.Logger.Level.ERROR, "物品联合事务执行失败；重试必须复用原请求 ID", failure);
            return failed(request.getRequestId(), FailureCode.INTERNAL_ERROR);
        }
    }

    private UseResponse execute(C context, Identifier trigger, String caller, UseRequest request,
                                ItemUseStore.Transaction transaction) {
        // 等待事务期间可能发生撤权；回执查询前和最终暂存前都重新核对身份。
        authorize(context, trigger, caller, request);
        var previous = transaction.receipt(request.getRequestId());
        if (previous.isPresent()) {
            return replay(caller, trigger, request, previous.get());
        }
        if (!registry.revision().equals(request.getRegistryRevision())) {
            throw new ItemUseRejected(FailureCode.REGISTRY_CHANGED);
        }
        var binding = registry.bindings().get(ItemProtocol.id(request.getBindingId()));
        if (binding == null) {
            throw new ItemUseRejected(FailureCode.UNKNOWN_BINDING);
        }
        if (!binding.trigger().equals(trigger)) {
            throw new ItemUseRejected(FailureCode.TRIGGER_MISMATCH);
        }
        var inventory = transaction.inventory();
        if (inventory.data().getRevision() != request.getInventoryRevision()) {
            throw new ItemUseRejected(FailureCode.INVENTORY_CHANGED);
        }
        var stack = inventory.resolve(request.getSource());
        if (!Registries.ITEM.getId(stack.getItem()).equals(binding.item())
                || !StackCodec.fingerprint(stack).equals(request.getStackFingerprint())) {
            throw new ItemUseRejected(FailureCode.ITEM_CHANGED);
        }
        // 先确认代价可支付再准备效果；候选尚未发布，任一步拒绝都不会扣物品。
        var next = inventory.consume(request.getSource(), binding.consumeCount());
        registry.effect(binding).prepare(new ItemEffect.Context<>(context, caller, request, binding, stack),
                Objects.requireNonNull(transaction.meridian(), "缺少 Meridian 联合事务参与者"));
        authorize(context, trigger, caller, request);
        var committed = UseCommitted.newBuilder()
                .setEffectId(binding.effect().toString())
                .setConsumed(binding.consumeCount())
                .setPreviousInventoryRevision(inventory.data().getRevision())
                .setInventoryRevision(next.data().getRevision()).build();
        transaction.stage(next, UseReceipt.newBuilder().setCallerId(caller)
                .setTriggerId(trigger.toString()).setRequest(request).setCommitted(committed).build());
        return response(request.getRequestId()).setCommitted(committed).build();
    }

    private void authorize(C context, Identifier trigger, String caller, UseRequest request) {
        // 必须维持同一可信调用者；授权回调不得执行效果或保留事务对象。
        if (!access.authorize(context, trigger, request).equals(Optional.of(caller))) {
            throw new ItemUseRejected(FailureCode.ACCESS_DENIED);
        }
    }

    private static UseResponse replay(String caller, Identifier trigger, UseRequest request,
                                      UseReceipt receipt) {
        // 即使物品已用完或目录升级，完全相同的已提交请求也只返回原回执。
        ItemProtocol.validate(receipt.getRequest());
        var committed = receipt.getCommitted();
        ItemProtocol.id(committed.getEffectId());
        if (!receipt.getCallerId().equals(caller)
                || !receipt.getRequest().getRequestId().equals(request.getRequestId())
                || !receipt.hasCommitted() || committed.getConsumed() < 0 || committed.getConsumed() > 64
                || committed.getPreviousInventoryRevision() != receipt.getRequest().getInventoryRevision()
                || committed.getInventoryRevision() != Math.addExact(committed.getPreviousInventoryRevision(),
                    committed.getConsumed() == 0 ? 0 : 1)) {
            throw new IllegalStateException("物品回执的身份或消费版本不一致");
        }
        if (!receipt.getRequest().equals(request) || !receipt.getTriggerId().equals(trigger.toString())) {
            throw new ItemUseRejected(FailureCode.REQUEST_ID_CONFLICT);
        }
        return response(request.getRequestId()).setCommitted(committed).build();
    }

    private static UseResponse.Builder response(String requestId) {
        return UseResponse.newBuilder().setProtocolVersion(ItemProtocol.VERSION).setRequestId(requestId);
    }

    private static UseResponse failed(String requestId, FailureCode code) {
        return response(requestId).setFailure(UseFailure.newBuilder().setCode(code)).build();
    }
}
