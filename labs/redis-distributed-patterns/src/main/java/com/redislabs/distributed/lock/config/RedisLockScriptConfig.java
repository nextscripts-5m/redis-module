package com.redislabs.distributed.lock.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisLockScriptConfig {

    @Bean
    public DefaultRedisScript<Long> safeUnlockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("""
                if redis.call('get', KEYS[1]) == ARGV[1] then
                  return redis.call('del', KEYS[1])
                else
                  return 0
                end
                """);
        script.setResultType(Long.class);
        return script;
    }
}
