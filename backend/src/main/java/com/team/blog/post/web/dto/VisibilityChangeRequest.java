package com.team.blog.post.web.dto;

/**
 * 공개 범위 변경 요청 (004 contracts {@code VisibilityChangeRequest}). 값은 문자열로 받아 Service가 ⑤단계에서 등록된 규칙으로
 * 검사한다(research R-21). {@code authorId} 등 다른 필드는 선언하지 않아 바인딩되지 않는다(42 §12 #2, FR-026).
 *
 * @param visibility {@code PUBLIC}·{@code PRIVATE} (적용자는 {@code FRIENDS})
 */
public record VisibilityChangeRequest(String visibility) {}
