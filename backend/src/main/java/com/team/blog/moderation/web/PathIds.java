package com.team.blog.moderation.web;

/** 경로의 번호 (숫자가 아니거나 1 미만이면 0 — 어떤 행과도 맞지 않아 404가 된다). */
final class PathIds {

    private PathIds() {}

    static long parse(String raw) {
        if (raw == null || !raw.matches("[0-9]{1,18}")) {
            return 0;
        }
        long id = Long.parseLong(raw);
        return id >= 1 ? id : 0;
    }
}
