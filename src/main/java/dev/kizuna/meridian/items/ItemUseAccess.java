package dev.kizuna.meridian.items;

import dev.kizuna.meridian.items.protocol.pb.ItemMessages.UseRequest;

import net.minecraft.util.Identifier;

import java.util.Optional;

/** 宿主验证身份、目标身体、真实触发事件及物品来源权限；此回调只读。 */
@FunctionalInterface
public interface ItemUseAccess<C> {
    /**
     * 成功返回稳定的可信 caller ID。禁止将客户端提交的 trigger 或 body ID 当作授权。
     * Fabric 宿主需检查主线程、存活/旁观状态、BS3D 锁及该来源是否允许触发此事件。
     * 同一请求会在事务外及事务内重新授权；不得在校验中消费或执行效果。
     */
    Optional<String> authorize(C trustedContext, Identifier trigger, UseRequest request);
}
