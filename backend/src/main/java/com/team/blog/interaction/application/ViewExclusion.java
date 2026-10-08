package com.team.blog.interaction.application;

import com.team.blog.post.infra.PostView;
import com.team.blog.shared.security.Viewer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.core.io.Resource;

/**
 * 조회에서 빼는 요청 (009 T024·T032, FR-024·FR-031, contracts/view-pipeline.md §1 ②). 순서: 작성자 본인 → 관리자 →
 * 봇·링크 미리보기(User-Agent에 단어, 대소문자 무시 부분 일치) → 미리 불러오기({@code Sec-Purpose}·{@code Purpose}에 {@code
 * prefetch}). 볼 수 없는 글(404)은 이보다 먼저 거른다.
 */
public final class ViewExclusion {

    private final List<String> botWords;

    private ViewExclusion(List<String> botWords) {
        this.botWords = List.copyOf(botWords);
    }

    /** 단어 목록 줄들 (앞뒤 공백 제거, 빈 줄·{@code #} 주석 제외, 소문자). */
    public static ViewExclusion of(List<String> lines) {
        List<String> words = new ArrayList<>();
        for (String line : lines) {
            String word = line == null ? "" : line.strip();
            if (word.startsWith("﻿")) {
                word = word.substring(1).strip();
            }
            if (!word.isEmpty() && !word.startsWith("#")) {
                words.add(word.toLowerCase(Locale.ROOT));
            }
        }
        return new ViewExclusion(words);
    }

    /** 단어 목록 파일(UTF-8, 한 줄에 한 단어)로 만든다. 파일이 없으면 시작 실패. */
    public static ViewExclusion fromResource(Resource resource) {
        if (!resource.exists()) {
            throw new IllegalStateException("봇 단어 목록 파일이 없습니다: " + resource.getDescription());
        }
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return of(reader.lines().toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @return 빼야 하면 그 이유, 세야 하면 빈 값
     */
    public Optional<ViewOutcome> reason(
            PostView post, Viewer viewer, String userAgent, String secPurpose, String purpose) {
        if (viewer.isAuthorOf(post.authorId())) {
            return Optional.of(ViewOutcome.EXCLUDED_AUTHOR);
        }
        if (viewer.isAdmin()) {
            return Optional.of(ViewOutcome.EXCLUDED_ADMIN);
        }
        if (isBot(userAgent)) {
            return Optional.of(ViewOutcome.EXCLUDED_BOT);
        }
        if (isPrefetch(secPurpose) || isPrefetch(purpose)) {
            return Optional.of(ViewOutcome.EXCLUDED_PREFETCH);
        }
        return Optional.empty();
    }

    private boolean isBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        for (String word : botWords) {
            if (ua.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPrefetch(String header) {
        return header != null && header.toLowerCase(Locale.ROOT).contains("prefetch");
    }
}
