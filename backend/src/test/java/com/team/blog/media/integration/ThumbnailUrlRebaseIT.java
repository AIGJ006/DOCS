package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.support.IntegrationTestBase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 공개 주소 변경 운영 SQL (003 T084 US8, quickstart §6 4단계, research R17). {@code
 * scripts/sql/rebase-image-urls.sql}을 읽어 psql 변수({@code :'old'}·{@code :'new'})를 값으로 바꿔 실행한다. 옛 주소로
 * 시작하는 {@code post.thumbnail_url}만 바뀐다.
 */
class ThumbnailUrlRebaseIT extends IntegrationTestBase {

    private static final String OLD = "http://old.example.com/blog";
    private static final String NEW = "https://img.example.com/blog2";

    static String script() throws IOException {
        Path path = Path.of("..", "scripts", "sql", "rebase-image-urls.sql");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** psql 메타 명령·주석을 빼고 변수를 SQL 문자열로 바꾼다. */
    static String asJdbc(String script, String old, String next) {
        StringBuilder sql = new StringBuilder();
        for (String line : script.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("\\") || trimmed.startsWith("--")) {
                continue;
            }
            sql.append(line).append('\n');
        }
        return sql.toString()
                .replace(":'old'", "'" + old.replace("'", "''") + "'")
                .replace(":'new'", "'" + next.replace("'", "''") + "'");
    }

    private long post(long author, String thumbnailUrl) {
        long id =
                new AuthoringFixtures(jdbc, redis)
                        .posts()
                        .post(author)
                        .published("PUBLIC")
                        .create();
        jdbc.update("UPDATE post SET thumbnail_url = ? WHERE id = ?", thumbnailUrl, id);
        return id;
    }

    private String thumbnail(long id) {
        return jdbc.queryForObject("SELECT thumbnail_url FROM post WHERE id = ?", String.class, id);
    }

    @Test
    void 옛_주소로_시작하는_썸네일만_바꾸고_바뀐_행_수를_낸다() throws IOException {
        long me = members().member().create();
        String key = "images/2026/10/0b6e1f5c-2a3d-4f7e-9c1a-5d8e7f6a1b2c_thumb.webp";
        long oldOne = post(me, OLD + "/" + key);
        long oldTwo = post(me, OLD + "/images/2025/01/11111111-2222-4333-8444-555555555555.png");
        long already = post(me, NEW + "/" + key);
        long lookalike = post(me, OLD + "x/" + key); // 옛 주소로 "시작"하지만 다른 경로
        long none = post(me, null);

        String sql = asJdbc(script(), OLD, NEW);
        List<Map<String, Object>> out =
                jdbc.queryForList(sql.substring(sql.indexOf("WITH")).split(";")[0]);

        assertThat(((Number) out.get(0).get("changed_rows")).longValue()).isEqualTo(2);
        assertThat(thumbnail(oldOne)).isEqualTo(NEW + "/" + key);
        assertThat(thumbnail(oldTwo))
                .isEqualTo(NEW + "/images/2025/01/11111111-2222-4333-8444-555555555555.png");
        assertThat(thumbnail(already)).isEqualTo(NEW + "/" + key);
        assertThat(thumbnail(lookalike)).isEqualTo(OLD + "x/" + key);
        assertThat(thumbnail(none)).isNull();
    }

    @Test
    void 스크립트는_트랜잭션으로_감싸고_오류에서_멈춘다() throws IOException {
        String script = script();
        assertThat(script)
                .contains("\\set ON_ERROR_STOP on")
                .contains("BEGIN;")
                .contains("COMMIT;");
        assertThat(script).contains(":'old'").contains(":'new'");
    }
}
