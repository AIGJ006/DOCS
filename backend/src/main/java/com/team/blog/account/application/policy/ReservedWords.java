package com.team.blog.account.application.policy;

import com.team.blog.account.infra.AccountProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 예약어·금칙어·흔한 비밀번호 목록 ({@code blog.policy.*}, 기본 {@code classpath:policy/*.txt}). 서비스 이름이 정해지면 파일에만
 * 추가한다(FR-019·024, README "정해진 것" 2026-10-07).
 *
 * <p>파일 형식: UTF-8, 한 줄에 한 단어, 앞뒤 공백 제거, 빈 줄과 {@code #}로 시작하는 줄은 건너뜀. 비교는 대소문자 무시이므로 소문자로 읽는다. 목록
 * 내용은 로그에 남기지 않는다.
 */
@Component
public class ReservedWords {

    private final Set<String> reservedHandles;
    private final Set<String> reservedNicknames;
    private final Set<String> bannedWords;
    private final Set<String> bannedWordExceptions;
    private final Set<String> commonPasswords;

    public ReservedWords(AccountProperties properties, ResourceLoader resourceLoader) {
        AccountProperties.Policy policy = properties.policy();
        this.reservedHandles = read(resourceLoader, policy.reservedHandles());
        this.reservedNicknames = read(resourceLoader, policy.reservedNicknames());
        this.bannedWords = read(resourceLoader, policy.bannedWords());
        this.bannedWordExceptions = read(resourceLoader, policy.bannedWordsExceptions());
        this.commonPasswords = read(resourceLoader, policy.commonPasswords());
    }

    public Set<String> reservedHandles() {
        return reservedHandles;
    }

    public Set<String> reservedNicknames() {
        return reservedNicknames;
    }

    public Set<String> bannedWords() {
        return bannedWords;
    }

    public Set<String> bannedWordExceptions() {
        return bannedWordExceptions;
    }

    public Set<String> commonPasswords() {
        return commonPasswords;
    }

    /** 목록 파일 하나를 읽는다 (소문자, 주석·빈 줄 제외, 읽은 순서 유지). 파일이 없으면 시작 실패. */
    public static Set<String> readList(Resource resource) {
        if (!resource.exists()) {
            throw new IllegalStateException("정책 목록 파일이 없습니다: " + resource.getDescription());
        }
        Set<String> words = new LinkedHashSet<>();
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.strip();
                if (word.startsWith("﻿")) {
                    word = word.substring(1).strip();
                }
                if (word.isEmpty() || word.startsWith("#")) {
                    continue;
                }
                words.add(word.toLowerCase(Locale.ROOT));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Set.copyOf(words);
    }

    private static Set<String> read(ResourceLoader loader, String location) {
        return readList(loader.getResource(location));
    }
}
