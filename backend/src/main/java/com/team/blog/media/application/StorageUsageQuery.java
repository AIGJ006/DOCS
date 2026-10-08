package com.team.blog.media.application;

import com.team.blog.shared.error.AccountStateException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * 내 사진 저장 공간 조회 (003 T060, FR-014·017). 보기 전용이라 인증 전·정지 회원도 볼 수 있고 탈퇴 유예만 403이다. 합계는 정리 작업이 파일을 지우기
 * 전까지 줄지 않는다(연결 해제 사진 포함).
 */
@Service
public class StorageUsageQuery {

    private final StorageQuotaService quota;
    private final AccountStatusGuard statusGuard;
    private final ImageProperties properties;
    private final Clock clock;

    public StorageUsageQuery(
            StorageQuotaService quota,
            AccountStatusGuard statusGuard,
            ImageProperties properties,
            Clock clock) {
        this.quota = quota;
        this.statusGuard = statusGuard;
        this.properties = properties;
        this.clock = clock;
    }

    /** 계정 상태 확인 뒤 조회. */
    public StorageUsage usageOf(long memberId) {
        try {
            statusGuard.requireActive(memberId, ActionKind.ACCOUNT_WRITE);
        } catch (AccountStateException e) {
            if (e.reasonCode() != CommonReasonCode.ACCOUNT_SUSPENDED) {
                throw e;
            }
        }
        return usage(memberId);
    }

    /** 조회만 (상태 확인 없음). */
    public StorageUsage usage(long memberId) {
        return new StorageUsage(
                quota.usedBytes(memberId),
                properties.quotaBytes(),
                quota.todayCount(memberId, clock.instant()).orElse(null),
                properties.dailyLimit(),
                new StorageUsage.Limits(
                        properties.maxUploadBytes(),
                        properties.maxThumbBytes(),
                        properties.maxSourceBytes(),
                        properties.longSide(),
                        properties.thumbMaxWidth(),
                        properties.gifMaxSide(),
                        properties.gifMaxFrames()));
    }
}
