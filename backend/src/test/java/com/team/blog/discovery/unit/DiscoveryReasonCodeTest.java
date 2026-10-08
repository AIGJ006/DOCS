package com.team.blog.discovery.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.application.DiscoveryReasonCode;
import com.team.blog.discovery.application.SearchQueryTooShortException;
import com.team.blog.discovery.application.SnapshotExpiredException;
import com.team.blog.shared.error.ErrorResponse;
import com.team.blog.shared.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** 트렌딩·검색 이유 코드 (012 T004, data-model §6) — 공통 본문 {code, message, errors: [], details: null}. */
class DiscoveryReasonCodeTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 스냅샷_만료는_410_공통_본문() {
        ResponseEntity<ErrorResponse> response = handler.handleApi(new SnapshotExpiredException());

        assertThat(response.getStatusCode().value()).isEqualTo(410);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("SNAPSHOT_EXPIRED");
        assertThat(body.message()).isEqualTo("순위가 새로 바뀌었어요");
        assertThat(body.errors()).isEmpty();
        assertThat(body.details()).isNull();
    }

    @Test
    void 검색어_부족은_400() {
        ResponseEntity<ErrorResponse> response =
                handler.handleApi(new SearchQueryTooShortException());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo("SEARCH_QUERY_TOO_SHORT");
        assertThat(response.getBody().message()).isEqualTo("두 글자 이상 입력해 주세요");
        assertThat(response.getBody().errors()).isEmpty();
        assertThat(response.getBody().details()).isNull();
    }

    @Test
    void 문구_끝에_마침표가_없다() {
        for (DiscoveryReasonCode code : DiscoveryReasonCode.values()) {
            assertThat(code.defaultMessage()).doesNotEndWith(".");
            assertThat(code.code()).isEqualTo(code.name());
        }
    }
}
