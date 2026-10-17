package dev.kizuna.meridian.items;

import dev.kizuna.backpack3d.protocol.StackCodec;
import dev.kizuna.backpack3d.service.TransferService;
import dev.kizuna.meridian.items.protocol.pb.ItemMessages.ItemSource;
import dev.kizuna.meridian.items.protocol.pb.ItemMessages.UseRequest;

import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

/** 只校验本项目契约，不解码客户端物品数据。 */
final class ItemProtocol {
    static final int VERSION = 1;
    static final int MAX_REQUEST_BYTES = 4096;

    private ItemProtocol() {}

    static void validate(UseRequest request) {
        // 有界请求只携带引用；数量、触发事件及效果实现由服务器决定。
        if (request == null || request.getSerializedSize() > MAX_REQUEST_BYTES
                || request.getProtocolVersion() != VERSION
                || !TransferService.validId(request.getRequestId())
                || request.getInventoryRevision() < 0
                || request.getStackFingerprint().size() != StackCodec.SHA256_BYTES
                || !request.getUnknownFields().asMap().isEmpty()) {
            throw new IllegalArgumentException("无效物品触发请求");
        }
        id(request.getBindingId());
        requireText(request.getBodyId(), "身体标识", 256);
        requireText(request.getRegistryRevision(), "目录修订", 128);
        validateSource(request.getSource());
    }

    static void validateSource(ItemSource source) {
        // uint32 在 Java 中也可能表现为负数；先拒绝溢出再作为列表下标使用。
        if (!source.getUnknownFields().asMap().isEmpty()) {
            throw new IllegalArgumentException("来源含未知字段");
        }
        switch (source.getLocationCase()) {
            case PLAYER_SLOT -> {
                if (source.getPlayerSlot() < 0) {
                    throw new IllegalArgumentException("槽号溢出");
                }
            }
            case STORED_ITEM_ID -> {
                if (!TransferService.validId(source.getStoredItemId())) {
                    throw new IllegalArgumentException("物品实例标识无效");
                }
            }
            default -> throw new IllegalArgumentException("必须指定物品来源");
        }
    }

    static Identifier id(String value) {
        // 要求规范 namespace:path，不能将缺省命名空间静默补为 minecraft。
        requireText(value, "命名标识", 256);
        var id = Identifier.tryParse(value);
        if (id == null || !id.toString().equals(value)) {
            throw new IllegalArgumentException("必须使用完整命名空间标识");
        }
        return id;
    }

    static void requireText(String value, String name, int maxBytes) {
        // 以 UTF-8 字节计上限，与持久化及协议中的实际体积一致。
        if (value == null || value.isBlank()
                || value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException(name + "缺失或过长");
        }
    }
}
