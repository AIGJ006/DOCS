package com.team.blog.category.application;

import com.team.blog.category.domain.CategoryName;
import com.team.blog.category.domain.CategoryReasonCode;
import com.team.blog.category.infra.CategoryRepository;
import com.team.blog.category.infra.CategoryRepository.CategoryRow;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카테고리 만들기·수정·순서·삭제 (017 US1, FR-002~FR-015, research R2~R4).
 *
 * <p>판정 순서: ② 계정 상태(001 {@link AccountStatusGuard} {@code CONTENT_WRITE} — 인증 전·정지·탈퇴 유예 403) → 회원
 * 행 잠금(같은 회원의 쓰기를 한 줄로) → ③④ 대상(남의 것·없는 것 같은 404) → ⑤ 규칙(400·409). 로그인 확인(401)은 컨트롤러의
 * {@code @CurrentUser}가 한다.
 */
@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final AccountStatusGuard accountStatusGuard;
    private final CategoryRepository categories;
    private final CategoryProperties properties;
    private final CategoryQueryService queries;

    public CategoryService(
            AccountStatusGuard accountStatusGuard,
            CategoryRepository categories,
            CategoryProperties properties,
            CategoryQueryService queries) {
        this.accountStatusGuard = accountStatusGuard;
        this.categories = categories;
        this.properties = properties;
        this.queries = queries;
    }

    /** 만들기 — 상위의 맨 아래에 붙는다 (FR-008). */
    @Transactional
    public CategoryView create(long memberId, String rawName, Long parentId) {
        begin(memberId);
        CategoryName name = CategoryName.of(rawName);
        if (parentId != null) {
            requireTopLevelParent(memberId, parentId, null);
        }
        if (categories.countByMember(memberId) >= properties.maxCount()) {
            throw new BusinessRuleException(CategoryReasonCode.TOO_MANY_CATEGORIES);
        }
        requireUniqueName(memberId, parentId, name, null);
        int position = categories.nextPosition(memberId, parentId);
        long id =
                saveGuarded(
                        () ->
                                categories.insert(
                                        memberId, parentId, name.value(), name.key(), position));
        log.info("카테고리 만듦: memberId={} id={} parentId={}", memberId, id, parentId);
        return new CategoryView(id, name.value(), parentId, position);
    }

    /**
     * 이름·상위 바꾸기 (FR-009). {@code rawName}이 {@code null}이면 이름은 그대로, {@code changeParent}가 거짓이면 상위는
     * 그대로다. 상위가 바뀌면 새 상위의 맨 아래로 간다.
     */
    @Transactional
    public CategoryView update(
            long memberId, long id, String rawName, boolean changeParent, Long parentId) {
        begin(memberId);
        CategoryRow current =
                categories
                        .findOwned(memberId, id)
                        .orElseThrow(() -> new NotFoundException("카테고리 수정: 내 것 아님"));
        CategoryName name =
                rawName == null ? CategoryName.of(current.name()) : CategoryName.of(rawName);
        Long newParent = changeParent ? parentId : current.parentId();
        boolean moved = !Objects.equals(newParent, current.parentId());
        if (moved && newParent != null) {
            requireTopLevelParent(memberId, newParent, id);
            if (categories.countChildren(id) > 0) {
                throw new BusinessRuleException(CategoryReasonCode.CATEGORY_DEPTH_EXCEEDED);
            }
        }
        requireUniqueName(memberId, newParent, name, id);
        int position = moved ? categories.nextPosition(memberId, newParent) : current.position();
        saveGuarded(
                () -> {
                    categories.update(id, newParent, name.value(), name.key(), position);
                    return id;
                });
        if (moved) {
            renumber(memberId, current.parentId());
        }
        return new CategoryView(id, name.value(), newParent, position);
    }

    /** 같은 상위 안 순서 저장 (FR-010) — 보낸 묶음이 지금 하위 묶음과 같아야 한다. */
    @Transactional
    public MyCategoriesView reorder(long memberId, Long parentId, List<Long> ids) {
        begin(memberId);
        if (parentId != null) {
            requireTopLevelParent(memberId, parentId, null);
        }
        List<Long> current = categories.siblingIds(memberId, parentId);
        List<Long> requested = ids == null ? List.of() : ids;
        if (requested.size() != current.size()
                || new HashSet<>(requested).size() != requested.size()
                || !new HashSet<>(current).equals(new HashSet<>(requested))) {
            throw new BusinessRuleException(CategoryReasonCode.CATEGORY_ORDER_STALE);
        }
        for (int i = 0; i < requested.size(); i++) {
            categories.updatePosition(requested.get(i), i);
        }
        return queries.myTree(memberId);
    }

    /** 지우기 (FR-011) — 하위가 있으면 409. 글은 {@code ON DELETE SET NULL}로 분류 없음이 된다. */
    @Transactional
    public void delete(long memberId, long id) {
        begin(memberId);
        CategoryRow current =
                categories
                        .findOwned(memberId, id)
                        .orElseThrow(() -> new NotFoundException("카테고리 삭제: 내 것 아님"));
        if (categories.countChildren(id) > 0) {
            throw new BusinessRuleException(CategoryReasonCode.CATEGORY_HAS_CHILDREN);
        }
        categories.delete(id);
        renumber(memberId, current.parentId());
        log.info("카테고리 지움: memberId={} id={}", memberId, id);
    }

    private void begin(long memberId) {
        accountStatusGuard.requireActive(memberId, ActionKind.CONTENT_WRITE);
        if (!categories.lockMember(memberId)) {
            throw new NotFoundException("카테고리: 회원 없음");
        }
    }

    /** 상위는 내 최상위 카테고리여야 한다. 남의 것·없는 것은 400 {@code INVALID_CATEGORY}, 하위·자기 자신이면 깊이 초과. */
    private void requireTopLevelParent(long memberId, long parentId, Long selfId) {
        if (selfId != null && parentId == selfId) {
            throw new BusinessRuleException(CategoryReasonCode.CATEGORY_DEPTH_EXCEEDED);
        }
        CategoryRow parent =
                categories
                        .findOwned(memberId, parentId)
                        .orElseThrow(() -> invalidCategory("parentId"));
        if (!parent.isTopLevel()) {
            throw new BusinessRuleException(CategoryReasonCode.CATEGORY_DEPTH_EXCEEDED);
        }
    }

    private void requireUniqueName(long memberId, Long parentId, CategoryName name, Long selfId) {
        if (categories.existsSiblingName(memberId, parentId, name.key(), selfId)) {
            throw new BusinessRuleException(CategoryReasonCode.CATEGORY_NAME_DUPLICATED);
        }
    }

    /** 떠난 자리를 메운다 — 0부터 다시 매긴다. */
    private void renumber(long memberId, Long parentId) {
        List<Long> ids = categories.siblingIds(memberId, parentId);
        for (int i = 0; i < ids.size(); i++) {
            categories.updatePosition(ids.get(i), i);
        }
    }

    /** 잠금이 막지 못한 유일 인덱스 위반도 같은 409로 (research R3). */
    private static long saveGuarded(java.util.function.LongSupplier write) {
        try {
            return write.getAsLong();
        } catch (DuplicateKeyException e) {
            throw new BusinessRuleException(CategoryReasonCode.CATEGORY_NAME_DUPLICATED);
        }
    }

    static BusinessRuleException invalidCategory(String field) {
        CategoryReasonCode code = CategoryReasonCode.INVALID_CATEGORY;
        return new BusinessRuleException(
                code, code.defaultMessage(), List.of(code.fieldError(field)), null);
    }
}
