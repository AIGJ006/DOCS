package com.team.blog.post.web.dto;

import com.team.blog.post.application.PostVisibilityService.VisibilityChangeResult;
import com.team.blog.post.domain.Visibility;
import java.time.Instant;

/**
 * 공개 범위 변경 응답 (004 contracts {@code VisibilityChangeResponse}). {@code firstPublicAt}은 한 번도 "발행 +
 * 전체 공개"가 된 적 없으면 {@code null}이고, 값은 마이크로초까지 자른 UTC ISO-8601이다.
 */
public record VisibilityChangeResponse(Visibility visibility, Instant firstPublicAt) {

    public static VisibilityChangeResponse from(VisibilityChangeResult result) {
        return new VisibilityChangeResponse(result.visibility(), result.firstPublicAt());
    }
}
