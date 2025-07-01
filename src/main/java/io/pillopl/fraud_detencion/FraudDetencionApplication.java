package io.pillopl.fraud_detencion;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.CompletableFuture.*;

@SpringBootApplication
public class FraudDetencionApplication {

    FraudDetencionApplication(JdbcTemplate jdbcTemplate, RedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    public static void main(String[] args) {
        SpringApplication.run(FraudDetencionApplication.class, args);
    }

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate redisTemplate;
    private final ExecutorService executors = Executors.newFixedThreadPool(60);

    @PostConstruct
    public void run() {
        try {
            testRules(1, 200000, "user_1231", "Firefox", "device_56648", "email"); //warmup
            System.out.println("-----");
            testRules(2, 200, "user_1234", "Firefox2", "device_56641", "email");
            testRules(3, 209, "user_1234", "Firefox2", "device_56641", "email");
            testRules(4, 200, "user_1234", "Firefox2", "device_56641", "bad@mail.com");
        } finally {
            executors.shutdown();
        }
    }

    private void testRules(int number, int timeout, String userId, String userAgent, String deviceId, String email) {
        Map<String, String> params = Map.of(
                "email", email,
                "user_id", userId,
                "device_id", deviceId,
                "user_agent", userAgent
        );

        List<Rule> rules = loadRules();

        Long start = System.nanoTime();
        int score = runRules(number, timeout, rules, params);
        Long end = System.nanoTime();
        System.out.println("Przejazd: " + number + " Elapsed time: " + Duration.ofNanos(end - start).toMillis());
        System.out.println("Score: " + score);

    }

    private List<Rule> loadRules() {
        Transactions transactions = new Transactions(jdbcTemplate);
        QueriedRule<Boolean> emailOnBlacklist = new QueriedRule<>("emailOnBlacklist",
                result -> result != null && result, new EmailOnBlacklist(redisTemplate),
                Score.of(100));
        QueriedRule<Double> userAgentAnomaly = new QueriedRule<>("userAgentAnomaly",
                data -> data <= 0.3d,
                new UserAgentPercentageUsageQuery(jdbcTemplate),
                Score.of(40));
        QueriedRule<Integer> deviceUsedByLogins = new QueriedRule<>("deviceUsedByLogins",
                data -> data >= 30,
                new DeviceUsedByUsers(jdbcTemplate),
                Score.of(20));
        SingleSourceQueriedRule<TransactionsView> transactionsChecks = new SingleSourceQueriedRule<>("transactionsChecks",
                List.of(
                        data -> data.average() >= 300d ? Score.of(10) : Score.zero(),
                        data -> data.total() >= 300000d ? Score.of(30) : Score.zero()),
                transactions);
        DependentRule<Double, Integer> loginThenTransactionRule = new DependentRule<>("loginThenTransactionRule",
                userAgentAnomaly,
                deviceUsedByLogins,
                percent -> percent >= 0.1d,
                percent -> Map.of("device_id", "zmieniony_device" + percent.intValue()) // przekazujemy tego samego usera
        );

        return List.of(emailOnBlacklist, userAgentAnomaly, deviceUsedByLogins, transactionsChecks, loginThenTransactionRule);
    }

    private int runRules(int number, int timeout, List<Rule> rules, Map<String, String> params) {
        AtomicInteger totalScore = new AtomicInteger(0);

        CompletableFuture<Void> combined = allOf(
                rules.stream() //zapewnic kolejnosc przekazywabnia zadan do kolejki - np za pomoca zwyklego for
                        .map(r -> supplyAsync(() -> {
                            Score calculate = r.calculateAndMeasure(params);
                            totalScore.addAndGet(calculate.score());
                            return calculate;
                        }, executors))
                        .toArray(CompletableFuture[]::new));

        try {
            Long start = System.nanoTime();
            combined.get(timeout, TimeUnit.MILLISECONDS);
            Long end = System.nanoTime();
            System.out.println("Iteration: " + number + ". Elapsed time: " + Duration.ofNanos(end - start).toMillis());
            System.out.println("Score: " + totalScore.get());

        } catch (TimeoutException e) {
            System.err.println("❌ timeout after 200ms");
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return totalScore.get();
    }

}


