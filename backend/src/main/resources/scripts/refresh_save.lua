-- KEYS[1] = 세션 키(refresh:{memberId}:{sessionId}), KEYS[2] = 인덱스 키(refresh-sessions:{memberId})
-- ARGV[1] = token, ARGV[2] = sessionId, ARGV[3] = ttlMillis, ARGV[4] = 세션 키 prefix(refresh:{memberId}:),
-- ARGV[5] = absExpMillis, ARGV[6] = nowMillis
-- 인덱스에 남아있지만 세션 키가 이미 만료된(죽은) sessionId 를 먼저 걷어낸다.
local members = redis.call('SMEMBERS', KEYS[2])
local deadSessionIds = {}
for _, sessionId in ipairs(members) do
  if redis.call('EXISTS', ARGV[4] .. sessionId) == 0 then
    table.insert(deadSessionIds, sessionId)
  end
end

for _, sessionId in ipairs(deadSessionIds) do
  redis.call('SREM', KEYS[2], sessionId)
end

local ttl = tonumber(ARGV[3])
local absExp = tonumber(ARGV[5])
local now = tonumber(ARGV[6])
local sessionTtl = math.min(ttl, absExp - now)

redis.call('HSET', KEYS[1], 'current', ARGV[1], 'abs_exp', ARGV[5])
redis.call('PEXPIRE', KEYS[1], sessionTtl)
redis.call('SADD', KEYS[2], ARGV[2])
-- 인덱스는 여러 세션이 공유하므로 절대 만료 캡을 씌우지 않는다.
redis.call('PEXPIRE', KEYS[2], ttl)
return 1
