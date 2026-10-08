package com.team.blog.discovery.application;

import com.team.blog.shared.error.ReasonCode;
import org.springframework.http.HttpStatus;

/**
 * 트렌딩·검색 거부 이유 (012 data-model §6, research R5·R6 — 원문에 없는 제안 코드, 팀 확인 T003 / ANALYSIS-tier-bc 팀 결정
 * 4). 문구 끝에 마침표를 붙이지 않는다(README "정해진 것").
 */
public enum DiscoveryReasonCode implements ReasonCode {
    /** 커서의 트렌딩 스냅샷이 만료됨 — 화면은 안내 후 처음부터 다시 부른다 (FR-011). */
    SNAPSHOT_EXPIRED(HttpStatus.GONE, "순위가 새로 바뀌었어요"),
    /** 정리 뒤 남는 단어가 없음(글) / 덩어리가 2글자 미만(사람) (FR-024·FR-036). */
    SEARCH_QUERY_TOO_SHORT(HttpStatus.BAD_REQUEST, "두 글자 이상 입력해 주세요");

    private final HttpStatus status;
    private final String defaultMessage;

    DiscoveryReasonCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
