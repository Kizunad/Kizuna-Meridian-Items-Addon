package dev.kizuna.meridian.items;

import dev.kizuna.meridian.items.protocol.pb.ItemMessages.UseRequest;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Fabric 玩家通用前置检查；身体及事件权限继续由宿主明确提供。 */
public final class PlayerItemAccess implements ItemUseAccess<ServerPlayer> {
    private final ItemUseAccess<ServerPlayer> policy;

    public PlayerItemAccess(ItemUseAccess<ServerPlayer> policy) {
        this.policy = Objects.requireNonNull(policy, "身体和事件授权策略");
    }

    @Override
    public Optional<String> authorize(ServerPlayer player, Identifier trigger, UseRequest request) {
        // 库存消费与 BS3D 即时搬运都在逻辑服务器主线程串行执行。
        if (!player.level().getServer().isSameThread()) {
            return Optional.empty();
        }
        if (!player.isAlive() || player.isSpectator()) {
            return Optional.empty();
        }
        return policy.authorize(player, trigger, request);
    }
}
