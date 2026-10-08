package com.team.blog.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.notification.domain.MutableType;
import com.team.blog.notification.domain.NotificationType;
import com.team.blog.support.IntegrationTestBase;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** enum 값이 V1 CHECK 값과 같은지 (011 T008). */
class NotificationTypeSchemaIT extends IntegrationTestBase {

    @Test
    void 알림_종류는_V1_CHECK와_같다() {
        assertThat(checkValues("ck_notification_type"))
                .containsExactlyElementsOf(
                        Arrays.stream(NotificationType.values()).map(Enum::name).toList());
    }

    @Test
    void 끌_수_있는_종류는_V1_CHECK와_같다() {
        assertThat(checkValues("ck_notification_mute_type"))
                .containsExactlyElementsOf(
                        Arrays.stream(MutableType.values()).map(Enum::name).toList());
    }

    private List<String> checkValues(String constraint) {
        String clause =
                jdbc.queryForObject(
                        "SELECT check_clause FROM information_schema.check_constraints"
                                + " WHERE constraint_name = ?",
                        String.class,
                        constraint);
        Matcher m = Pattern.compile("'([A-Z_]+)'").matcher(clause);
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        while (m.find()) {
            values.add(m.group(1));
        }
        return values;
    }
}
