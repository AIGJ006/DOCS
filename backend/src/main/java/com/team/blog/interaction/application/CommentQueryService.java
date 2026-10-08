package com.team.blog.interaction.application;

import com.team.blog.interaction.infra.CommentQueryRepository;
import com.team.blog.interaction.infra.CommentQueryRepository.Replies;
import com.team.blog.interaction.infra.CommentRow;
import com.team.blog.post.application.PostReadService;
import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.infra.PostView;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.CacheControlPolicy;
import com.team.blog.shared.web.cursor.ListScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 조회 공개 Service (007 T021·T032·T052, research R8·R11). 006이 만든 {@link
 * #commentIdsOfPost(long)}(신고 종료 단계용)를 넘겨받아 그대로 두고, 목록·답글 펼치기·바로 가기를 더했다.
 *
 * <p>보기 권한은 글 읽기 권한과 같다: 004 {@link PostReadService#requireReadable} + 발행됨(작성자 본인의 임시글도 404). 응답의
 * {@code Cache-Control}은 그 글 상태로 정한다(FR-015).
 */
@Service
@Transactional(readOnly = true)
public class CommentQueryService {

    private final NamedParameterJdbcTemplate jdbc;
    private final PostReadService posts;
    private final CommentQueryRepository comments;
    private final CommentViewAssembler assembler;
    private final CommentCursor cursors;
    private final CommentProperties properties;

    public CommentQueryService(
            NamedParameterJdbcTemplate jdbc,
            PostReadService posts,
            CommentQueryRepository comments,
            CommentViewAssembler assembler,
            CommentCursor cursors,
            CommentProperties properties) {
        this.jdbc = jdbc;
        this.posts = posts;
        this.comments = comments;
        this.assembler = assembler;
        this.cursors = cursors;
        this.properties = properties;
    }

    /** 응답 본문과 {@code Cache-Control}. */
    public record Result<T>(T body, String cacheControl) {}

    /** 그 글의 댓글 번호 전부 (답글·삭제 표시된 댓글 포함, 번호 순). 댓글이 없으면 빈 목록. 006 신고 종료 단계가 쓴다. */
    public List<Long> commentIdsOfPost(long postId) {
        return jdbc.queryForList(
                "SELECT id FROM comment WHERE post_id = :postId ORDER BY id",
                Map.of("postId", postId),
                Long.class);
    }

    /**
     * 댓글 목록 한 페이지. {@code cursor}가 있으면 그 위치(다음 또는 이전 방향), 없고 {@code around}가 그 글의 보이는 댓글이면 그 댓글의
     * 최상위부터, 그 밖에는 첫 페이지.
     */
    public Result<CommentPage> page(long postId, String cursor, String around, Viewer viewer) {
        PostView post = requirePublished(postId, viewer);
        String cache = CacheControlPolicy.forPost(post);
        ListScope scope = CommentCursor.commentsOf(postId);
        CommentCursor.Position position = cursors.decode(cursor, scope);
        if (position != null && position.previous()) {
            List<CommentRow> roots =
                    comments.findRootsBefore(
                            postId, position.createdAt(), position.id(), properties.pageSize());
            String prev = null;
            if (!roots.isEmpty()) {
                CommentRow first = roots.get(0);
                if (comments.hasRootsBefore(postId, first.createdAt(), first.id())) {
                    prev = cursors.previous(scope, first.createdAt(), first.id());
                }
            }
            return new Result<>(assemble(post, viewer, roots, null, prev, null), cache);
        }
        if (position == null) {
            Long aroundId = parseId(around);
            if (aroundId != null) {
                CommentPage page = around(post, viewer, aroundId);
                if (page != null) {
                    return new Result<>(page, cache);
                }
            }
        }
        List<CommentRow> rows =
                comments.findRoots(
                        postId,
                        position == null ? null : position.createdAt(),
                        position == null ? null : position.id(),
                        properties.pageSize() + 1);
        return new Result<>(pageOf(post, viewer, rows, null, null), cache);
    }

    /** 답글 펼치기 (20개씩). 최상위가 아니거나 없거나 그 글을 볼 수 없으면 404. */
    public Result<ReplyPage> replies(long rootId, String cursor, Viewer viewer) {
        CommentRow root =
                comments.find(rootId)
                        .filter(CommentRow::isRoot)
                        .orElseThrow(() -> new PostNotFoundException("답글 펼치기: 최상위 아님"));
        PostView post = requirePublished(root.postId(), viewer);
        ListScope scope = CommentCursor.repliesOf(rootId);
        CommentCursor.Position position = cursors.decode(cursor, scope);
        if (position != null && position.previous()) {
            throw new com.team.blog.shared.web.cursor.InvalidCursorException("replies prev");
        }
        int size = properties.replyPageSize();
        List<CommentRow> rows =
                new ArrayList<>(
                        comments.findReplies(
                                rootId,
                                position == null ? null : position.createdAt(),
                                position == null ? null : position.id(),
                                size + 1));
        String next = null;
        if (rows.size() > size) {
            rows = new ArrayList<>(rows.subList(0, size));
            CommentRow last = rows.get(size - 1);
            next = cursors.next(scope, last.createdAt(), last.id());
        }
        CommentViewAssembler.Batch batch = assembler.prepare(post.authorId(), viewer, rows);
        List<CommentView> items = rows.stream().map(batch::view).toList();
        return new Result<>(new ReplyPage(items, next), CacheControlPolicy.forPost(post));
    }

    private PostView requirePublished(long postId, Viewer viewer) {
        PostView post = posts.requireReadable(postId, viewer);
        if (post.status() != PostStatus.PUBLISHED) {
            throw new PostNotFoundException("댓글: 발행되지 않은 글");
        }
        return post;
    }

    /** 최상위 {@code pageSize + 1}개까지 받은 행으로 페이지를 만든다. */
    private CommentPage pageOf(
            PostView post, Viewer viewer, List<CommentRow> fetched, String prev, Long focus) {
        int size = properties.pageSize();
        List<CommentRow> roots = fetched;
        String next = null;
        if (fetched.size() > size) {
            roots = fetched.subList(0, size);
            CommentRow last = roots.get(size - 1);
            next = cursors.next(CommentCursor.commentsOf(post.id()), last.createdAt(), last.id());
        }
        return assemble(post, viewer, roots, next, prev, focus);
    }

    private CommentPage assemble(
            PostView post,
            Viewer viewer,
            List<CommentRow> roots,
            String next,
            String prev,
            Long focus) {
        return assemble(post, viewer, roots, next, prev, focus, Map.of());
    }

    /** 최상위 + 답글 미리보기를 조립한다. {@code expanded}에 든 최상위는 미리보기 대신 그 답글 목록(바로 가기 대상까지)을 쓴다. */
    private CommentPage assemble(
            PostView post,
            Viewer viewer,
            List<CommentRow> roots,
            String next,
            String prev,
            Long focus,
            Map<Long, Replies> expanded) {
        List<Long> rootIds = roots.stream().map(CommentRow::id).toList();
        Map<Long, Replies> previews =
                new java.util.HashMap<>(
                        comments.findReplyPreviews(
                                rootIds.stream().filter(id -> !expanded.containsKey(id)).toList(),
                                properties.replyPreview()));
        previews.putAll(expanded);
        List<CommentRow> all = new ArrayList<>(roots);
        previews.values().forEach(r -> all.addAll(r.rows()));
        CommentViewAssembler.Batch batch = assembler.prepare(post.authorId(), viewer, all);
        List<RootCommentView> items = new ArrayList<>(roots.size());
        for (CommentRow root : roots) {
            Replies replies = previews.get(root.id());
            List<CommentView> replyViews =
                    replies == null ? List.of() : replies.rows().stream().map(batch::view).toList();
            int total = replies == null ? 0 : replies.total();
            String repliesNext = null;
            if (replies != null && total > replies.rows().size() && !replies.rows().isEmpty()) {
                CommentRow last = replies.rows().get(replies.rows().size() - 1);
                repliesNext =
                        cursors.next(
                                CommentCursor.repliesOf(root.id()), last.createdAt(), last.id());
            }
            items.add(RootCommentView.of(batch.view(root), total, replyViews, repliesNext));
        }
        return new CommentPage(items, next, prev, focus);
    }

    /**
     * 바로 가기 (research R11). 대상이 그 글의 보이는 댓글(정상, 또는 보는 사람 자신의 숨김 댓글 — 작성자 탈퇴 아님)이 아니면 {@code null} —
     * 호출한 쪽이 첫 페이지를 준다(응답 모양이 같아 무시 여부가 드러나지 않음).
     */
    private CommentPage around(PostView post, Viewer viewer, long targetId) {
        CommentRow target = comments.findForAround(targetId, post.id()).orElse(null);
        if (target == null || target.isDeleted()) {
            return null;
        }
        if (target.isHidden() && !viewer.isAuthorOf(target.authorId())) {
            return null;
        }
        if (!assembler.isActiveAuthor(target.authorId())) {
            return null;
        }
        CommentRow root =
                target.isRoot()
                        ? target
                        : comments.findForAround(target.parentId(), post.id()).orElse(null);
        if (root == null) {
            return null;
        }
        ListScope scope = CommentCursor.commentsOf(post.id());
        List<CommentRow> fetched =
                comments.findRootsFrom(
                        post.id(), root.createdAt(), root.id(), properties.pageSize() + 1);
        String prev =
                comments.hasRootsBefore(post.id(), root.createdAt(), root.id())
                        ? cursors.previous(scope, root.createdAt(), root.id())
                        : null;
        Map<Long, Replies> expanded = Map.of();
        long focus = target.id();
        if (!target.isRoot()) {
            int cap = properties.aroundMaxReplies();
            List<CommentRow> replies = comments.findReplies(root.id(), null, null, cap);
            int index = -1;
            for (int i = 0; i < replies.size(); i++) {
                if (replies.get(i).id() == target.id()) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                focus = root.id();
            } else if (index >= properties.replyPreview()) {
                int total = comments.countReplies(root.id());
                expanded =
                        Map.of(
                                root.id(),
                                new Replies(total, new ArrayList<>(replies.subList(0, index + 1))));
            }
        }
        int size = properties.pageSize();
        List<CommentRow> roots = fetched.size() > size ? fetched.subList(0, size) : fetched;
        String next = null;
        if (fetched.size() > size) {
            CommentRow last = roots.get(size - 1);
            next = cursors.next(scope, last.createdAt(), last.id());
        }
        return assemble(post, viewer, roots, next, prev, focus, expanded);
    }

    private static Long parseId(String value) {
        if (value == null || value.isEmpty() || value.length() > 18) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') {
                return null;
            }
        }
        long id = Long.parseLong(value);
        return id < 1 ? null : id;
    }
}
