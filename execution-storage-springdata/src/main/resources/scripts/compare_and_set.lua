-- KEYS[1]: The key to update
-- ARGV[1]: Expected current value
-- ARGV[2]: Replacement value
-- ARGV[3]: TTL in milliseconds

if redis.call('GET', KEYS[1]) == ARGV[1] then
    redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
    return 1
else
    return 0
end
