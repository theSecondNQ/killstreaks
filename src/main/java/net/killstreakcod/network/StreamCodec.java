package net.killstreakcod.network;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** Small codec used by the existing bounded messages on Forge's FriendlyByteBuf. */
public record StreamCodec<B, T>(BiConsumer<B, T> encoder, Function<B, T> decoder) {
    public void encode(B buffer, T value) {
        encoder.accept(buffer, value);
    }

    public T decode(B buffer) {
        return decoder.apply(buffer);
    }

    public static <B, T> StreamCodec<B, T> of(BiConsumer<B, T> encoder, Function<B, T> decoder) {
        return new StreamCodec<>(encoder, decoder);
    }

    public static <B, T, A> StreamCodec<B, T> composite(StreamCodec<B, A> codec,
            Function<T, A> getter, Function<A, T> factory) {
        return of((buffer, value) -> codec.encode(buffer, getter.apply(value)),
                buffer -> factory.apply(codec.decode(buffer)));
    }

    public static <B, T, A, C, D, E> StreamCodec<B, T> composite(
            StreamCodec<B, A> a, Function<T, A> ga, StreamCodec<B, C> c, Function<T, C> gc,
            StreamCodec<B, D> d, Function<T, D> gd, StreamCodec<B, E> e, Function<T, E> ge,
            Factory4<A, C, D, E, T> factory) {
        return of((buffer, value) -> {
            a.encode(buffer, ga.apply(value));
            c.encode(buffer, gc.apply(value));
            d.encode(buffer, gd.apply(value));
            e.encode(buffer, ge.apply(value));
        }, buffer -> factory.create(a.decode(buffer), c.decode(buffer), d.decode(buffer), e.decode(buffer)));
    }

    @FunctionalInterface
    public interface Factory4<A, B, C, D, T> {
        T create(A a, B b, C c, D d);
    }
}
