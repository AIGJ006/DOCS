package com.team.blog.post.application;

import com.team.blog.post.domain.PostAccessPolicy;
import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.post.infra.PostDetailRow;
import com.team.blog.post.infra.PostQueryRepository;
import com.team.blog.shared.security.Viewer;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 상세 조회 (005 T036, FR-026 ②③, research R-11·R-12·R-22).
 *
 * <ol>
 *   <li>② 글 번호가 숫자가 아니거나 {@code long} 범위를 넘으면 {@link PostNotFoundException} — 400이 아니다.
 *   <li>③ 글이 없거나 {@link PostAccessPolicy#canRead}가 false면 <b>같은</b> 404 (이유를 구분하지 않는다).
 *   <li>조회와 판정이 쿼리 한 번이다 — {@code PostReadService.requireReadable}을 따로 부르지 않는다.
 * </ol>
 *
 * <p>이 서비스는 {@code view_count}를 바꾸지 않는다(조회 기록은 009 {@code POST /api/posts/{id}/views}).
 */
@Service
@Transactional(readOnly = true)
public class PostQueryService {

    private static final Logger log = LoggerFactory.getLogger(PostQueryService.class);

    private static final Pattern DIGITS = Pattern.compile("^[0-9]+$");

    private final PostQueryRepository posts;
    private final PostAccessPolicy policy;
    private final PostDetailAssembler assembler;

    public PostQueryService(
            PostQueryRepository posts, PostAccessPolicy policy, PostDetailAssembler assembler) {
        this.posts = posts;
        this.policy = policy;
        this.assembler = assembler;
    }

    /**
     * @param rawPostId 주소에서 온 글 번호 문자열
     * @throws PostNotFoundException 숫자가 아님·없음·볼 수 없음 (이유 구분 없음)
     */
    public PostDetailView getDetail(String rawPostId, Viewer viewer) {
        return detailOf(requireReadableRow(rawPostId, viewer), viewer);
    }

    /** 이미 읽어 둔 행으로 상세 응답을 만든다 (컨트롤러가 헤더에 쓸 상태를 먼저 본 경우 — 조회를 두 번 하지 않는다). */
    public PostDetailView detailOf(PostDetailRow row, Viewer viewer) {
        return assembler.toView(row, viewer);
    }

    /**
     * 상세와 같은 조회·판정 (005 T038 화면 첫 응답이 {@code Cache-Control}과 메타에 쓴다).
     *
     * @throws PostNotFoundException 숫자가 아님·없음·볼 수 없음 (이유 구분 없음)
     */
    public PostDetailRow requireReadableRow(String rawPostId, Viewer viewer) {
        long postId = parseId(rawPostId);
        PostDetailRow row = posts.findDetailRow(postId).orElse(null);
        if (row == null) {
            log.debug("글 {}: 없음", postId);
            throw new PostNotFoundException("post not found: " + postId);
        }
        if (!policy.canRead(row.toPostView(), viewer)) {
            log.debug("글 {}: 볼 수 없음", postId);
            throw new PostNotFoundException("post not readable: " + postId);
        }
        return row;
    }

    private static long parseId(String rawPostId) {
        if (rawPostId == null || !DIGITS.matcher(rawPostId).matches()) {
            throw new PostNotFoundException("post id is not a number: " + rawPostId);
        }
        try {
            return Long.parseLong(rawPostId);
        } catch (NumberFormatException e) {
            throw new PostNotFoundException("post id is out of range: " + rawPostId);
        }
    }
}
