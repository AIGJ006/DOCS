package com.team.blog.shared.error;

/**
 * 칸별 입력 오류 ({@code errors[]}의 항목).
 *
 * @param field 입력 칸 이름 (요청 JSON 속성 이름)
 * @param code 대문자 이유 코드 (예: {@code NICKNAME_DUPLICATE})
 * @param message 사용자 문구 (끝 마침표 없음)
 */
public record FieldError(String field, String code, String message) {

    public FieldError {
        message = Messages.withoutTrailingPeriod(message);
    }
}
