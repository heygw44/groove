-- KEYS[1] = 세션 키(refresh:{memberId}:{sessionId}), KEYS[2] = 인덱스 키(refresh-sessions:{memberId})
-- ARGV[1] = token, ARGV[2] = sessionId, ARGV[3] = ttlMillis, ARGV[4] = 세션 키 prefix(refresh:{memberId}:)
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

redis.call('HSET', KEYS[1], 'current', ARGV[1])
redis.call('PEXPIRE', KEYS[1], ARGV[3])
redis.call('SADD', KEYS[2], ARGV[2])
redis.call('PEXPIRE', KEYS[2], ARGV[3])
return 1
