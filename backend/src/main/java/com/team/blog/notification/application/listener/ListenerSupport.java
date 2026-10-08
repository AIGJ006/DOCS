package com.team.blog.notification.application.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 알림 리스너 공통 도우미 (011 research R3, FR-002). 알림 처리 실패는 원래 행동에 영향을 주지 않는다 — 예외를 잡아 이벤트 종류와 대상 번호만
 * WARN으로 남긴다(제목·닉네임 등 글자는 남기지 않는다, 이벤트에도 없다).
 */
public final class ListenerSupport {

    private static final Logger log = LoggerFactory.getLogger(ListenerSupport.class);

    private ListenerSupport() {}

    /**
     * @param eventName 이벤트 종류 (예: {@code PostLiked})
     * @param targetId 대상 번호 (글·댓글·회원·신고)
     */
    public static void runSafely(String eventName, long targetId, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException ex) {
            log.warn(
                    "알림 처리 실패: event={}, targetId={}, error={}",
                    eventName,
                    targetId,
                    ex.getClass().getSimpleName());
        }
    }
}
