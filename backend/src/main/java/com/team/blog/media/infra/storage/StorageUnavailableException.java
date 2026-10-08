package com.team.blog.media.infra.storage;

import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.CommonReasonCode;
import java.util.List;
import java.util.Map;

/**
 * 사진 저장소에 닿지 못함 → 503 {@code TEMPORARILY_UNAVAILABLE} + {@code Retry-After: 30}. 화면은 기기에 보관하고 다시
 * 올린다(research R12). 사진 행은 그대로 둔다(다시 complete 할 수 있게).
 */
public class StorageUnavailableException extends ApiException {

    public StorageUnavailableException(Throwable cause) {
        super(
                CommonReasonCode.TEMPORARILY_UNAVAILABLE,
                CommonReasonCode.TEMPORARILY_UNAVAILABLE.defaultMessage(),
                List.of(),
                null,
                Map.of("Retry-After", "30"));
        initCause(cause);
    }
}
