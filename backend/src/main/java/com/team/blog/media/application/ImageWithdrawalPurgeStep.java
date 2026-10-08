package com.team.blog.media.application;

import com.team.blog.shared.application.withdraw.WithdrawalPurgeStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 정리 order 40: 내가 올린 사진 전부(현재 프로필·TEMP 포함)를 연결 해제로 표시 (015 T053, contracts/purge-steps.md §2).
 * 003 {@link ImagePurgeService#detachAllByUploader}가 {@code detached_at}을 보관 기간만큼 앞당겨 두면 같은 날 03:30
 * 사진 정리가 파일과 행을 지운다. 여기서는 파일을 건드리지 않는다.
 */
@Component
public class ImageWithdrawalPurgeStep implements WithdrawalPurgeStep {

    private static final Logger log = LoggerFactory.getLogger(ImageWithdrawalPurgeStep.class);

    private final ImagePurgeService images;

    public ImageWithdrawalPurgeStep(ImagePurgeService images) {
        this.images = images;
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(long memberId) {
        int detached = images.detachAllByUploader(memberId);
        log.info("탈퇴 사진 정리: memberId={} detached={}", memberId, detached);
    }
}
