package com.team.blog.tag.application.suggest;

import java.util.List;
import java.util.Objects;

/** 공급자 호출 결과 (013 data-model §3). 예외를 던지지 않고 값으로 돌려준다. */
public sealed interface SuggestOutcome {

    /** AI가 답을 줌 — 정규화 전 문자열 0~5개. */
    record Success(List<String> rawTags) implements SuggestOutcome {
        public Success {
            rawTags = List.copyOf(rawTags);
        }
    }

    /** Gemini 429. */
    record QuotaExceeded(QuotaKind kind) implements SuggestOutcome {
        public QuotaExceeded {
            Objects.requireNonNull(kind, "kind");
        }
    }

    /** 실패. */
    record Failed(FailureKind kind) implements SuggestOutcome {
        public Failed {
            Objects.requireNonNull(kind, "kind");
        }
    }

    /** 자체 AI 동시 처리 초과. */
    record Busy() implements SuggestOutcome {}
}
