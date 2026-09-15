-- KEYS[1] = 세션 키(refresh:{memberId}:{sessionId}), KEYS[2] = 인덱스 키(refresh-sessions:{memberId})
-- ARGV[1] = presented, ARGV[2] = newToken, ARGV[3] = nowMillis, ARGV[4] = graceMillis,
-- ARGV[5] = ttlMillis, ARGV[6] = sessionId
-- 반환: {0} NOT_FOUND / {1, newToken} ROTATED / {2, current} GRACE(멱등, 새로 발급 안 함) / {3} REUSED
-- noeviction 환경이라 모든 읽기를 쓰기보다 앞에 둔다.
local current = redis.call('HGET', KEYS[1], 'current')
if not current then
  return {0}
end

local prev = redis.call('HGET', KEYS[1], 'prev')
local prevExp = redis.call('HGET', KEYS[1], 'prev_exp')
local now = tonumber(ARGV[3])

if ARGV[1] == current then
  redis.call('HSET', KEYS[1], 'current', ARGV[2], 'prev', ARGV[1], 'prev_exp', now + tonumber(ARGV[4]))
  redis.call('PEXPIRE', KEYS[1], ARGV[5])
  -- 인덱스 키만 유실돼도(TTL 경합·장애) 회전 시점에 sessionId 를 다시 심어 자가 치유한다.
  redis.call('SADD', KEYS[2], ARGV[6])
  redis.call('PEXPIRE', KEYS[2], ARGV[5])
  return {1, ARGV[2]}
end

if prev and ARGV[1] == prev and prevExp and now < tonumber(prevExp) then
  return {2, current}
end

redis.call('DEL', KEYS[1])
redis.call('SREM', KEYS[2], ARGV[6])
return {3}
