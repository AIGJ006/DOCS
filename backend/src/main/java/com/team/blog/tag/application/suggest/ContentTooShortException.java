package com.team.blog.tag.application.suggest;

import com.team.blog.shared.error.BusinessRuleException;
import java.util.LinkedHashMap;
import java.util.Map;

/** 422 {@code CONTENT_TOO_SHORT} + {@code details {minChars, length}}. AI를 부르지 않는다. */
public class ContentTooShortException extends BusinessRuleException {

    public ContentTooShortException(int minChars, int length) {
        super(TagSuggestReasonCode.CONTENT_TOO_SHORT, details(minChars, length));
    }

    private static Map<String, Object> details(int minChars, int length) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("minChars", minChars);
        details.put("length", length);
        return details;
    }
}
