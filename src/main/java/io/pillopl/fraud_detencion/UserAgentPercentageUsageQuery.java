package io.pillopl.fraud_detencion;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

class UserAgentPercentageUsageQuery implements Query<Double> {

    private final JdbcTemplate jdbcTemplate;

    UserAgentPercentageUsageQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Double execute(Map<String, String> params) {
        String sql = build(params.get("user_id"), params.get("user_agent"));
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                int matching = rs.getInt("matching_logins");
                int total = rs.getInt("total_logins");
                double percent = rs.getDouble("percent");
                System.out.println(String.format("🟢 %s wystąpił %.2f%% (%d z %d)",
                        params.get("user_agent"), percent, matching, total));
                return percent;
            } else {
                return 0d;
            }
        });
    }

    private String build(String userId, String userAgent) {
        return "WITH " +
                "  total AS ( " +
                "    SELECT count() AS total_logins " +
                "    FROM login_events " +
                "    WHERE event_time >= now() - INTERVAL 1 YEAR " +
                "      AND user_id = '" + userId + "' " +
                "  ), " +
                "  agent_count AS ( " +
                "    SELECT count() AS matching_logins " +
                "    FROM login_events " +
                "    WHERE event_time >= now() - INTERVAL 1 YEAR " +
                "      AND user_id = '" + userId + "' " +
                "      AND user_agent = '" + userAgent + "' " +
                "  ) " +
                "SELECT " +
                "  matching_logins, " +
                "  total_logins, " +
                "  round(matching_logins / total_logins * 100, 2) AS percent " +
                "FROM agent_count, total;";
    }

}
