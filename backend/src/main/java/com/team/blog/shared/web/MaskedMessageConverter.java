package com.team.blog.shared.web;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * logback 변환어 {@code %maskedMsg}: 로그 문구에서 비밀번호·토큰 값을 가린다({@link SensitiveParamMasking#mask},
 * FR-015).
 */
public class MaskedMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SensitiveParamMasking.mask(super.convert(event));
    }
}
