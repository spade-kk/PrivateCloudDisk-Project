FROM redis:7-alpine

# 自定义 Redis 配置
COPY redis.conf /usr/local/etc/redis/redis.conf

# 以自定义配置启动
CMD ["redis-server", "/usr/local/etc/redis/redis.conf"]