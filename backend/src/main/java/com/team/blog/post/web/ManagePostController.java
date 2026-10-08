package com.team.blog.post.web;

import com.team.blog.post.application.ManagePostQueryService;
import com.team.blog.post.domain.ManageTab;
import com.team.blog.post.web.dto.ManagePostPage;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.web.CacheControlPolicy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 글 관리 목록 (006 contracts {@code listMyPosts}·{@code listMyTrash}, research R15). 조회 대상 회원은 세션에서만
 * 꺼낸다 — {@code authorId} 같은 요청 값은 선언하지 않는다(FR-002). 페이지 크기 요청 값도 받지 않는다(FR-006). 이메일 인증 전 회원도 볼 수
 * 있다(42 §10). 본인만 보는 목록이라 {@code Cache-Control: private, no-store}.
 */
@RestController
public class ManagePostController {

    private final ManagePostQueryService queryService;

    public ManagePostController(ManagePostQueryService queryService) {
        this.queryService = queryService;
    }

    /** {@code tab}이 없으면 임시글, 모르는 값이면 400 {@code INVALID_TAB}. {@code visibility}는 발행 글 탭에서만 쓴다. */
    @GetMapping("/api/me/posts")
    public ResponseEntity<ManagePostPage> list(
            @CurrentUser Long memberId,
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String cursor) {
        return page(memberId, ManageTab.fromParam(tab), visibility, cursor);
    }

    /** 휴지통 목록 별칭 (13 §2-4·02 §5-1). {@code tab=trash}로 고정한 같은 조회다. */
    @GetMapping("/api/me/trash")
    public ResponseEntity<ManagePostPage> trash(
            @CurrentUser Long memberId, @RequestParam(required = false) String cursor) {
        return page(memberId, ManageTab.TRASH, null, cursor);
    }

    private ResponseEntity<ManagePostPage> page(
            Long memberId, ManageTab tab, String visibility, String cursor) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControlPolicy.NO_STORE)
                .body(ManagePostPage.from(queryService.list(memberId, tab, visibility, cursor)));
    }
}
