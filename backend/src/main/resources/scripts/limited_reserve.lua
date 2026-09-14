-- KEYS[1] = stock key, KEYS[2] = buyers key, KEYS[3] = pending key
-- ARGV[1] = memberId, ARGV[2] = nowMs (Java 가 넘긴다, 스크립트 안에서 TIME 을 쓰지 않는다)
if redis.call('EXISTS', KEYS[1]) == 0 then
  return 3
end
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
  return 1
end
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock <= 0 then
  return 2
end
redis.call('SADD', KEYS[2], ARGV[1])
redis.call('DECR', KEYS[1])
redis.call('ZADD', KEYS[3], ARGV[2], ARGV[1])
return 0
