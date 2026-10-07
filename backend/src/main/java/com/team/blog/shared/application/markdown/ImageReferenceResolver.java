package com.team.blog.shared.application.markdown;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * 사진 판별 포트 (12 §6, 23 §2-4). 렌더러(shared)가 media 모듈에 의존하지 않도록 포트로 둔다. 구현은 {@code
 * media.infra.ImageReferenceResolverAdapter}(003이 교체).
 */
public interface ImageReferenceResolver {

    /**
     * 우리 저장소 사진 주소면 저장 키. 주소가 공개 주소({@code blog.image.public-base-url}) 또는 옛 주소({@code
     * blog.image.legacy-base-urls}) 중 하나 + {@code /}로 시작하고, 나머지가 {@code
     * images/{yyyy}/{MM}/{uuid}(_thumb)?.{ext}} 모양일 때만.
     */
    Optional<String> keyOf(String url);

    /** 키 중 {@code ownerId}가 올린 사진만 (한 번의 조회). 결과 맵의 키는 저장 키. */
    Map<String, OwnedImage> findOwned(Collection<String> keys, long ownerId);

    /** 저장 키 → 지금의 공개 주소 ({@code public-base-url + "/" + key}). */
    String publicUrlOf(String storageKey);
}
