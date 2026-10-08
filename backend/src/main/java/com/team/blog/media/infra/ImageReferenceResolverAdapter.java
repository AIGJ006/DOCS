package com.team.blog.media.infra;

import com.team.blog.media.application.ImageUrlResolver;
import com.team.blog.media.application.ImageUrls;
import com.team.blog.shared.application.markdown.ImageReferenceResolver;
import com.team.blog.shared.application.markdown.OwnedImage;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 렌더러의 사진 판별 포트 구현 (002 T025 → 003 최종). 주소 → 키는 {@link ImageUrls} 한 곳에 위임하고(FR-024), "누가 올렸나"는
 * {@link ImageRepository#findCompletedOwned}로 한 번에 찾는다.
 *
 * <p><b>003 최종 — 규칙과 회귀 테스트 목록</b>
 *
 * <ul>
 *   <li>{@code keyOf}: 지금 공개 주소·옛 주소 목록({@code public-base-url}·{@code legacy-base-urls}) + 키 모양
 *       {@code images/{yyyy}/{MM}/{uuid}(_thumb)?.{ext}}일 때만 키. 쿼리·조각이 붙은 주소, 대소문자가 다른 주소는 우리 사진이
 *       아니다 — {@code ImageUrlsTest}, {@code
 *       ImageReferenceResolverAdapterIT#공개_주소와_키_모양이_맞을_때만_키를_돌려준다}
 *   <li>{@code findOwned}: 올린 회원({@code uploader_id})의 <b>완료된</b>({@code width IS NOT NULL},
 *       research R5) 사진만, 키 수와 상관없이 조회 1번 — {@code #올린_회원의_사진만_한_번의_조회로_찾는다}, {@code
 *       ImageLinkIT#완료_전_내_사진은_연결되지_않는다}, 렌더링 결과는 {@code
 *       ContentRendererWiringIT#작성자_사진은_img_남이면_링크}
 *   <li>공개 주소는 지금 설정값으로 만든다(옛 주소로 쓴 사진도 지금 주소로) — {@code #공개_주소는_지금_설정값으로_만든다}, {@code
 *       PublicBaseUrlChangeIT}
 *   <li>발행 때 작성자 사진만 연결하고 남의 사진은 링크로 바뀐다 — {@code
 *       PublishIT#남이_올린_사진은_연결하지_않고_링크로_바꾸며_원래_주인의_연결은_그대로}, {@code ImageLinkIT}
 * </ul>
 */
@Component
public class ImageReferenceResolverAdapter implements ImageReferenceResolver {

    private final ImageUrls imageUrls;
    private final ImageRepository images;
    private final ImageUrlResolver urls;

    public ImageReferenceResolverAdapter(
            ImageUrls imageUrls, ImageRepository images, ImageUrlResolver urls) {
        this.imageUrls = imageUrls;
        this.images = images;
        this.urls = urls;
    }

    @Override
    public Optional<String> keyOf(String url) {
        return imageUrls.keyOf(url);
    }

    /** 주소 → 저장 키 (순수 함수). {@link ImageUrls#parseKey}에 위임한다 — 002 테스트 스텁이 같은 규칙을 쓴다. */
    public static Optional<String> parseKey(String url, List<String> baseUrls) {
        return ImageUrls.parseKey(url, baseUrls);
    }

    @Override
    public Map<String, OwnedImage> findOwned(Collection<String> keys, long ownerId) {
        Map<String, OwnedImage> result = new LinkedHashMap<>();
        for (ImageRepository.OwnedKeys owned : images.findCompletedOwned(keys, ownerId)) {
            result.put(
                    owned.storageKey(),
                    new OwnedImage(owned.storageKey(), owned.thumbStorageKey()));
        }
        return result;
    }

    @Override
    public String publicUrlOf(String storageKey) {
        return urls.publicUrl(storageKey);
    }
}
