package com.team.blog.media.domain;

/**
 * 사진 상태 (V1 {@code ck_image_status}, data-model §2). 완료 여부는 상태가 아니라 {@code width IS NOT NULL}로
 * 본다(research R5). 한 번 {@link #ATTACHED}가 되면 {@link #TEMP}로 돌아가지 않고, 연결 해제는 {@code detached_at}으로만
 * 표시한다.
 */
public enum ImageStatus {
    /** 올리기만 함 (글·프로필에 아직 연결되지 않음). 24시간 뒤 정리 대상. */
    TEMP,
    /** 글 또는 프로필에 연결된 적이 있음. */
    ATTACHED
}
