package net.openhft.chronicle.queue.internal.streaming;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.queue.AppenderListener;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.incubator.streaming.Accumulation;
import net.openhft.chronicle.queue.incubator.streaming.ExcerptExtractor;
import net.openhft.chronicle.queue.incubator.streaming.ToLongExcerptExtractor;
import net.openhft.chronicle.wire.DocumentContext;
import net.openhft.chronicle.wire.Wire;
import org.jetbrains.annotations.NotNull;

import java.util.function.LongSupplier;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import java.util.stream.Collector;

import static java.util.Objects.requireNonNull;

public final class AccumulationUtil {

    // Suppresses default constructor, ensuring non-instantiability.
    private AccumulationUtil() {
    }

    public static long accept(@NotNull final AppenderListener appenderListener,
                              @NotNull final ExcerptTailer tailer) {
        requireNonNull(tailer);
        long lastIndex = -1;
        boolean end = false;
        while (!end) {
            try (final DocumentContext dc = tailer.readingDocument()) {
                final Wire wire = dc.wire();
                if (dc.isPresent() && wire != null) {
                    lastIndex = dc.index();
                    appenderListener.onExcerpt(wire, lastIndex);
                } else {
                    end = true;
                }
            }
        }
        return lastIndex;
    }

    public static final class CollectorAccumulation<E, A, R> implements Accumulation<R> {
        private final ExcerptExtractor<E> extractor;
        private final Collector<E, A, ? extends R> collector;

        private final A accumulation;

        public CollectorAccumulation(@NotNull final ExcerptExtractor<E> extractor,
                                     @NotNull final Collector<E, A, ? extends R> collector) {
            this.extractor = extractor;
            this.collector = collector;

            if (!collector.characteristics().contains(Collector.Characteristics.CONCURRENT)) {
                Jvm.warn().on(CollectorAccumulation.class, "The collector " + collector + " should generally have the characteristics CONCURRENT");
            }
            this.accumulation = collector.supplier().get();
        }

        @Override
        public void onExcerpt(@NotNull Wire wire, long index) {
            final E element = extractor.extract(wire, index);
            if (element != null) {
                collector.accumulator()
                        .accept(accumulation, element);
            }
        }

        @SuppressWarnings("unchecked")
        @NotNull
        @Override
        public R accumulation() {
            if (collector.characteristics().contains(Collector.Characteristics.IDENTITY_FINISH)) {
                return (R) accumulation;
            }
            return collector.finisher().apply(accumulation);
        }

        @Override
        public long accept(@NotNull final ExcerptTailer tailer) {
            requireNonNull(tailer);
            return AccumulationUtil.accept(this, tailer);
        }
    }

    public static final class LongSupplierAccumulation<A> implements Accumulation<LongSupplier> {
        private final ToLongExcerptExtractor extractor;
        private final ObjLongConsumer<A> accumulator;
        private final A accumulation;
        private final ToLongFunction<A> finisher;

        public LongSupplierAccumulation(@NotNull final ToLongExcerptExtractor extractor,
                                        @NotNull final Supplier<A> supplier,
                                        @NotNull final ObjLongConsumer<A> accumulator,
                                        @NotNull final ToLongFunction<A> finisher) {
            this.extractor = extractor;
            this.accumulator = accumulator;
            this.accumulation = supplier.get();
            this.finisher = finisher;
        }

        @Override
        public void onExcerpt(@NotNull Wire wire, long index) {
            final long element = extractor.extractAsLong(wire, index);
            if (element != Long.MIN_VALUE) {
                accumulator.accept(accumulation, element);
            }
        }

        @NotNull
        @Override
        public LongSupplier accumulation() {
            return () -> finisher.applyAsLong(accumulation);
        }

        @Override
        public long accept(@NotNull final ExcerptTailer tailer) {
            requireNonNull(tailer);
            return AccumulationUtil.accept(this, tailer);
        }
    }
}