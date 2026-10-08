package com.team.blog.notification.domain;

import java.util.Objects;

/**
 * 묶음 키 ({@code notification.group_key}, 011 research R7). 받는 사람·묶음 키마다 안 읽은 묶음은 하나다({@code
 * uq_notification_unread_group}).
 *
 * @param value {@code LIKE:post:{postId}} 또는 {@code FOLLOW}
 */
public record GroupKey(String value) {

    public GroupKey {
        Objects.requireNonNull(value, "value");
    }

    /** 그 글의 좋아요 묶음. */
    public static GroupKey like(long postId) {
        return new GroupKey("LIKE:post:" + postId);
    }

    /** 새 팔로워 묶음 (받는 사람마다 하나). */
    public static GroupKey follow() {
        return new GroupKey("FOLLOW");
    }
}
