package com.watchparty.common.validation;

import com.watchparty.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputSanitizerTest {

    private final InputSanitizer sanitizer = new InputSanitizer();

    @Test
    void removesHtmlAndDangerousSchemes() {
        String result = sanitizer.sanitizeRequiredText(
                "  <script>alert(1)</script> javascript:alert(2) أهلا  ",
                "message",
                1,
                200
        );

        assertEquals("alert(1) blocked:alert(2) أهلا", result);
    }

    @Test
    void returnsNullForBlankOptionalText() {
        assertNull(sanitizer.sanitizeOptionalText("   ", "field", 10));
    }

    @Test
    void rejectsTooLongText() {
        assertThrows(BadRequestException.class,
                () -> sanitizer.sanitizeRequiredText("abcdef", "field", 1, 3));
    }
}
