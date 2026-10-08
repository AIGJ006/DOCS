package com.team.blog.account.web;

import com.team.blog.account.application.RestoreService;
import com.team.blog.account.application.WithdrawalPreview;
import com.team.blog.account.application.WithdrawalPreviewService;
import com.team.blog.account.application.WithdrawalService;
import com.team.blog.account.web.dto.RestoreResult;
import com.team.blog.account.web.dto.WithdrawRequest;
import com.team.blog.account.web.dto.WithdrawResult;
import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 탈퇴·복구 API (015 contracts/openapi.yaml). 대상은 로그인한 본인뿐이다. 응답은 모두 {@code Cache-Control:
 * no-store}. 유예 회원은 001 게이트 필터가 {@code POST /api/me/restore} 밖을 403으로 막는다.
 */
@RestController
@RequestMapping("/api/me")
public class WithdrawalController {

    private final WithdrawalPreviewService previewService;
    private final WithdrawalService withdrawalService;
    private final RestoreService restoreService;

    public WithdrawalController(
            WithdrawalPreviewService previewService,
            WithdrawalService withdrawalService,
            RestoreService restoreService) {
        this.previewService = previewService;
        this.withdrawalService = withdrawalService;
        this.restoreService = restoreService;
    }

    /** 탈퇴 안내 숫자 ({@code getWithdrawalPreview}). */
    @GetMapping("/withdrawal")
    public ResponseEntity<WithdrawalPreview> preview(@CurrentUser Long memberId) {
        return noStore(previewService.preview(memberId));
    }

    /**
     * 탈퇴 신청 ({@code withdraw}). 성공하면 지금 요청의 세션도 무효화한다 — Spring Session은 요청 끝에 세션을 다시 저장하므로 저장소에서 지운
     * 것만으로는 지금 세션이 되살아난다(research R4).
     */
    @PostMapping("/withdraw")
    public ResponseEntity<WithdrawResult> withdraw(
            @CurrentUser Long memberId,
            @RequestBody WithdrawRequest body,
            HttpServletRequest request) {
        if (body == null) {
            throw new ApiException(CommonReasonCode.MALFORMED_REQUEST);
        }
        Instant deadline = withdrawalService.withdraw(memberId, body.toCommand());
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException alreadyInvalid) {
                // 저장소에서 이미 지워져 무효인 세션
            }
        }
        SecurityContextHolder.clearContext();
        return noStore(new WithdrawResult(deadline));
    }

    /** 복구 ({@code restore}) — 허용 목록 경로. 이미 활동 중이면 변화 없이 200. */
    @PostMapping("/restore")
    public ResponseEntity<RestoreResult> restore(@CurrentUser Long memberId) {
        restoreService.restore(memberId);
        return noStore(RestoreResult.active());
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
