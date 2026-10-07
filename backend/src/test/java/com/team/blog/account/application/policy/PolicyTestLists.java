package com.team.blog.account.application.policy;

import java.util.Set;
import org.springframework.core.io.ClassPathResource;

/** 단위 테스트용 목록 (금칙어는 {@code src/test/resources/policy/test-*.txt}, 예약어는 운영 목록 그대로). */
final class PolicyTestLists {

    private PolicyTestLists() {}

    static Set<String> bannedWords() {
        return ReservedWords.readList(new ClassPathResource("policy/test-banned-words.txt"));
    }

    static Set<String> bannedWordExceptions() {
        return ReservedWords.readList(
                new ClassPathResource("policy/test-banned-words-exceptions.txt"));
    }

    static Set<String> reservedHandles() {
        return ReservedWords.readList(new ClassPathResource("policy/reserved-handles.txt"));
    }

    static Set<String> reservedNicknames() {
        return ReservedWords.readList(new ClassPathResource("policy/reserved-nicknames.txt"));
    }

    static Set<String> commonPasswords() {
        return ReservedWords.readList(new ClassPathResource("policy/common-passwords.txt"));
    }

    static BannedWordFilter bannedWordFilter() {
        return new BannedWordFilter(bannedWords(), bannedWordExceptions());
    }
}
