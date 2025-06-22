package io.pillopl.fraud_detencion;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

class EmailOnBlacklist implements Query<Boolean> {

    private final RedisTemplate redisTemplate;

    EmailOnBlacklist(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Boolean execute(Map<String, String> params) {
        Boolean email = redisTemplate.opsForSet().isMember("blacklist:emails", params.get("email"));
        System.out.println(email);
        return email;
    }


}


