package com.team.blog.account.application.policy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 검증 정책 Bean. 목록은 {@link ReservedWords}(설정 파일), 중복 조회는 account 인프라의 {@code Jdbc*Lookup}. */
@Configuration(proxyBeanMethods = false)
public class AccountPolicyConfig {

    @Bean
    BannedWordFilter bannedWordFilter(ReservedWords words) {
        return new BannedWordFilter(words.bannedWords(), words.bannedWordExceptions());
    }

    @Bean
    HandlePolicy handlePolicy(ReservedWords words, BannedWordFilter bannedWordFilter) {
        return new HandlePolicy(words.reservedHandles(), bannedWordFilter);
    }

    @Bean
    HandleSuggester handleSuggester(ReservedWords words, HandleLookup handleLookup) {
        return new HandleSuggester(words.reservedHandles(), handleLookup);
    }

    @Bean
    NicknamePolicy nicknamePolicy(
            ReservedWords words, BannedWordFilter bannedWordFilter, NicknameLookup nicknameLookup) {
        return new NicknamePolicy(words.reservedNicknames(), bannedWordFilter, nicknameLookup);
    }

    @Bean
    PasswordPolicy passwordPolicy(ReservedWords words) {
        return new PasswordPolicy(words.commonPasswords());
    }
}
