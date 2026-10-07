-- autosave-save.lua: 자동 저장 버전 확인 + 저장 (002 data-model §4, research A-4·B-3 ①)
-- KEYS[1] = autosave:post:{postId}, KEYS[2] = autosave:dirty
-- ARGV[1] = memberId, ARGV[2] = baseVersion, ARGV[3] = dbVersion(max(post, post_draft)),
-- ARGV[4] = title, ARGV[5] = contentMd, ARGV[6] = savedAt(ISO-8601), ARGV[7] = TTL(ms), ARGV[8] = postId
-- 현재 버전 = max(Redis version, dbVersion) — 장애 동안 DB로 오른 버전이 옛 Redis 키보다 크면 DB가 기준이다.
-- 반환(문자열 배열):
--   {'1', 새 버전}                                   받아들임
--   {'0', 현재 버전}                                 거부, 현재 내용은 DB에 있음
--   {'0', 현재 버전, title, contentMd, savedAt}      거부, 현재 내용은 Redis에 있음
--   {'-1'}                                           키의 회원이 다름
local owner = redis.call('HGET', KEYS[1], 'memberId')
if owner and owner ~= ARGV[1] then
  return {'-1'}
end
local current = tonumber(ARGV[3])
local fromRedis = false
local stored = redis.call('HGET', KEYS[1], 'version')
if stored and tonumber(stored) > current then
  current = tonumber(stored)
  fromRedis = true
end
if tonumber(ARGV[2]) ~= current then
  if fromRedis then
    local h = redis.call('HMGET', KEYS[1], 'title', 'contentMd', 'savedAt')
    return {'0', tostring(current), h[1] or '', h[2] or '', h[3] or ''}
  end
  return {'0', tostring(current)}
end
local nextVersion = current + 1
redis.call('HSET', KEYS[1],
  'memberId', ARGV[1],
  'title', ARGV[4],
  'contentMd', ARGV[5],
  'version', tostring(nextVersion),
  'savedAt', ARGV[6])
redis.call('PEXPIRE', KEYS[1], ARGV[7])
redis.call('SADD', KEYS[2], ARGV[8])
return {'1', tostring(nextVersion)}
