package com.team.blog.post.application;

import com.team.blog.post.domain.PostAccessPolicy;
import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.infra.PostDetailRow;
import com.team.blog.post.infra.PostQueryRepository;
import com.team.blog.shared.security.Viewer;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 상세 조회 (005 T036, FR-026 ②③, research R-11·R-12·R-22).
 *
 * <ol>
 *   <li>② 글 번호가 숫자가 아니거나 {@code long} 범위를 넘으면 {@link PostNotFoundException} — 400이 아니다.
 *   <li>③ 글이 없거나 {@link PostAccessPolicy#canRead}가 false면 <b>같은</b> 404 (이유를 구분하지 않는다).
 *   <li>⑤ 판정을 통과한 임시글은 작성자 본인뿐이다 — 본문 없이 {@link PostDetailView#draft}(에디터 주소)만 준다(005 T056,
 *       research R-22). 화면 주소는 같은 경우 {@code 302 /write/{id}}로 보낸다.
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
    public PostDetailResponse getDetail(String rawPostId, Viewer viewer) {
        return detailOf(requireReadableRow(rawPostId, viewer), viewer);
    }

    /**
     * 이미 읽어 둔 행으로 상세 응답을 만든다 (컨트롤러가 헤더에 쓸 상태를 먼저 본 경우 — 조회를 두 번 하지 않는다).
     *
     * <p>트랜잭션 밖에서 조립한다: 부가 정보(태그·좋아요·팔로우·작업본) 조회는 각 Service의 짧은 읽기 트랜잭션에서 돌고, 그중 하나가 실패해도 함께 쓰는
     * 트랜잭션이 rollback-only가 되어 응답 전체가 500이 되지 않게 한다(원칙 V, research R-30).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PostDetailResponse detailOf(PostDetailRow row, Viewer viewer) {
        if (isAuthorDraft(row, viewer)) {
            return PostDetailView.draft(row.id(), editorPath(row.id()));
        }
        return assembler.toView(row, viewer);
    }

    /** ⑤ 작성자 본인이 자기 임시글을 열었는가 (FR-026 ⑤). {@code canRead}가 임시글을 작성자에게만 허용하므로 판정 뒤에만 부른다. */
    public static boolean isAuthorDraft(PostDetailRow row, Viewer viewer) {
        return row.status() == PostStatus.DRAFT && viewer.isAuthorOf(row.authorId());
    }

    /** 에디터 주소 {@code /write/{postId}} (002 화면 경로). */
    public static String editorPath(long postId) {
        return "/write/" + postId;
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
