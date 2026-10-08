package com.team.blog.moderation.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.moderation.domain.ReportReason;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 신고 사유 enum이 V1 {@code ck_report_reason}과 같고 {@code hidden_reason varchar(30)}에 들어간다 (014 T008).
 */
class ReportReasonTest {

    @Test
    void V1_CHECK의_6개와_같다() throws IOException {
        String v1 =
                Files.readString(
                        Path.of("src/main/resources/db/migration")
                                .resolve(
                                        Files.list(Path.of("src/main/resources/db/migration"))
                                                .filter(
                                                        p ->
                                                                p.getFileName()
                                                                        .toString()
                                                                        .startsWith("V1__"))
                                                .findFirst()
                                                .orElseThrow()
                                                .getFileName()));
        Matcher m =
                Pattern.compile("ck_report_reason CHECK \\(reason IN \\(([^)]*)\\)").matcher(v1);
        assertThat(m.find()).isTrue();
        String[] codes = m.group(1).replace("'", "").replace(" ", "").split(",");
        assertThat(Arrays.stream(ReportReason.values()).map(Enum::name)).containsExactly(codes);
    }

    @Test
    void 숨김_사유_칸_30자_안에_들어간다() {
        for (ReportReason r : ReportReason.values()) {
            assertThat(r.name().length()).isLessThanOrEqualTo(30);
        }
    }
}
