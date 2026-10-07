package com.team.blog.post.application;

import com.team.blog.media.application.ImageService;
import com.team.blog.shared.application.markdown.ContentRenderer;
import com.team.blog.shared.application.markdown.ImageContext;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 저장한 본문의 작성자 사진 찾기·연결 (003 FR-022: 수동 저장·1분 반영·DB 직접 저장 때 {@code post_image} 연결). 사진 판별은 발행과 같은
 * 렌더러 규칙을 쓴다. 렌더링이 실패해도(너무 복잡한 글) 저장은 막지 않고 연결만 건너뛴다. 렌더링은 트랜잭션 밖에서 한다.
 */
@Component
public class SavedContentImages {

    private static final Logger log = LoggerFactory.getLogger(SavedContentImages.class);

    private final ContentRenderer renderer;
    private final ImageService imageService;

    public SavedContentImages(ContentRenderer renderer, ImageService imageService) {
        this.renderer = renderer;
        this.imageService = imageService;
    }

    /** 본문에 쓴 작성자 사진의 저장 키 (트랜잭션 밖에서 부른다). 판별할 수 없으면 빈 목록. */
    public List<String> ownedKeys(String contentMd, long authorId) {
        if (contentMd == null || contentMd.isBlank()) {
            return List.of();
        }
        try {
            return renderer.render(contentMd, new ImageContext(authorId)).ownedImageKeys();
        } catch (RuntimeException e) {
            log.debug("저장 본문의 사진 판별을 건너뜁니다: {}", e.getClass().getSimpleName());
            return List.of();
        }
    }

    /** 연결만 더한다 (끊지 않음 — 발행본이 아직 쓸 수 있다). 트랜잭션 안에서 부른다. */
    public void attach(long postId, long authorId, List<String> keys) {
        imageService.attachPostImages(postId, authorId, keys);
    }
}
