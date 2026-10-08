package com.team.blog.tag.application.suggest;

/** 공급자 호출 실패 종류 (013 data-model §3). */
public enum FailureKind {
    /** 응답 시간 제한 초과. */
    TIMEOUT,
    /** 5xx·400·401·403 등 서버가 답하지 못함. */
    SERVER_ERROR,
    /** 200이지만 형식이 깨짐 (JSON 아님, {@code tags}가 문자열 배열이 아님, 6개 이상). */
    MALFORMED,
    /** 연결 실패. */
    CONNECT
}
