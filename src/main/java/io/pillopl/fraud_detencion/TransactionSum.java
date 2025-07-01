package io.pillopl.fraud_detencion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Map;

class TransactionSum implements Query<Double> {

    private final Transactions transactions;

    TransactionSum(Transactions transactions) {
        this.transactions = transactions;
    }

    @Override
    public Double execute(Map<String, String> params) {
        TransactionsView view = transactions.execute(params);
        System.out.println("🟢 Suma transakcji: " + view.total());
        return view.total();
    }

    @Override
    public RuleSource ruleSource() {
        return null;
    }


}

class TransactionAvg implements Query<Double> {

    private final Transactions transactions;

    TransactionAvg(Transactions transactions) {
        this.transactions = transactions;
    }

    @Override
    public Double execute(Map<String, String> params) {
        TransactionsView view = transactions.execute(params);
        System.out.println("🟢 Avg transakcji: " + view.average());
        return view.average();
    }

    @Override
    public RuleSource ruleSource() {
        return null;
    }


}


abstract class CachedJdbcQuery<T> implements Query<T> {

    private final JdbcTemplate jdbcTemplate;
    private final String sql;
    private final RowMapper<T> rowMapper;
    private T result;

    CachedJdbcQuery(JdbcTemplate jdbcTemplate, String sql, RowMapper<T> rowMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.sql = sql;
        this.rowMapper = rowMapper;
    }

    @Override
    public T execute(Map<String, String> params) {
        if (result != null) {
            return result;
        }
        result = jdbcTemplate.queryForObject(sql, rowMapper);
        return result;
    }

}

class Transactions implements Query<TransactionsView> {

    private final JdbcTemplate jdbcTemplate;
    private TransactionsView cached;

    Transactions(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TransactionsView execute(Map<String, String> params) {
        String sql = build(params.get("user_id"));
        if (cached != null) {
            return cached;
        }
        cached = jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
                new TransactionsView(
                        rs.getDouble("total"),
                        rs.getDouble("average")
                )
        );
        return cached;
    }

    @Override
    public RuleSource ruleSource() {
        return null;
    }

    private String build(String userId) {
        return "SELECT " +
                "  sum(amount) AS total, " +
                "  avg(amount) AS average " +
                "FROM transactions " +
                "WHERE user_id = '" + userId + "' " + // use after sql injection thread analysis
                "  AND transaction_time >= now() - INTERVAL 30 DAY;";
    }

}

record TransactionsView(double total, double average) { }