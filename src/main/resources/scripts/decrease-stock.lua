local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil then
    return -1
end
if stock <= 0 then
    return 0
end
redis.call('DECR', KEYS[1])
return 1
