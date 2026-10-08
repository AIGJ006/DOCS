package com.team.blog.category.domain;

import com.team.blog.shared.error.ValidationException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 정리한 카테고리 이름 (017 FR-003·FR-004, research R3): NFC → 앞뒤 공백 제거 → 안쪽 공백 묶음 한 칸, 1~30자(코드 포인트). 중복 비교
 * 키는 {@code Locale.ROOT} 소문자다. 이름은 글자로만 다룬다(원칙 IV) — HTML·Markdown을 해석하지 않는다.
 *
 * @param value 정리한 이름
 */
public record CategoryName(String value) {

    public static final int MAX_LENGTH = 30;

    private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * @throws ValidationException 칸 {@code name}: 비었음 {@code CATEGORY_NAME_REQUIRED}, 30자 초과 {@code
     *     CATEGORY_NAME_TOO_LONG}
     */
    public static CategoryName of(String raw) {
        String cleaned =
                raw == null
                        ? ""
                        : SPACES.matcher(Normalizer.normalize(raw, Normalizer.Form.NFC).strip())
                                .replaceAll(" ");
        if (cleaned.isEmpty()) {
            throw reject(CategoryReasonCode.CATEGORY_NAME_REQUIRED);
        }
        if (cleaned.codePointCount(0, cleaned.length()) > MAX_LENGTH) {
            throw reject(CategoryReasonCode.CATEGORY_NAME_TOO_LONG);
        }
        return new CategoryName(cleaned);
    }

    /** 같은 상위 안 중복 비교 키. */
    public String key() {
        return value.toLowerCase(Locale.ROOT);
    }

    private static ValidationException reject(CategoryReasonCode code) {
        return new ValidationException(List.of(code.fieldError("name")));
    }
}
