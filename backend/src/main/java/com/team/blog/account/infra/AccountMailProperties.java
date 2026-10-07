package com.team.blog.account.infra;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 계정 메일 설정 ({@code blog.mail}). SMTP 접속은 {@code spring.mail.*}(개발 Mailpit, 운영은 배포 때 설정값 — FR-009,
 * R-29).
 *
 * @param from 보내는 사람 주소
 * @param linkBaseUrl 메일 링크의 앞부분(스킴·호스트·포트, 끝 {@code /} 없음). 예: {@code https://devlog.example} →
 *     {@code https://devlog.example/verify-email?token=…}
 */
@Validated
@ConfigurationProperties("blog.mail")
public record AccountMailProperties(@NotBlank String from, @NotBlank String linkBaseUrl) {

    public AccountMailProperties {
        if (linkBaseUrl != null) {
            linkBaseUrl = linkBaseUrl.replaceAll("/+$", "");
        }
    }
}
