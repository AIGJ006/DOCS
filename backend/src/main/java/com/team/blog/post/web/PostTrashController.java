package com.team.blog.post.web;

import com.team.blog.post.application.PostTrashService;
import com.team.blog.post.web.dto.RestoreResponse;
import com.team.blog.post.web.dto.TrashResponse;
import com.team.blog.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 글 삭제·복구·영구 삭제 (006 contracts {@code trashPost}·{@code restorePost}·{@code purgePost}). 현재 사용자는
 * 세션에서만 꺼낸다. 계정 상태는 서비스가 {@code CONTENT_CLEANUP}으로 본다 — 이메일 인증 전 회원도 자기 글을 지울 수 있어야 한다(FR-037).
 */
@RestController
public class PostTrashController {

    private final PostTrashService trashService;

    public PostTrashController(PostTrashService trashService) {
        this.trashService = trashService;
    }

    /** 휴지통으로 (빈 임시글은 바로 완전 삭제). 1 미만인 번호는 없는 글이라 404. */
    @DeleteMapping("/api/posts/{postId}")
    public TrashResponse trash(@CurrentUser Long memberId, @PathVariable long postId) {
        return TrashResponse.from(trashService.trash(memberId, postId));
    }

    /** 휴지통에서 복구. 확인창 없이 부른다(FR-027). 휴지통에 없는 글은 404. */
    @PostMapping("/api/posts/{postId}/restore")
    public RestoreResponse restore(@CurrentUser Long memberId, @PathVariable long postId) {
        return RestoreResponse.from(trashService.restore(memberId, postId));
    }

    /** 휴지통 글 영구 삭제 (확인창은 화면이 띄운다, FR-029). 휴지통에 없는 글은 404. 이메일 인증 전에도 허용한다. */
    @DeleteMapping("/api/posts/{postId}/permanent")
    public TrashResponse.Purged purge(@CurrentUser Long memberId, @PathVariable long postId) {
        trashService.purgePermanently(memberId, postId);
        return TrashResponse.Purged.INSTANCE;
    }
}
