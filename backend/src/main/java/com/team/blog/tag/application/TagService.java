package com.team.blog.tag.application;

import com.team.blog.post.domain.PostReasonCode;
import com.team.blog.shared.error.FieldError;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * 태그 임시 구현 (002 T046, data-model §1-3).
 *
 * <p>// TODO(008): 008 정규화(NFC·금칙어·{@code TAG_TOO_LONG} 등)로 교체. 지금은 소문자·앞뒤 공백 제거·중복 제거와 DB {@code
 * ck_tag_name} 형식만 본다.
 */
@Service
public class TagService {

    /** DB {@code ck_tag_name}과 같은 형식: 허용 글자 1~30자 + 한글·영문 소문자·숫자 하나 이상. */
    private static final Pattern NAME = Pattern.compile("[가-힣a-z0-9._+#-]{1,30}");

    private static final Pattern MEANINGFUL = Pattern.compile(".*[가-힣a-z0-9].*");

    private final JdbcClient jdbc;

    public TagService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 태그 하나 정규화: 앞뒤 공백 제거 + 소문자. */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }

    /** 정규화·중복 제거한 태그 (입력 순서 유지). 형식 검사는 하지 않는다. */
    public List<String> normalizeAll(List<String> rawTags) {
        if (rawTags == null) {
            return List.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String raw : rawTags) {
            names.add(normalize(raw));
        }
        return List.copyOf(names);
    }

    /**
     * 형식 검사. 위반한 입력 칸마다 {@code tags[i]/INVALID_TAG} (i는 입력 순서 번호). 개수 제한은 발행 검증({@code
     * PublishValidator})이 본다.
     */
    public List<FieldError> validate(List<String> rawTags) {
        List<FieldError> errors = new ArrayList<>();
        if (rawTags == null) {
            return errors;
        }
        for (int i = 0; i < rawTags.size(); i++) {
            if (!isValidName(normalize(rawTags.get(i)))) {
                errors.add(PostReasonCode.INVALID_TAG.fieldError("tags[" + i + "]"));
            }
        }
        return errors;
    }

    static boolean isValidName(String name) {
        return NAME.matcher(name).matches() && MEANINGFUL.matcher(name).matches();
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
