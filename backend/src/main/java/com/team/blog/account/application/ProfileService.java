package com.team.blog.account.application;

import com.team.blog.account.application.policy.BioCheck;
import com.team.blog.account.application.policy.BioPolicy;
import com.team.blog.account.application.policy.NicknameCheck;
import com.team.blog.account.application.policy.NicknamePolicy;
import com.team.blog.account.domain.Member;
import com.team.blog.account.infra.AccountProperties;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.media.application.ImageUrlResolver;
import com.team.blog.media.application.ProfileImageKeys;
import com.team.blog.media.application.ProfileImageQuery;
import com.team.blog.media.application.ProfileImageService;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.ValidationException;
import com.team.blog.shared.infra.db.UniqueViolations;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 조회·저장 (FR-028·046~052, 11 §5, R-19·R-20).
 *
 * <p>저장은 한 트랜잭션: ① {@code member FOR UPDATE} → ② {@link AccountStatusGuard}({@code ACCOUNT_WRITE},
 * 인증 전도 통과) → ③ 보낸 칸만 검사(닉네임 — 자기 자신 제외 중복, 소개, 사진 ID)해 오류를 <b>모두</b> 모음 → 있으면 400(아무것도 저장 안 함) → ④
 * 닉네임이 실제로 바뀌는데 30일이 안 지났으면 전체 409 {@code NICKNAME_CHANGE_TOO_SOON} → ⑤ 저장({@code
 * nickname_changed_at}은 실제 변경 때만) → ⑥ 사진 떼기 → 붙이기. 동시 저장은 회원 행 잠금으로 직렬화되어 나중 저장이 반영된다.
 */
@Service
public class ProfileService {

    static final String NICKNAME_FIELD = "nickname";
    static final String BIO_FIELD = "bio";
    static final String IMAGE_FIELD = "profileImageId";

    private final MemberRepository members;
    private final AccountStatusGuard statusGuard;
    private final NicknamePolicy nicknamePolicy;
    private final BioPolicy bioPolicy;
    private final ProfileImageService profileImages;
    private final ProfileImageQuery profileImageQuery;
    private final ImageUrlResolver imageUrlResolver;
    private final Duration nicknameChangeInterval;
    private final Clock clock;

    public ProfileService(
            MemberRepository members,
            AccountStatusGuard statusGuard,
            NicknamePolicy nicknamePolicy,
            BioPolicy bioPolicy,
            ProfileImageService profileImages,
            ProfileImageQuery profileImageQuery,
            ImageUrlResolver imageUrlResolver,
            AccountProperties properties,
            Clock clock) {
        this.members = members;
        this.statusGuard = statusGuard;
        this.nicknamePolicy = nicknamePolicy;
        this.bioPolicy = bioPolicy;
        this.profileImages = profileImages;
        this.profileImageQuery = profileImageQuery;
        this.imageUrlResolver = imageUrlResolver;
        this.nicknameChangeInterval = properties.member().nicknameChangeInterval();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MyProfile get(long memberId) {
        Member member =
                members.findById(memberId)
                        .filter(m -> !m.isDeleted())
                        .orElseThrow(() -> new ApiException(CommonReasonCode.LOGIN_REQUIRED));
        return toProfile(member);
    }

    @Transactional
    public MyProfile update(long memberId, ProfileUpdate update) {
        if (update == null || update.isEmpty()) {
            throw new ApiException(CommonReasonCode.MALFORMED_REQUEST);
        }
        Member member =
                members.findByIdForUpdate(memberId)
                        .filter(m -> !m.isDeleted())
                        .orElseThrow(() -> new ApiException(CommonReasonCode.LOGIN_REQUIRED));
        statusGuard.requireActive(memberId, ActionKind.ACCOUNT_WRITE);

        List<FieldError> errors = new ArrayList<>();
        String nickname = null;
        if (update.nicknamePresent()) {
            NicknameCheck check = nicknamePolicy.check(update.nickname(), memberId);
            if (check.valid()) {
                nickname = check.normalized();
            } else {
                errors.add(check.toFieldError(NICKNAME_FIELD));
            }
        }
        String bio = null;
        if (update.bioPresent()) {
            BioCheck check = bioPolicy.check(update.bio());
            if (check.valid()) {
                bio = check.normalized();
            } else {
                errors.add(check.toFieldError(BIO_FIELD));
            }
        }
        if (update.profileImagePresent()
                && update.profileImageId() != null
                && !profileImages.isAttachable(memberId, update.profileImageId())) {
            errors.add(
                    new FieldError(
                            IMAGE_FIELD,
                            ProfileImageService.INVALID_PROFILE_IMAGE,
                            ProfileImageService.INVALID_PROFILE_IMAGE_MESSAGE));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        Instant now = clock.instant();
        if (nickname != null && !nickname.equals(member.getNickname())) {
            Instant availableAt = member.nicknameChangeAvailableAt(nicknameChangeInterval);
            if (availableAt != null && availableAt.isAfter(now)) {
                throw new BusinessRuleException(
                        AccountReasonCode.NICKNAME_CHANGE_TOO_SOON,
                        Map.of("nextChangeAvailableAt", availableAt.toString()));
            }
            member.changeNickname(nickname, now);
        }
        if (update.bioPresent()) {
            member.changeBio(bio, now);
        }
        try {
            members.flush();
        } catch (DataIntegrityViolationException e) {
            if (UniqueViolations.isViolationOf(e, "uq_member_nickname")) {
                throw new ValidationException(
                        List.of(
                                new FieldError(
                                        NICKNAME_FIELD,
                                        AccountReasonCode.NICKNAME_DUPLICATE.code(),
                                        AccountReasonCode.NICKNAME_DUPLICATE.defaultMessage())));
            }
            throw e;
        }
        if (update.profileImagePresent()) {
            if (update.profileImageId() == null) {
                profileImages.detach(memberId);
            } else {
                profileImages.attach(memberId, update.profileImageId());
            }
        }
        return toProfile(member);
    }

    private MyProfile toProfile(Member member) {
        long memberId = member.getId();
        Instant availableAt = member.nicknameChangeAvailableAt(nicknameChangeInterval);
        if (availableAt != null && !availableAt.isAfter(clock.instant())) {
            availableAt = null;
        }
        String url =
                profileImageQuery
                        .currentKeys(memberId)
                        .map(ProfileImageKeys::display)
                        .map(imageUrlResolver::publicUrl)
                        .orElse(null);
        return new MyProfile(
                member.getHandle(),
                member.getNickname(),
                member.getBio(),
                profileImages.currentImageId(memberId).orElse(null),
                url,
                availableAt);
    }
}
