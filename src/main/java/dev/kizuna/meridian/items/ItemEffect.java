package dev.kizuna.meridian.items;

import dev.kizuna.meridian.items.protocol.pb.ItemMessages.UseRequest;
import dev.kizuna.meridian.server.BodyTransactionStore;

import net.minecraft.item.ItemStack;

/** 附属实现具体效果；仅允许在传入的联合事务参与者中暂存变更。 */
@FunctionalInterface
public interface ItemEffect<C> {
    /**
     * 正常返回表示效果已准备好；业务拒绝抛 ItemUseRejected，异常令整个外层事务回滚。
     * 禁止使用另一个独立提交的 Meridian store、直接发包、修改实体或自行消费库存。
     * 回调中不得泄露事务对象；通知与其他副作用由宿主在最终提交后执行。
     */
    void prepare(Context<C> context, BodyTransactionStore meridian);

    record Context<C>(C trustedContext, String callerId, UseRequest request,
                      ItemBinding binding, ItemStack stack) {
        public Context {
            // 效果只能读取触发时的栈副本，不能通过上下文绕过库存候选计算。
            stack = stack.copy();
        }

        @Override
        public ItemStack stack() {
            return stack.copy();
        }
    }
}
