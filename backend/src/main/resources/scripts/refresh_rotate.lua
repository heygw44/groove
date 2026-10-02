-- KEYS[1] = 세션 키(refresh:{memberId}:{sessionId}), KEYS[2] = 인덱스 키(refresh-sessions:{memberId})
-- ARGV[1] = presented, ARGV[2] = newToken, ARGV[3] = nowMillis, ARGV[4] = graceMillis,
-- ARGV[5] = 세션 키 ttlMillis, ARGV[6] = sessionId, ARGV[7] = legacyAbsExpMillis, ARGV[8] = 인덱스 키 ttlMillis
-- 반환: {0} NOT_FOUND / {1, newToken, absExp} ROTATED / {2, current, absExp} GRACE(멱등, 새로 발급 안 함) /
-- {3} REUSED / {4} EXPIRED(절대 만료)
-- noeviction 환경이라 모든 읽기를 쓰기보다 앞에 둔다.
local fields = redis.call('HMGET', KEYS[1], 'current', 'prev', 'prev_exp', 'abs_exp')
local current = fields[1]
local prev = fields[2]
local prevExp = fields[3]
local absExpField = fields[4]
if not current then
  return {0}
end

local now = tonumber(ARGV[3])
-- 필드가 없는 기존 세션은 호출자가 넘긴 값(now + 역할별 상한)으로 채운다. 배포 직후 전원 재로그인을 막기 위해서다.
local absExp = tonumber(absExpField) or tonumber(ARGV[7])
if now >= absExp then
  redis.call('DEL', KEYS[1])
  redis.call('SREM', KEYS[2], ARGV[6])
  return {4}
end

if ARGV[1] == current then
  local sessionTtl = math.min(tonumber(ARGV[5]), absExp - now)
  redis.call('HSET', KEYS[1], 'current', ARGV[2], 'prev', ARGV[1], 'prev_exp', now + tonumber(ARGV[4]),
      'abs_exp', absExp)
  redis.call('PEXPIRE', KEYS[1], sessionTtl)
  -- 인덱스 키만 유실돼도(TTL 경합·장애) 회전 시점에 sessionId 를 다시 심어 자가 치유한다.
  redis.call('SADD', KEYS[2], ARGV[6])
  redis.call('PEXPIRE', KEYS[2], ARGV[8])
  return {1, ARGV[2], absExp}
end

if prev and ARGV[1] == prev and prevExp and now < tonumber(prevExp) then
  return {2, current, absExp}
end

redis.call('DEL', KEYS[1])
redis.call('SREM', KEYS[2], ARGV[6])
return {3}
