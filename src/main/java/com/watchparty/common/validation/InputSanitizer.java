package com.watchparty.common.validation;

import com.watchparty.common.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.regex.Pattern;

@Component
public class InputSanitizer {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern DANGEROUS_SCHEME_PATTERN = Pattern.compile("(?i)(javascript|data|vbscript)\\s*:");
    private static final Pattern BIDIRECTIONAL_CONTROL_PATTERN = Pattern.compile("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]");
    private static final Pattern ZERO_WIDTH_PATTERN = Pattern.compile("[\\u200B\\u200C\\uFEFF]");
    private static final Pattern MULTIPLE_SPACES_PATTERN = Pattern.compile("[ \\t]{2,}");

    /**
     * تنظيف نص عام مع حد طول.
     *
     * التعليق بالعربية: نتعامل مع كل مدخلات المستخدم كنص عادي Plain Text، لذلك نزيل وسوم HTML
     * والمخططات الخطرة ونمنع محارف التحكم قبل التخزين أو البث عبر WebSocket.
     */
    public String sanitizeRequiredText(String value, String fieldName, int minLength, int maxLength) {
        String sanitized = sanitizeOptionalText(value, fieldName, maxLength);
        if (!StringUtils.hasText(sanitized) || sanitized.length() < minLength) {
            throw new BadRequestException(fieldName + " قصير أو فارغ");
        }
        return sanitized;
    }

    public String sanitizeOptionalText(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
        String withoutTags = HTML_TAG_PATTERN.matcher(normalized).replaceAll("");
        String withoutDangerousSchemes = DANGEROUS_SCHEME_PATTERN.matcher(withoutTags).replaceAll("blocked:");
        String withoutBidiControls = BIDIRECTIONAL_CONTROL_PATTERN.matcher(withoutDangerousSchemes).replaceAll("");
        String withoutZeroWidth = ZERO_WIDTH_PATTERN.matcher(withoutBidiControls).replaceAll("");

        StringBuilder builder = new StringBuilder(withoutZeroWidth.length());
        withoutZeroWidth.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .forEach(builder::appendCodePoint);

        String sanitized = MULTIPLE_SPACES_PATTERN.matcher(builder.toString().trim()).replaceAll(" ");
        if (sanitized.length() > maxLength) {
            throw new BadRequestException(fieldName + " أطول من الحد المسموح: " + maxLength);
        }
        return sanitized.isBlank() ? null : sanitized;
    }
}
