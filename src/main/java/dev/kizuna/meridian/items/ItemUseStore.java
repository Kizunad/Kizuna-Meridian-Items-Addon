package dev.kizuna.meridian.items;

import dev.kizuna.meridian.core.BodyId;
import dev.kizuna.meridian.items.protocol.pb.ItemMessages.UseReceipt;
import dev.kizuna.meridian.server.BodyTransactionStore;

import java.util.Optional;
import java.util.function.Function;

/** 宿主的联合提交边界，不提供分开保存两套存档的默认实现。 */
public interface ItemUseStore<C> {
    /**
     * 隔离调用者库存、目标身体、效果涉及的环境资源与回执，恰好执行 work 一次。
     * 回调正常返回后原子发布全部暂存；任何异常撤销全部暂存，不自动重跑回调。
     * 返回表示持久化已完成，崩溃恢复也必须同时恢复物品、身体及两侧回执。
     * 提交结果不确定时保留原请求 ID 重试。callerId 的回执索引必须跨 bodyId 去重。
     * 尚未具备这些保证的宿主不得装配本服务，不能组合两次独立 commit 冒充实现。
     */
    <T> T transact(C trustedContext, String callerId, BodyId bodyId,
                   Function<Transaction, T> work);

    interface Transaction {
        /** 当前调用者的已提交回执；保留期内原 ID 必须可重放，过期 ID 必须拒绝而非重新执行。 */
        Optional<UseReceipt> receipt(String requestId);

        /** 在本次隔离范围内，从 BS3D 真实个人账户读取；不包含地面容器或页面光标。 */
        Bs3dInventory inventory();

        /**
         * 仅属于当前 bodyId 的 Meridian 事务参与者，必须复用其授权、规则及一致性验证。
         * 内层 transact 正常返回只代表暂存，最终发布归外层所有；失败必须撤销本次内层暂存。
         * 环境账本等连带资源必须一并纳入联合提交；不支持的命令应拒绝。
         * 此参与者不兼容带即时外部副作用的 BodyAuditSink，参见 MeridianItemEffects。
         */
        BodyTransactionStore meridian();

        /**
         * 至多暂存一次物品候选和回执；校验身份、前后修订、消费差异及唯一键。
         * 不得立即发布；此后发生异常时须连同 Meridian 暂存全部撤销。
         * 物品变更最终通过 BS3D 宿主事务接入，并由宿主完成标脏、同步与恢复。
         */
        void stage(Bs3dInventory next, UseReceipt receipt);
    }
}
