-- KEYS[1] = stock key, KEYS[2] = buyers key, ARGV[1] = memberId
local removed = redis.call('SREM', KEYS[2], ARGV[1])
if removed == 1 then
  redis.call('INCR', KEYS[1])
end
return removed
