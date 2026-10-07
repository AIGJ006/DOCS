package com.team.blog.post.application;

import com.team.blog.post.domain.PostNotFoundException;
import com.team.blog.post.domain.ServerCopy;
import com.team.blog.post.infra.AutosaveEntry;
import com.team.blog.post.infra.PostEditRepository;
import com.team.blog.post.infra.PostEditRepository.EditState;
import com.team.blog.post.infra.RedisAutosaveStore;
import com.team.blog.tag.application.TagService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 에디터 열기 (002 T049, FR-034, B-1). 작성자만, 계정 상태 판정 없음(읽기). 내용은 Redis 보관분·작업본·글 중 버전이 가장 큰 출처에서 가져온다.
 * Redis 장애면 DB만 본다.
 */
@Service
public class EditorQueryService {

    private final PostEditRepository edits;
    private final RedisAutosaveStore autosaves;
    private final TagService tagService;

    public EditorQueryService(
            PostEditRepository edits, RedisAutosaveStore autosaves, TagService tagService) {
        this.edits = edits;
        this.autosaves = autosaves;
        this.tagService = tagService;
    }

    /**
     * @throws PostNotFoundException 남의 글·휴지통 글·없는 글 (구분 없음)
     */
    public WorkingCopy open(long postId, long memberId) {
        EditState state =
                edits.findOwnedEditState(postId, memberId)
                        .orElseThrow(() -> new PostNotFoundException("에디터 열기: 내 글 아님"));
        ServerCopy current = currentCopy(state, autosaves.find(postId));
        boolean editing =
                state.isPublished()
                        && (state.hasDraft() || current.version() > state.postVersion());
        List<String> tags = tagService.tagNamesOf(postId);
        String url =
                state.isPublished()
                        ? edits.findAuthorHandle(memberId)
                                .map(h -> PostUrls.of(h, postId))
                                .orElse(null)
                        : null;
        return new WorkingCopy(
                postId,
                state.postStatus(),
                editing,
                current.title(),
                current.contentMd(),
                current.version(),
                current.savedAt(),
                state.visibility(),
                tags,
                url);
    }

    /** max(Redis, post_draft, post) 출처의 내용. 같은 버전이면 DB를 쓴다. */
    static ServerCopy currentCopy(EditState state, Optional<AutosaveEntry> redis) {
        ServerCopy db = state.dbCopy();
        if (redis.isPresent() && redis.get().version() > db.version()) {
            AutosaveEntry e = redis.get();
            return new ServerCopy(
                    e.title(),
                    e.contentMd(),
                    e.version(),
                    e.savedAt() != null ? e.savedAt() : db.savedAt());
        }
        return db;
    }
}
