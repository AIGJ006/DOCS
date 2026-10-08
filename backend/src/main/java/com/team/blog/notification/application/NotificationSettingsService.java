package com.team.blog.notification.application;

import com.team.blog.notification.domain.MutableType;
import com.team.blog.notification.infra.NotificationMuteRepository;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.ValidationException;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 종류 켜고 끄기 (011 US6, contracts §9). 끌 수 있는 5종만 다룬다 — 운영 알림은 목록에 없다. 기본(행 없음)은 모두 켜짐. 바꾸기는 {@code
 * ACCOUNT_WRITE}(이메일 인증 전도 통과).
 */
@Service
public class NotificationSettingsService {

    private final NotificationMuteRepository mutes;
    private final AccountStatusGuard guard;
    private final Clock clock;

    public NotificationSettingsService(
            NotificationMuteRepository mutes, AccountStatusGuard guard, Clock clock) {
        this.mutes = mutes;
        this.guard = guard;
        this.clock = clock;
    }

    /** 5종의 켜짐({@code true})·꺼짐, 키 순서는 {@link MutableType} 순서. */
    @Transactional(readOnly = true)
    public Map<String, Boolean> get(long me) {
        return view(mutes.mutedTypes(me));
    }

    /**
     * 5개 키를 모두 받아 저장한다.
     *
     * @throws ValidationException 키가 빠졌거나 boolean이 아니거나 모르는 키 (400 {@code VALIDATION_FAILED})
     */
    @Transactional
    public Map<String, Boolean> put(long me, Map<String, Object> body) {
        guard.requireActive(me, ActionKind.ACCOUNT_WRITE);
        List<FieldError> errors = new ArrayList<>();
        Set<MutableType> muted = EnumSet.noneOf(MutableType.class);
        Map<String, Object> input = body == null ? Map.of() : body;
        for (MutableType type : MutableType.values()) {
            Object value = input.get(type.name());
            if (!(value instanceof Boolean on)) {
                errors.add(
                        new FieldError(
                                type.name(),
                                value == null ? "REQUIRED" : "INVALID_VALUE",
                                "켜짐 또는 꺼짐을 골라 주세요"));
            } else if (!on) {
                muted.add(type);
            }
        }
        for (String key : input.keySet()) {
            if (!isKnown(key)) {
                errors.add(new FieldError(key, "UNKNOWN_FIELD", "알 수 없는 항목이에요"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        mutes.replace(me, muted, clock.instant());
        return view(muted);
    }

    private static boolean isKnown(String key) {
        for (MutableType type : MutableType.values()) {
            if (type.name().equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Boolean> view(Set<MutableType> muted) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (MutableType type : MutableType.values()) {
            result.put(type.name(), !muted.contains(type));
        }
        return result;
    }
}
