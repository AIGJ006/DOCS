-- 009 1분 반영: 모음 묶음을 처리 중 묶음으로 옮긴다 (research R8, contracts/view-pipeline.md §3).
-- 이름을 바꾸는 순간부터 새 조회는 새 view:pending 키에 모인다. 처리 중 묶음은 TTL 없이 반영이 끝날 때까지 남긴다.
-- KEYS[1] = view:pending:{yyyyMMdd}, KEYS[2] = view:processing:{yyyyMMdd}:{uuid}
-- 반환: 1 = 옮김, 0 = 이미 없음
if redis.call('EXISTS', KEYS[1]) == 0 then
  return 0
end
redis.call('RENAME', KEYS[1], KEYS[2])
redis.call('PERSIST', KEYS[2])
return 1
