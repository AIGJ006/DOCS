package com.team.blog.post.support;

import com.team.blog.media.infra.ImageReferenceResolverAdapter;
import com.team.blog.shared.application.markdown.ImageReferenceResolver;
import com.team.blog.shared.application.markdown.OwnedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 테스트용 사진 판별기 (002 T009). 주소 → 키 규칙은 실제 구현({@link ImageReferenceResolverAdapter#parseKey})을 그대로 쓰고,
 * "누가 올린 사진인가"만 테스트가 {@link #own(long, String, String)}로 지정한다(DB {@code image} 행 없이).
 */
public class StubImageReferenceResolver implements ImageReferenceResolver {

    public static final String PUBLIC_BASE_URL = "https://cdn.devlog.example";
    public static final String LEGACY_BASE_URL = "https://old-cdn.devlog.example";

    private final String publicBaseUrl;
    private final List<String> bases;
    private final Map<Long, Map<String, OwnedImage>> owned = new ConcurrentHashMap<>();
    private final AtomicInteger findOwnedCalls = new AtomicInteger();

    public StubImageReferenceResolver() {
        this(PUBLIC_BASE_URL, List.of(LEGACY_BASE_URL));
    }

    public StubImageReferenceResolver(String publicBaseUrl, List<String> legacyBaseUrls) {
        this.publicBaseUrl = publicBaseUrl;
        List<String> all = new ArrayList<>();
        all.add(publicBaseUrl);
        all.addAll(legacyBaseUrls);
        this.bases = List.copyOf(all);
    }

    /** {@code ownerId}가 올린 사진으로 등록한다. {@code thumbKey}는 없으면 null(썸네일 없는 옛 사진). */
    public StubImageReferenceResolver own(long ownerId, String storageKey, String thumbKey) {
        owned.computeIfAbsent(ownerId, k -> new ConcurrentHashMap<>())
                .put(storageKey, new OwnedImage(storageKey, thumbKey));
        return this;
    }

    public void clear() {
        owned.clear();
        findOwnedCalls.set(0);
    }

    /** {@link #findOwned} 호출 횟수 (본문 키 묶음 1회 확인용). */
    public int findOwnedCalls() {
        return findOwnedCalls.get();
    }

    @Override
    public Optional<String> keyOf(String url) {
        return ImageReferenceResolverAdapter.parseKey(url, bases);
    }

    @Override
    public Map<String, OwnedImage> findOwned(Collection<String> keys, long ownerId) {
        findOwnedCalls.incrementAndGet();
        Map<String, OwnedImage> mine = owned.getOrDefault(ownerId, Map.of());
        Map<String, OwnedImage> result = new LinkedHashMap<>();
        for (String key : keys) {
            OwnedImage image = mine.get(key);
            if (image != null) {
                result.put(key, image);
            }
        }
        return result;
    }

    @Override
    public String publicUrlOf(String storageKey) {
        return publicBaseUrl + "/" + storageKey;
    }
}
