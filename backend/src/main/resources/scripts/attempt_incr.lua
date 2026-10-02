-- KEYS[1] = 실패 카운터 키, ARGV[1] = 잠금 창 길이(밀리초)
-- 실패마다 TTL 을 다시 건다: 잠금은 "마지막 실패 시점 + window" 에 풀린다.
local n = redis.call('INCR', KEYS[1])
redis.call('PEXPIRE', KEYS[1], ARGV[1])
return n
