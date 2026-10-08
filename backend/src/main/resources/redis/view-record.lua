-- 009 조회 기록 (research R6, contracts/view-pipeline.md §1 ⑤). 원자적으로 중복 판정 + 모음.
-- KEYS[1] = view:seen:{postId}:{visitorKey}  (기간 안 조회 횟수, 첫 조회부터 기간 TTL)
-- KEYS[2] = view:pending:{yyyyMMdd}          (Hash postId -> 모은 조회 수, 48시간 안전망 TTL)
-- ARGV = [기간(초), 기간 안 최대 횟수, postId]
-- 반환: 1 = 셈, 0 = 중복
local n = redis.call('INCR', KEYS[1])
if n == 1 then
  redis.call('EXPIRE', KEYS[1], ARGV[1])
end
if n <= tonumber(ARGV[2]) then
  redis.call('HINCRBY', KEYS[2], ARGV[3], 1)
  redis.call('EXPIRE', KEYS[2], 172800)
  return 1
end
return 0
