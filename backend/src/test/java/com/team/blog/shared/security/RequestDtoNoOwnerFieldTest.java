package com.team.blog.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 요청 DTO 작성자 필드 금지 (004 T046, FR-026, Constitution III). 글의 주인은 세션의 회원 번호로만 정한다 — 요청 본문 타입에 {@code
 * authorId}·{@code memberId}·{@code ownerId}·{@code userId}가 있으면 "남의 이름으로 쓰기" 통로가 생긴다.
 *
 * <p>대상: {@code com.team.blog..web.dto} 패키지의 {@code *Request} 타입과, 모든 컨트롤러의 {@code @RequestBody}
 * 매개변수 타입(그 안에 들어 있는 {@code com.team.blog} 타입까지). 필드·record 구성 요소·생성자 인자 이름을 본다.
 */
class RequestDtoNoOwnerFieldTest {

    private static final Set<String> FORBIDDEN =
            Set.of("authorid", "memberid", "ownerid", "userid");
    private static final String BASE = "com.team.blog";

    @Test
    void 요청_DTO에_작성자_번호_필드가_없다() throws Exception {
        Set<Class<?>> roots = new LinkedHashSet<>(dtoRequests());
        roots.addAll(requestBodyTypes());
        assertThat(roots).as("검사할 요청 타입").hasSizeGreaterThanOrEqualTo(5);

        List<String> violations = new ArrayList<>();
        Set<Class<?>> seen = new LinkedHashSet<>();
        Deque<Class<?>> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            Class<?> type = queue.pop();
            if (!seen.add(type)) {
                continue;
            }
            for (String name : names(type)) {
                if (FORBIDDEN.contains(name.toLowerCase(Locale.ROOT))) {
                    violations.add(type.getName() + "." + name);
                }
            }
            for (Field field : type.getDeclaredFields()) {
                if (field.getType().getName().startsWith(BASE) && !field.getType().isEnum()) {
                    queue.add(field.getType());
                }
            }
        }

        assertThat(violations).as("요청 본문에 주인 번호를 받지 않는다 (FR-026)").isEmpty();
    }

    @Test
    void 공개_범위_변경_요청은_visibility_하나뿐이다() throws Exception {
        Class<?> request = Class.forName(BASE + ".post.web.dto.VisibilityChangeRequest");
        assertThat(names(request)).containsOnly("visibility");
    }

    private static Set<String> names(Class<?> type) {
        Set<String> names = new LinkedHashSet<>();
        for (Field field : type.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                names.add(field.getName());
            }
        }
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                names.add(component.getName());
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            for (Parameter parameter : constructor.getParameters()) {
                if (parameter.isNamePresent()) {
                    names.add(parameter.getName());
                }
            }
        }
        return names;
    }

    private static List<Class<?>> dtoRequests() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(
                            org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
                                    definition) {
                        return definition.getMetadata().isIndependent();
                    }
                };
        scanner.addIncludeFilter(
                new RegexPatternTypeFilter(Pattern.compile(".*\\.web\\.dto\\.[^.]*Request$")));
        return load(scanner.findCandidateComponents(BASE));
    }

    private static Set<Class<?>> requestBodyTypes() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        Set<Class<?>> types = new LinkedHashSet<>();
        for (Class<?> controller : load(scanner.findCandidateComponents(BASE))) {
            for (Method method : controller.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    if (parameter.isAnnotationPresent(RequestBody.class)
                            && parameter.getType().getName().startsWith(BASE)) {
                        types.add(parameter.getType());
                    }
                }
            }
        }
        return types;
    }

    private static List<Class<?>> load(Set<BeanDefinition> definitions)
            throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition definition : definitions) {
            classes.add(Class.forName(definition.getBeanClassName()));
        }
        return classes;
    }
}
