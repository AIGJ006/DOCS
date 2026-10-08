package com.team.blog.post.application;

import com.team.blog.post.domain.InvalidVisibilityException;
import com.team.blog.post.domain.ManageCursor;
import com.team.blog.post.domain.ManageTab;
import com.team.blog.post.domain.Visibility;
import com.team.blog.post.infra.ManagePostQueryRepository;
import com.team.blog.post.infra.ManagePostRow;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import com.team.blog.shared.web.cursor.CursorCodec;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 글 관리 목록 (006 T042, US3, FR-001~012, research R16~R18).
 *
 * <p>판정 순서: 401(컨트롤러) → 403 계정 상태({@link ActionKind#CONTENT_CLEANUP} — 이메일 인증 전 허용, 탈퇴 유예·정지 403) →
 * 400 {@code INVALID_VISIBILITY}(발행 글 탭만) → 400 {@code INVALID_CURSOR}. 조회 대상은 언제나 현재 회원 본인
 * 글이다(FR-002).
 *
 * <p>SQL은 첫 요청이면 목록 1번 + 글 수 1번, 이어 보기면 목록 1번이다(41 §6). 페이지 크기는 설정값이며 클라이언트 값은 받지 않는다(FR-006).
 */
@Service
public class ManagePostQueryService {

    private static final Map<String, Visibility> FILTERS =
            Map.of("public", Visibility.PUBLIC, "private", Visibility.PRIVATE);

    private final AccountStatusGuard accountStatusGuard;
    private final ManagePostQueryRepository repository;
    private final CursorCodec codec;
    private final ManageProperties manageProperties;
    private final TrashProperties trashProperties;

    public ManagePostQueryService(
            AccountStatusGuard accountStatusGuard,
            ManagePostQueryRepository repository,
            CursorCodec codec,
            ManageProperties manageProperties,
            TrashProperties trashProperties) {
        this.accountStatusGuard = accountStatusGuard;
        this.repository = repository;
        this.codec = codec;
        this.manageProperties = manageProperties;
        this.trashProperties = trashProperties;
    }

    /**
     * @param visibilityFilter {@code public|private} (없거나 빈 값이면 전체). 발행 글 탭에서만 쓰고 다른 탭에서는 무시한다
     * @param rawCursor 이전 응답의 {@code nextCursor} (없거나 빈 값이면 첫 페이지)
     */
    @Transactional(readOnly = true)
    public ManagePostList list(long me, ManageTab tab, String visibilityFilter, String rawCursor) {
        accountStatusGuard.requireActive(me, ActionKind.CONTENT_CLEANUP);

        String filter = null;
        Visibility visibility = null;
        if (tab == ManageTab.PUBLISHED) {
            if (visibilityFilter == null || visibilityFilter.isEmpty()) {
                filter = ManageCursor.ALL;
            } else {
                visibility = FILTERS.get(visibilityFilter);
                if (visibility == null) {
                    throw new InvalidVisibilityException("visibility");
                }
                filter = visibilityFilter;
            }
        }

        boolean first = rawCursor == null || rawCursor.isEmpty();
        ManageCursor after = first ? null : ManageCursor.decode(rawCursor, tab, filter, codec);

        int pageSize = manageProperties.pageSize();
        List<ManagePostRow> rows = repository.list(me, tab, visibility, after, pageSize + 1);
        boolean hasNext = rows.size() > pageSize;
        List<ManagePostRow> page = hasNext ? rows.subList(0, pageSize) : rows;

        String nextCursor = null;
        if (hasNext) {
            ManagePostRow last = page.get(page.size() - 1);
            nextCursor =
                    new ManageCursor(
                                    tab,
                                    filter,
                                    tab == ManageTab.TRASH ? last.deletedAt() : last.updatedAt(),
                                    last.id())
                            .encode(codec);
        }

        ManagePostList.Counts counts = null;
        if (first) {
            Map<ManageTab, Long> byTab = repository.count(me);
            counts =
                    new ManagePostList.Counts(
                            byTab.get(ManageTab.DRAFTS),
                            byTab.get(ManageTab.PUBLISHED),
                            byTab.get(ManageTab.TRASH));
        }

        List<ManagePostList.Item> items =
                page.stream()
                        .map(
                                row ->
                                        new ManagePostList.Item(
                                                row,
                                                row.deletedAt() == null
                                                        ? null
                                                        : row.deletedAt()
                                                                .plus(trashProperties.retention())))
                        .toList();
        return new ManagePostList(items, nextCursor, counts);
    }
}
