-- autosave-clear-dirty.lua: 1분 반영 뒤 dirty 정리 (002 T082, EV §3)
-- KEYS[1] = autosave:post:{postId}, KEYS[2] = autosave:dirty
-- ARGV[1] = 반영한 버전, ARGV[2] = postId
-- 키 버전이 반영한 버전과 같을 때만 SREM 한다(반영 중에 다른 탭이 저장했으면 dirty 유지). 키가 없으면(만료) SREM.
local version = redis.call('HGET', KEYS[1], 'version')
if (not version) or tonumber(version) == tonumber(ARGV[1]) then
  redis.call('SREM', KEYS[2], ARGV[2])
  return 1
end
return 0
