package com.team.blog.post.domain;

import com.team.blog.post.application.exception.PublishValidationException;
import com.team.blog.post.config.PostAuthoringProperties;
import com.team.blog.shared.error.FieldError;
import com.team.blog.tag.application.TagService;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 발행 검증 (05 §4, data-model §3, FR-026). 실패 항목을 <b>모두</b> 모아 한 번에 돌려준다: 제목 → 본문 → 업로드 대기 사진 → 태그 →
 * 공개 범위 순서.
 */
@Component
public class PublishValidator {

    /** 업로드가 끝나지 않은 사진 주소: 인라인 {@code ](local:…)}, 참조 정의 {@code [x]: local:…} (003 규칙). */
    private static final Pattern PENDING_IMAGE =
            Pattern.compile("\\]\\(\\s*<?local:|(?m)^ {0,3}\\[[^\\]]+\\]:\\s*<?local:");

    private final PostAuthoringProperties properties;
    private final TagService tagService;
    private final VisibilityRegistry visibilityRegistry;

    public PublishValidator(
            PostAuthoringProperties properties,
            TagService tagService,
            VisibilityRegistry visibilityRegistry) {
        this.properties = properties;
        this.tagService = tagService;
        this.visibilityRegistry = visibilityRegistry;
    }

    /**
     * @return 정리한 제목·공개 범위·정규화한 태그
     * @throws PublishValidationException 실패 항목이 하나라도 있으면 (전부 담음)
     */
    public Validated validate(
            String rawTitle, String contentMd, List<String> rawTags, String rawVisibility) {
        List<FieldError> errors = new ArrayList<>();

        String title = TitleNormalizer.normalize(rawTitle);
        TitleNormalizer.check(title, properties.post().titleMax())
                .ifPresent(code -> errors.add(code.fieldError("title")));

        String md = contentMd == null ? "" : contentMd;
        if (md.strip().isEmpty()) {
            errors.add(PostReasonCode.CONTENT_REQUIRED.fieldError("contentMd"));
        } else if (md.length() > properties.post().contentMax()) {
            errors.add(PostReasonCode.CONTENT_TOO_LONG.fieldError("contentMd"));
        } else if (hasPendingImages(md)) {
            errors.add(PostReasonCode.PENDING_IMAGES.fieldError("contentMd"));
        }

        List<String> tags = tagService.normalizeAll(rawTags);
        if (tags.size() > properties.post().maxTags()) {
            errors.add(PostReasonCode.TOO_MANY_TAGS.fieldError("tags"));
        } else {
            errors.addAll(tagService.validate(rawTags));
        }

        Visibility visibility = null;
        try {
            visibility = visibilityRegistry.require(rawVisibility, "visibility");
        } catch (InvalidVisibilityException e) {
            errors.add(InvalidVisibilityException.fieldError("visibility"));
        }

        if (!errors.isEmpty()) {
            throw new PublishValidationException(errors);
        }
        return new Validated(title, visibility, tags);
    }

    /** 본문에 업로드 대기({@code local:}) 사진이 있는가. */
    public static boolean hasPendingImages(String contentMd) {
        return contentMd != null && PENDING_IMAGE.matcher(contentMd).find();
    }

    /**
     * 검증을 통과한 값.
     *
     * @param title 정리한 제목
     * @param visibility 공개 범위
     * @param tags 정규화·중복 제거한 태그 (입력 순서)
     */
    public record Validated(String title, Visibility visibility, List<String> tags) {}
}
