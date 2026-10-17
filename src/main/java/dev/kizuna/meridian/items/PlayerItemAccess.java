package dev.kizuna.meridian.items;

import dev.kizuna.backpack3d.server.VolumeServer;
import dev.kizuna.meridian.items.protocol.pb.ItemMessages.UseRequest;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.Optional;

/** Fabric 玩家通用前置检查；身体及事件权限继续由宿主明确提供。 */
public final class PlayerItemAccess implements ItemUseAccess<ServerPlayerEntity> {
    private final ItemUseAccess<ServerPlayerEntity> policy;

    public PlayerItemAccess(ItemUseAccess<ServerPlayerEntity> policy) {
        this.policy = Objects.requireNonNull(policy, "身体和事件授权策略");
    }

    @Override
    public Optional<String> authorize(ServerPlayerEntity player, Identifier trigger, UseRequest request) {
        // 库存和 BS3D 会话只在逻辑服务器主线程访问；正在搬运时不能同时消费。
        var server = player.getServer();
        if (server == null || !server.isOnThread()) {
            return Optional.empty();
        }
        if (!player.isAlive() || player.isSpectator() || VolumeServer.locked(player)) {
            return Optional.empty();
        }
        return policy.authorize(player, trigger, request);
    }
}
