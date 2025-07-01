package io.pillopl.fraud_detencion;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;


@Component
public class DataSourceRegistry {

    private final Map<String, DataSourceDriver<?>> drivers;

    DataSourceRegistry(Map<String, DataSourceDriver<?>> drivers) {
        this.drivers = drivers;
    }

    Optional<DataSourceDriver<?>> get(String name) {
        return Optional.ofNullable(drivers.get(name));
    }

    RedisTemplate<String, String> redis() {
        return (RedisTemplate<String, String>) drivers.get("redis").driver;
    }

    JdbcTemplate clickhouse() {
        return (JdbcTemplate) drivers.get("clickhouse").driver;
    }
}

class DataSourceDriver<T> {
    final T driver;

    DataSourceDriver(T driver) {
        this.driver = driver;
    }
}


@Configuration
class ClickHouseJdbcConfig {

    @Bean
    DataSource clickHouseDataSource() throws SQLException {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:clickhouse://localhost:8123/default");
        ds.setUsername("default");
        ds.setPassword("");
        ds.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        ds.setMaximumPoolSize(40);
        ds.setMinimumIdle(5);
        return ds;
    }

    @Bean
    DataSourceDriver<JdbcTemplate> clickhouse(DataSource clickHouseDataSource) {
        return new DataSourceDriver<>(new JdbcTemplate(clickHouseDataSource));
    }
}

@Configuration
class RedisConfig {

    @Bean
    LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("localhost", 6379);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    DataSourceDriver<RedisTemplate<String, String>> redis(LettuceConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return new DataSourceDriver<>(template);
    }
}

