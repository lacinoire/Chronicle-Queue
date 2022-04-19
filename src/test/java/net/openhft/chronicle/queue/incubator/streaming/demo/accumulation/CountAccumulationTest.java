package net.openhft.chronicle.queue.incubator.streaming.demo.accumulation;

import net.openhft.chronicle.core.io.IOTools;
import net.openhft.chronicle.core.time.SetTimeProvider;
import net.openhft.chronicle.queue.AppenderListener;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ChronicleQueueTestBase;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.queue.incubator.streaming.Accumulation;
import net.openhft.chronicle.queue.incubator.streaming.Accumulations;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.stream.Collector;

import static net.openhft.chronicle.queue.incubator.streaming.CollectorUtil.throwingMerger;
import static org.junit.Assert.assertEquals;

public class CountAccumulationTest extends ChronicleQueueTestBase {

    private static final String Q_NAME = CountAccumulationTest.class.getSimpleName();

    @Before
    public void clearBefore() {
        IOTools.deleteDirWithFiles(Q_NAME);
    }

    @After
    public void clearAfter() {
        IOTools.deleteDirWithFiles(Q_NAME);
    }

    @Test
    public void countCustom() {
        Accumulation<AtomicLong> listener = Accumulations.of(
                (wire, index) -> 1L,
                Collector.of(AtomicLong::new, AtomicLong::addAndGet, throwingMerger(), Collector.Characteristics.CONCURRENT));

        count(listener);
        assertEquals(3, listener.accumulation().get());
    }

    @Test
    public void countBuiltIn() {
        Accumulation<LongSupplier> listener = Accumulations.counting();
        count(listener);
        assertEquals(3, listener.accumulation().getAsLong());
    }

    private void count(AppenderListener listener) {
        final SetTimeProvider tp = new SetTimeProvider(1_000_000_000);
        try (ChronicleQueue q = SingleChronicleQueueBuilder.builder()
                .path(Q_NAME)
                .timeProvider(tp)
                .appenderListener(listener)
                .build()) {
            ExcerptAppender appender = q.acquireAppender();
            appender.writeText("one");
            appender.writeText("two");
            appender.writeText("three");
        }

    }

}