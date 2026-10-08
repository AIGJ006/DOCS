package com.team.blog.discovery.application;

import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.cursor.ListScope;
import com.team.blog.tag.application.TagQueryService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 태그별 글 목록 (008 T033, US2, research R5·R6). 홈과 같은 카드·9개·정렬·커서이고 목록 구분은 {@code tag:{name}}이다.
 *
 * <ul>
 *   <li>이름이 정규화 결과와 다르거나 형식이 틀리면 404 — API는 리다이렉트하지 않는다.
 *   <li>태그가 없으면 카드 SQL 없이 빈 페이지 — "있는데 공개 글이 없는 태그"와 응답이 같다(SC-005). 커서가 있으면 먼저 {@code
 *       tag:{name}}으로 검사해 다른 목록의 커서는 그대로 400이다.
 *   <li>보는 사람과 상관없이 전체 공개 글만이다(FR-022) — 로그인했어도 {@link Viewer#anonymous()}로 카드를 읽는다.
 * </ul>
 *
 * SQL은 태그 번호 1번 + 카드 1번.
 */
@Service
@Transactional(readOnly = true)
public class TagPostQueryService {

    private final TagQueryService tags;
    private final PostListService lists;
    private final PostListCursor cursors;

    public TagPostQueryService(
            TagQueryService tags, PostListService lists, PostListCursor cursors) {
        this.tags = tags;
        this.lists = lists;
        this.cursors = cursors;
    }

    /**
     * @throws NotFoundException 정규화되지 않은 이름·형식 오류
     */
    public CursorPage<PostCardView> page(String name, String cursor) {
        tags.requireCanonical(name);
        ListScope scope = ListScope.tag(name);
        Optional<Long> tagId = tags.findIdByName(name);
        if (tagId.isEmpty()) {
            cursors.decode(cursor, scope);
            return new CursorPage<>(List.of(), null);
        }
        return lists.page(scope, new CardFilter(null, tagId.get()), cursor, Viewer.anonymous());
    }
}
