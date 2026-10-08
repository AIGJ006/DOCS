package com.team.blog.post.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.exception.AutosaveUnavailableException;
import com.team.blog.post.application.exception.IdempotencyKeyReusedException;
import com.team.blog.post.application.exception.NotPublishedException;
import com.team.blog.post.application.exception.PayloadTooLargeException;
import com.team.blog.post.application.exception.PublishInProgressException;
import com.team.blog.post.application.exception.PublishValidationException;
import com.team.blog.post.application.exception.VersionConflictException;
import com.team.blog.post.domain.PostReasonCode;
import com.team.blog.post.domain.ServerCopy;
import com.team.blog.shared.application.markdown.ContentTooComplexException;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.ErrorResponse;
import com.team.blog.shared.error.FieldError;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 002 오류 코드·예외 (T007, contracts/openapi.yaml 예시 문구, README "정해진 것": 마침표 없음). */
class PostExceptionsTest {

    @Test
    void 버전_충돌은_409이고_details_server에_서버_사본을_싣는다() {
        ServerCopy server =
                new ServerCopy("JPA N+1 정리", "## 원인", 14, Instant.parse("2026-10-02T05:03:12Z"));
        VersionConflictException e = new VersionConflictException(server);
        assertResponse(e, HttpStatus.CONFLICT, "VERSION_CONFLICT", "다른 탭이나 기기에서 이 글이 수정되었어요");
        assertThat(e.details()).isEqualTo(Map.of("server", server));
        assertThat(e.server()).isEqualTo(server);
    }

    @Test
    void 발행_검증은_400_VALIDATION_FAILED에_칸_오류를_모두_싣는다() {
        List<FieldError> errors =
                List.of(
                        PostReasonCode.TITLE_REQUIRED.fieldError("title"),
                        PostReasonCode.PENDING_IMAGES.fieldError("contentMd"));
        PublishValidationException e = new PublishValidationException(errors);
        assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.reasonCode().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(e.errors())
                .containsExactly(
                        new FieldError("title", "TITLE_REQUIRED", "제목을 입력해 주세요"),
                        new FieldError("contentMd", "PENDING_IMAGES", "업로드가 끝나지 않은 사진이 있어요"));
        assertThat(e.details()).isNull();
    }

    @Test
    void 각_예외의_상태_코드_문구() {
        assertResponse(
                new NotPublishedException(),
                HttpStatus.CONFLICT,
                "NOT_PUBLISHED",
                "발행한 글만 변경을 취소할 수 있어요");
        assertResponse(
                new PublishInProgressException(), HttpStatus.CONFLICT, "IN_PROGRESS", "발행 중이에요");
        assertResponse(
                new IdempotencyKeyReusedException(),
                HttpStatus.UNPROCESSABLE_CONTENT,
                "IDEMPOTENCY_KEY_REUSED",
                "같은 요청 키로 다른 내용을 보낼 수 없어요");
        assertResponse(
                new AutosaveUnavailableException(),
                HttpStatus.SERVICE_UNAVAILABLE,
                "AUTOSAVE_UNAVAILABLE",
                "잠시 후 다시 저장할게요");
        assertResponse(
                new PayloadTooLargeException(),
                HttpStatus.CONTENT_TOO_LARGE,
                "PAYLOAD_TOO_LARGE",
                "요청이 너무 커요");
        assertResponse(
                new ContentTooComplexException(),
                HttpStatus.BAD_REQUEST,
                "CONTENT_TOO_COMPLEX",
                "글 구조가 너무 복잡해요 (목록·인용은 20단계까지)");
    }

    @Test
    void 칸_오류_전용_코드도_문구가_있고_마침표로_끝나지_않는다() {
        for (PostReasonCode code : PostReasonCode.values()) {
            assertThat(code.defaultMessage()).as(code.name()).isNotBlank().doesNotEndWith(".");
            assertThat(code.code()).isEqualTo(code.name());
        }
        assertThat(PostReasonCode.TITLE_TOO_LONG.fieldError("title"))
                .isEqualTo(new FieldError("title", "TITLE_TOO_LONG", "제목은 100자까지예요"));
    }

    private static void assertResponse(
            ApiException e, HttpStatus status, String code, String message) {
        assertThat(e.status()).isEqualTo(status);
        ErrorResponse body = e.toResponse();
        assertThat(body.code()).isEqualTo(code);
        assertThat(body.message()).isEqualTo(message);
        assertThat(body.errors()).isEmpty();
    }
}
