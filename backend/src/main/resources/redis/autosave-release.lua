-- 발행·변경 취소 커밋 후 Redis 자동 저장 보관분 정리 (002 research B-3 ④·⑤, data-model §4)
-- KEYS[1] = autosave:post:{postId}, KEYS[2] = autosave:dirty
-- ARGV[1] = 확인한 버전 v0, ARGV[2] = 새 DB 버전 v1, ARGV[3] = postId
-- 반환: 0 = 키 없음, 1 = 지움, 2 = 남기고 버전을 v1 + 1로 다시 매김
local version = redis.call('HGET', KEYS[1], 'version')
if not version then
  return 0
end
if tonumber(version) <= tonumber(ARGV[1]) then
  redis.call('DEL', KEYS[1])
  redis.call('SREM', KEYS[2], ARGV[3])
  return 1
end
-- 발행 중에 다른 탭이 저장함: 지우지 않고 새 DB 버전 다음 번호로 다시 매긴다(dirty 유지 → 1분 반영이 작업본으로 남김)
redis.call('HSET', KEYS[1], 'version', tostring(tonumber(ARGV[2]) + 1))
redis.call('SADD', KEYS[2], ARGV[3])
return 2
