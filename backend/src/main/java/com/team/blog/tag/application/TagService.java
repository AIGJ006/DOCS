package com.team.blog.tag.application;

import com.team.blog.shared.error.FieldError;
import com.team.blog.tag.domain.TagNormalization;
import com.team.blog.tag.domain.TagNormalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * 글-태그 연결과 발행 태그 검증 (008, data-model §1·§2, research R3). 정규화 규칙은 {@link TagNormalizer} 하나다 — 002가
 * 둔 임시 구현(T046)을 넘겨받아 공개 메서드 이름·서명은 그대로 두고 안의 규칙만 바꿨다.
 *
 * <p><b>교체 점검표 (002 T122)</b> — 이 클래스를 고칠 때 지켜야 할 계약과 그것을 확인하는 테스트:
 *
 * <ul>
 *   <li>오류는 입력 칸 번호로 {@code tags[i]} 필드에 붙고(정리·중복 제거 전 입력 순서), 코드는 {@code INVALID_TAG}·{@code
 *       TAG_TOO_LONG}·{@code TAG_BANNED_WORD}({@code tag.domain.TagReasonCode}) — 화면이 그 칩 옆에 보인다 —
 *       {@code PublishValidatorTest#형식이_틀린_태그는_그_칸_번호로_INVALID_TAG}, {@code
 *       PublishValidatorTest#여러_항목이_틀리면_모두_모은다}, {@code TagPublishIT#문제_태그를_모두_한_번에_알려준다}, 프런트
 *       {@code PublishDialog.test.tsx}("400 칸 오류를 모두 보인다")
 *   <li>개수 제한({@code TOO_MANY_TAGS}, {@code blog.post.max-tags})은 중복 제거 후 개수로 발행 검증이 보고, 칸 오류와 함께
 *       모은다 — {@code PublishValidatorTest#태그_11개는_TOO_MANY_TAGS_10개는_통과}·{@code
 *       #대소문자_중복은_하나로_센다}·{@code #개수_초과와_칸_오류를_함께_모은다}
 *   <li>{@code replacePostTags}(발행 트랜잭션 안): 입력 순서대로 {@code position} 0부터, 태그 수와 상관없이 쿼리 3번 — {@code
 *       PublishIT#임시글을_전체_공개로_발행하면_누구나_읽는다}, {@code
 *       PublishQueryCountIT#발행_SQL_수는_태그_사진_수에_비례하지_않는다}, 같은 새 태그 동시 생성은 {@code ON CONFLICT}로 하나 —
 *       {@code TagPublishIT#같은_새_태그로_동시에_10건_발행해도_태그는_하나}
 *   <li>트랜잭션이 실패하면 태그도 되돌리고 같은 요청 키로 다시 발행할 수 있다 — {@code PublishTransactionIT}, {@code
 *       PublishIdempotencyIT#트랜잭션이_실패하면_키가_풀려_같은_키_같은_내용으로_다시_발행할_수_있다}
 * </ul>
 */
@Service
public class TagService {

    private final JdbcClient jdbc;
    private final TagNormalizer normalizer;

    public TagService(JdbcClient jdbc, TagNormalizer normalizer) {
        this.jdbc = jdbc;
        this.normalizer = normalizer;
    }

    /** 받아들인 태그만 정규화·중복 제거해 입력 순서대로 돌려준다(처음 것만 남김). 거부된 칸은 빠진다 — 검사는 {@link #validate}. */
    public List<String> normalizeAll(List<String> rawTags) {
        if (rawTags == null) {
            return List.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String raw : rawTags) {
            if (normalizer.normalize(raw) instanceof TagNormalization.Accepted accepted) {
                names.add(accepted.name());
            }
        }
        return List.copyOf(names);
    }

    /**
     * 칸별 검사. 거부된 입력 칸마다 {@code tags[i]}(i는 화면이 보낸 원래 칸 번호) 오류 한 건. 개수 제한은 발행 검증({@code
     * PublishValidator})이 본다. 금칙어 거부는 어떤 단어인지 담지 않는다.
     */
    public List<FieldError> validate(List<String> rawTags) {
        List<FieldError> errors = new ArrayList<>();
        if (rawTags == null) {
            return errors;
        }
        for (int i = 0; i < rawTags.size(); i++) {
            if (normalizer.normalize(rawTags.get(i))
                    instanceof TagNormalization.Rejected rejected) {
                errors.add(rejected.code().fieldError("tags[" + i + "]"));
            }
        }
        return errors;
    }

    /**
     * 글의 태그를 바꾼다 (발행 트랜잭션 안). 새 이름 묶음 INSERT 1회 → 기존 연결 삭제 → 입력 순서대로 {@code position} 0부터 묶음
     * INSERT. 태그 수와 상관없이 쿼리 3번(N+1 없음).
     */
    public void replacePostTags(long postId, List<String> rawTags) {
        List<String> names = normalizeAll(rawTags);
        jdbc.sql("DELETE FROM post_tag WHERE post_id = ?").param(postId).update();
        if (names.isEmpty()) {
            return;
        }
        String[] array = names.toArray(String[]::new);
        jdbc.sql(
                        "INSERT INTO tag (name) SELECT unnest(CAST(:names AS varchar[]))"
                                + " ON CONFLICT (name) DO NOTHING")
                .param("names", array)
                .update();
        jdbc.sql(
                        """
                        INSERT INTO post_tag (post_id, tag_id, position)
                        SELECT :postId, t.id, (n.ord - 1)
                          FROM unnest(CAST(:names AS varchar[])) WITH ORDINALITY AS n(name, ord)
                          JOIN tag t ON t.name = n.name
                        """)
                .param("postId", postId)
                .param("names", array)
                .update();
    }

    /** 글의 태그 이름 ({@code position} 순서). */
    public List<String> tagNamesOf(long postId) {
        return jdbc.sql(
                        "SELECT t.name FROM post_tag pt JOIN tag t ON t.id = pt.tag_id"
                                + " WHERE pt.post_id = ? ORDER BY pt.position")
                .param(postId)
                .query(String.class)
                .list();
    }
}
