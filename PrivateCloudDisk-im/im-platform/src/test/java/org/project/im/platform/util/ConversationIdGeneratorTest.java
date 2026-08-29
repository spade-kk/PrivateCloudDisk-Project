package org.project.im.platform.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 不依赖 Mockito/中间件的会话 ID 契约测试。 */
class ConversationIdGeneratorTest {

    @Test
    void shouldGenerateSameSingleSessionForBothDirections() {
        String a = "00000000-0000-0000-0000-000000000001";
        String b = "00000000-0000-0000-0000-000000000002";
        assertEquals(a + "*" + b, ConversationIdGenerator.generate(a, b, 1));
        assertEquals(a + "*" + b, ConversationIdGenerator.generate(b, a, 1));
    }

    @Test
    void shouldPrefixGroupSessionAndRejectUnsupportedType() {
        assertEquals("group*00000000-0000-0000-0000-000000000099",
                ConversationIdGenerator.generate("00000000-0000-0000-0000-000000000001",
                        "00000000-0000-0000-0000-000000000099", 2));
        assertThrows(IllegalArgumentException.class,
                () -> ConversationIdGenerator.generate("a", "b", 99));
    }
}
