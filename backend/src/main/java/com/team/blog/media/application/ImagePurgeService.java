package com.team.blog.media.application;

import com.team.blog.media.infra.ImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 때 사진 정리 표시 (003 T082, contracts/storage.md §3-2, FR-043). 015 {@code
 * ImageWithdrawalPurgeStep}({@code WithdrawalPurgeStep} order 40)이 탈퇴 트랜잭션 안에서 부른다.
 *
 * <p>파일과 행은 여기서 지우지 않는다 — 회원의 모든 사진(현재 프로필·TEMP·글에 연결된 사진 포함)의 {@code detached_at}을 "연결 해제 보관 기간이
 * 이미 지난" 시각으로 두면 다음 {@link ImageCleanupJob}(최대 24시간 뒤)이 저장소 파일과 행을 지운다. 트랜잭션 안에서 외부(저장소) 호출을 하지 않기
 * 위해서다.
 */
@Service
public class ImagePurgeService {

    private final ImageRepository images;
    private final ImageProperties properties;

    public ImagePurgeService(ImageRepository images, ImageProperties properties) {
        this.images = images;
        this.properties = properties;
    }

    /**
     * @param memberId 탈퇴하는 회원
     * @return 바뀐 사진 수 (이미 오래전 해제된 사진은 세지 않음)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int detachAllByUploader(long memberId) {
        return images.detachAllByUploader(memberId, properties.cleanup().detachedTtl());
    }
}
