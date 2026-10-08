package com.team.blog.tag.application.suggest;

import com.team.blog.shared.error.BusinessRuleException;
import java.util.Map;

/** 409 {@code AI_CONSENT_REQUIRED} + {@code details {version}} (현재 동의 버전) — 화면이 동의 창을 띄운다. */
public class AiConsentRequiredException extends BusinessRuleException {

    public AiConsentRequiredException(String currentVersion) {
        super(TagSuggestReasonCode.AI_CONSENT_REQUIRED, Map.of("version", currentVersion));
    }
}
