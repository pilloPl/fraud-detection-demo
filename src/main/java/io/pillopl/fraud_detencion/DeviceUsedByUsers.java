package io.pillopl.fraud_detencion;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

class DeviceUsedByUsers implements Query<Integer> {

    private final JdbcTemplate jdbcTemplate;

    DeviceUsedByUsers(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Integer execute(Map<String, String> params) {
        String sql = build(params.get("device_id"));
        System.out.println(sql);
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                System.out.println("🟢 Device uzyty: " + rs.getString("user_count"));
                return rs.getInt("user_count");
            } else {
                return 0;
            }
        });
    }

    @Override
    public RuleSource ruleSource() {
        return null;
    }

    private String build(String deviceId) {
        return  "SELECT " +
                "  count(DISTINCT user_id) AS user_count " +
                "FROM login_events " +
                "WHERE device_id = '" + deviceId + "' " +
                "  AND event_time >= now() - INTERVAL 1 YEAR;";
    }
}


