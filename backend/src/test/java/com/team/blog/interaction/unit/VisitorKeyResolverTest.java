package com.team.blog.interaction.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.interaction.application.VisitorKeyResolver;
import com.team.blog.shared.security.Viewer;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 방문자 키 (009 T024, research R5, FR-023, SC-010). */
class VisitorKeyResolverTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 8);
    private static final String IP = "203.0.113.77";
    private static final String UA = "Mozilla/5.0 Test";
    private static final String VID = "3f2b8c4e-9a1d-4e7b-8c2a-1b2c3d4e5f60";

    private final Map<LocalDate, String> salts = new HashMap<>(Map.of(DAY, "salt-a"));
    private final AtomicInteger saltReads = new AtomicInteger();
    private final VisitorKeyResolver resolver =
            new VisitorKeyResolver(
                    date -> {
                        saltReads.incrementAndGet();
                        return Optional.ofNullable(salts.get(date));
                    });

    private static Viewer member(long id) {
        return new Viewer(id, Role.USER, MemberStatus.ACTIVE, true);
    }

    @Test
    void 회원은_m_회원번호() {
        assertThat(resolver.resolve(member(42), VID, IP, UA, DAY)).contains("m:42");
    }

    @Test
    void 비회원_유효한_vid는_v_키이고_vid_문자열은_담지_않는다() {
        String key = resolver.resolve(Viewer.anonymous(), VID, IP, UA, DAY).orElseThrow();
        assertThat(key).startsWith("v:").doesNotContain(VID).doesNotContain(IP);
        assertThat(
                        resolver.resolve(
                                Viewer.anonymous(), VID.toUpperCase(), "198.51.100.1", "x", DAY))
                .as("같은 쿠키면 IP·UA·대소문자와 상관없이 같은 방문자")
                .contains(key);
        assertThat(resolver.resolve(Viewer.anonymous(), VID, IP, UA, DAY.plusDays(3)))
                .as("쿠키 방문자는 날짜를 넘겨도 같다")
                .contains(key);
    }

    @Test
    void vid_형식이_틀리면_h_키() {
        for (String bad : new String[] {null, "", "not-a-uuid", VID + "x", "../../etc"}) {
            assertThat(resolver.resolve(Viewer.anonymous(), bad, IP, UA, DAY).orElseThrow())
                    .as(String.valueOf(bad))
                    .startsWith("h:");
        }
    }

    @Test
    void 같은_날_같은_IP_UA는_같은_h_키이고_IP는_담지_않는다() {
        String first = resolver.resolve(Viewer.anonymous(), null, IP, UA, DAY).orElseThrow();
        String second = resolver.resolve(Viewer.anonymous(), null, IP, UA, DAY).orElseThrow();
        assertThat(first).isEqualTo(second).startsWith("h:").doesNotContain(IP).doesNotContain(UA);
        assertThat(resolver.resolve(Viewer.anonymous(), null, "198.51.100.1", UA, DAY))
                .isNotEqualTo(Optional.of(first));
        assertThat(resolver.resolve(Viewer.anonymous(), null, IP, "Other UA", DAY))
                .isNotEqualTo(Optional.of(first));
        assertThat(saltReads.get()).as("하루 비밀값은 날짜별로 메모리에 둔다").isOne();
    }

    @Test
    void 비밀값이_바뀌면_다른_h_키() {
        String today = resolver.resolve(Viewer.anonymous(), null, IP, UA, DAY).orElseThrow();
        salts.put(DAY.plusDays(1), "salt-b");
        String tomorrow =
                resolver.resolve(Viewer.anonymous(), null, IP, UA, DAY.plusDays(1)).orElseThrow();
        assertThat(tomorrow).isNotEqualTo(today);
    }

    @Test
    void 비밀값을_얻지_못하면_키가_없다() {
        assertThat(resolver.resolve(Viewer.anonymous(), null, IP, UA, DAY.minusDays(1))).isEmpty();
        assertThat(resolver.resolve(Viewer.anonymous(), VID, IP, UA, DAY.minusDays(1)))
                .as("쿠키·회원은 비밀값이 필요 없다")
                .isPresent();
    }
}
