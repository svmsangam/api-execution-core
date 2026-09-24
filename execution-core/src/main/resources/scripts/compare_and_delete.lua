-- KEYS[1]: The lock key
-- ARGV[1]: Expected lock value ("LOCKED:uuid")

if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
else
    return 0
end