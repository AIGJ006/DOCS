package com.team.blog.discovery.application;

/** 시험에서 {@link SitemapService}의 패키지 전용 도구를 부른다. */
public final class SitemapServiceAccess {

    private SitemapServiceAccess() {}

    public static String escape(String value) {
        return SitemapService.escape(value);
    }
}
