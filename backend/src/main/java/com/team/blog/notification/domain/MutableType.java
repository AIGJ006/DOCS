package com.team.blog.notification.domain;

/** 회원이 끌 수 있는 알림 종류 5개 (V1 {@code ck_notification_mute_type}). 설정 API의 키 이름과 같다. */
public enum MutableType {
    COMMENT,
    REPLY,
    LIKE,
    FOLLOW,
    NEW_POST;

    public NotificationType type() {
        return NotificationType.valueOf(name());
    }
}
