package io.pillopl.fraud_detencion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

import java.util.List;
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