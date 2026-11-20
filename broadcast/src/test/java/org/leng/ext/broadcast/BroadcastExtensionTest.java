package org.leng.ext.broadcast;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BroadcastExtensionTest {

    @Test
    void orderlyModeFollowsListOrderAndLoops() {
        List<String> messages = List.of("1", "2", "3");
        AtomicInteger cursor = new AtomicInteger();

        assertEquals("1", pick(messages, "orderly", cursor, () -> 0));
        assertEquals("2", pick(messages, "orderly", cursor, () -> 0));
        assertEquals("3", pick(messages, "orderly", cursor, () -> 0));
        assertEquals("1", pick(messages, "orderly", cursor, () -> 0));
    }

    @Test
    void orderlyModeHandlesMessageListShrink() {
        AtomicInteger cursor = new AtomicInteger();
        List<String> three = List.of("1", "2", "3");
        List<String> two = List.of("1", "2");

        pick(three, "orderly", cursor, () -> 0);
        pick(three, "orderly", cursor, () -> 0);

        assertEquals("1", pick(two, "orderly", cursor, () -> 0));
    }

    @Test
    void randomModeUsesSupplierAndHandlesNegativeIndex() {
        List<String> messages = List.of("1", "2", "3");

        assertEquals("2", pick(messages, "random", new AtomicInteger(), () -> 1));
        assertEquals("3", pick(messages, "random", new AtomicInteger(), () -> -1));
    }

    @Test
    void emptyMessagesFallBackToDefault() {
        assertEquals("default", pick(List.of(), "orderly", new AtomicInteger(), () -> 0));
        assertEquals("default", pick(null, "random", new AtomicInteger(), () -> 0));
    }

    @Test
    void unknownModeFallsBackToRandom() {
        List<String> messages = List.of("1", "2", "3");

        assertEquals("2", pick(messages, "unknown", new AtomicInteger(), () -> 1));
    }

    private String pick(List<String> messages, String mode, AtomicInteger cursor,
                        java.util.function.IntSupplier random) {
        return BroadcastExtension.pickTemplate(messages, "default", mode, cursor, random);
    }
}
