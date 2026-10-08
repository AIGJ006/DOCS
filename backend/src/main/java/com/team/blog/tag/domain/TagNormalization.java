package com.team.blog.tag.domain;

import java.util.Objects;

/**
 * 태그 하나의 정규화 결과 (008 data-model §2-1, research R1). 예외를 던지지 않고 값으로 돌려준다 — 발행 검증이 칸마다 결과를 모아 문제 태그를
 * 한 번에 알려 주기 때문이다(FR-013).
 */
public sealed interface TagNormalization {

    /**
     * 받아들인 태그.
     *
     * @param name 정규화된 이름 (DB {@code ck_tag_name}을 만족)
     */
    record Accepted(String name) implements TagNormalization {
        public Accepted {
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * 거부한 태그. 입력 값·걸린 금칙어는 담지 않는다(응답·로그에 남기지 않음).
     *
     * @param code 거부 이유
     */
    record Rejected(TagReasonCode code) implements TagNormalization {
        public Rejected {
            Objects.requireNonNull(code, "code");
        }
    }
}
