package dev.kizuna.meridian.items;

import dev.kizuna.meridian.items.protocol.pb.ItemMessages.FailureCode;
import dev.kizuna.meridian.items.protocol.pb.ItemMessages.UseFailure;
import dev.kizuna.meridian.protocol.pb.MeridianMessages;

/** 集中定义的业务拒绝，通过异常退出宿主回调以撤销已经暂存的效果。 */
public final class ItemUseRejected extends RuntimeException {
    private final UseFailure failure;

    public ItemUseRejected(FailureCode code) {
        this(UseFailure.newBuilder().setCode(code).build());
    }

    private ItemUseRejected(UseFailure failure) {
        super(failure.getCode().name());
        // 缺省枚举不能作为可观察的业务错误。
        if (failure.getCode() == FailureCode.FAILURE_CODE_UNSPECIFIED
                || failure.getCode() == FailureCode.UNRECOGNIZED) {
            throw new IllegalArgumentException("拒绝必须有明确错误码");
        }
        this.failure = failure;
    }

    public static ItemUseRejected meridian(MeridianMessages.FailureCode code) {
        // Meridian 的失败码原样保留，附属不能靠异常文本重新分类。
        if (code == MeridianMessages.FailureCode.FAILURE_CODE_UNSPECIFIED
                || code == MeridianMessages.FailureCode.UNRECOGNIZED) {
            throw new IllegalArgumentException("Meridian 拒绝必须有明确错误码");
        }
        return new ItemUseRejected(UseFailure.newBuilder()
                .setCode(FailureCode.EFFECT_REJECTED).setMeridianCode(code).build());
    }

    public UseFailure failure() {
        return failure;
    }
}
