package com.team.blog.post.web.dto;

import com.team.blog.post.application.PostTrashService.RestoreOutcome;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.Visibility;

/**
 * 복구 응답 (006 T028, contracts {@code RestoredResult}, research R19 제안). 화면이 "복구했어요 [… 탭에서 보기]"의 탭을
 * 고르는 데 쓴다.
 */
public record RestoreResponse(boolean restored, PostStatus status, Visibility visibility) {

    public static RestoreResponse from(RestoreOutcome outcome) {
        return new RestoreResponse(true, outcome.status(), outcome.visibility());
    }
}
