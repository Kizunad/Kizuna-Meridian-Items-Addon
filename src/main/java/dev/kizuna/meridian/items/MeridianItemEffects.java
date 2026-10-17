package dev.kizuna.meridian.items;

import dev.kizuna.meridian.core.BodyId;
import dev.kizuna.meridian.core.BodySnapshot;
import dev.kizuna.meridian.protocol.MeridianProtocol;
import dev.kizuna.meridian.protocol.pb.EnvironmentMessages.EnvironmentQiSource;
import dev.kizuna.meridian.protocol.pb.MeridianMessages;
import dev.kizuna.meridian.server.BodyAuditSink;
import dev.kizuna.meridian.server.BodyCommandHandler;
import dev.kizuna.meridian.server.BodyTransactionStore;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/** 将附属构造的一条 Meridian 权威命令接入联合事务，不内置具体效果。 */
public final class MeridianItemEffects {
    private MeridianItemEffects() {}

    public static <C> ItemEffect<C> command(
            Function<ItemEffect.Context<C>, MeridianMessages.RequestEnvelope> command,
            BiFunction<BodyTransactionStore, BodyAuditSink<ItemEffect.Context<C>>,
                    BodyCommandHandler<ItemEffect.Context<C>>> handlerFactory) {
        // 工厂必须使用提供的事务与无副作用审计；真正通知只能在宿主最终提交之后发生。
        Objects.requireNonNull(command, "command factory");
        Objects.requireNonNull(handlerFactory, "handler factory");
        return (context, store) -> {
            var request = command.apply(context);
            if (!request.hasBodyCommand()
                    || !request.getBodyCommand().getBodyId().equals(context.request().getBodyId())
                    || !request.getRequestId().equals(context.request().getRequestId())) {
                throw new IllegalArgumentException("效果命令必须属于当前身体与请求 ID");
            }
            var scope = new CommandScope(store, request);
            var handler = handlerFactory.apply(scope, (trusted, receipt, before, after) -> {
                // 内层处理器返回不代表物品事务已提交，此处禁止提前发送审计或游戏副作用。
            });
            var response = MeridianProtocol.decodeResponse(handler.handle(context, request.toByteArray()));
            if (!response.getRequestId().equals(request.getRequestId())) {
                throw new IllegalStateException("Meridian 响应请求 ID 不一致");
            }
            if (response.hasFailure()) {
                throw ItemUseRejected.meridian(response.getFailure().getCode());
            }
            if (!scope.staged || !response.hasCommandCommitted()
                    || !response.getCommandCommitted().getBodyId().equals(context.request().getBodyId())) {
                throw new IllegalStateException("Meridian 效果没有准备新的命令回执，或请求 ID 已被其他入口使用");
            }
        };
    }

    private static final class CommandScope implements BodyTransactionStore {
        private final BodyTransactionStore delegate;
        private final MeridianMessages.RequestEnvelope request;
        private boolean entered;
        private boolean staged;

        private CommandScope(BodyTransactionStore delegate, MeridianMessages.RequestEnvelope request) {
            this.delegate = delegate;
            this.request = request;
        }

        @Override
        public <T> T transact(BodyId bodyId, Function<Transaction, T> work) {
            // 每次物品效果对应一条新命令；防止独立入口的旧身体回执导致再次扣物品。
            if (entered || !bodyId.value().equals(request.getBodyCommand().getBodyId())) {
                throw new IllegalStateException("效果事务目标不一致或被重复调用");
            }
            entered = true;
            return delegate.transact(bodyId, transaction -> work.apply(new Transaction() {
                @Override
                public Optional<BodySnapshot> snapshot() {
                    return transaction.snapshot();
                }

                @Override
                public Optional<MeridianMessages.CommandReceipt> receipt(String callerId, String requestId) {
                    return transaction.receipt(callerId, requestId);
                }

                @Override
                public Optional<EnvironmentQiSource> environmentSource(String sourceId) {
                    return transaction.environmentSource(sourceId);
                }

                @Override
                public void stage(BodySnapshot next, MeridianMessages.CommandReceipt receipt) {
                    // 仅暂存当前命令，完整一致性与连带账本仍由 Meridian 宿主参与者验证。
                    if (staged || !receipt.getRequestId().equals(request.getRequestId())
                            || !receipt.getCommand().equals(request.getBodyCommand())) {
                        throw new IllegalStateException("效果暂存与触发命令不一致");
                    }
                    transaction.stage(next, receipt);
                    staged = true;
                }
            }));
        }
    }
}
