package io.pillopl.fraud_detencion;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public interface Rule {
    Score calculate(Map<String, String> params);

    default Score calculateAndMeasure(Map<String, String> params) {
        Long start = System.nanoTime();
        Score score = calculate(params);
        Long end = System.nanoTime();
        System.out.println("Elapsed time of " + this.getClass() + ": " + Duration.ofNanos(end - start).toMillis());
        return score;
    }

    String id();
}

class QueriedRule<T> implements Rule {

    final String id;
    final ScoreCheck<T> check;
    final Query<T> query;

    QueriedRule(String id, ScoreCheck check, Query query) {
        this.id = id;
        this.check = check;
        this.query = query;
    }

    QueriedRule(String id, Predicate<T> check, Query<T> query, Score score) {
        this.id = id;
        this.check = data -> {
            if (check.test(data)) {
                return score;
            }
            return Score.zero();
        };
        this.query = query;
    }

    @Override
    public Score calculate(Map<String, String> params) {
        T result = query.measureAndExecute(params);
        return check.scoreOver(result);
    }

    @Override
    public String id() {
        return id;
    }
}

class NonQueriedRule implements Rule {

    final String id;
    final ScoreCheck<Map<String, String>> check;

    NonQueriedRule(String id, ScoreCheck<Map<String, String>> check) {
        this.id = id;
        this.check = check;
    }

    @Override
    public Score calculate(Map<String, String> params) {
        return check.scoreOver(params);
    }

    @Override
    public String id() {
        return id;
    }
}

//a co jak jeden scorecheck potrzebuje dodatkowego zrodla?
class SingleSourceQueriedRule<T> implements Rule {

    private final String id;
    private final List<ScoreCheck<T>> checks;
    private final Query<T> query;

    SingleSourceQueriedRule(String id, List<ScoreCheck<T>> checks, Query<T> query) {
        this.id = id;
        this.checks = checks;
        this.query = query;
    }

    @Override
    public Score calculate(Map<String, String> params) {
        T result = query.measureAndExecute(params);
        Score score = Score.zero();
        for (ScoreCheck<T> check : checks) {
            Score singleScore = check.scoreOver(result);
            score = score.add(singleScore);
        }
        return score;
    }

    @Override
    public String id() {
        return id;
    }
}

class DependentRule<T, P> implements Rule {

    private final String id;
    private final QueriedRule<T> source;
    private final QueriedRule<P> sink;
    private final Predicate<T> condition; //todo - a co jak to jest kosztowne czasowo?
    private final Function<T, Map<String, String>> transmiter;

    DependentRule(String id, QueriedRule<T> source, QueriedRule<P> sink, Predicate<T> condition, Function<T, Map<String, String>> transmiter) {
        this.id = id;
        this.source = source;
        this.sink = sink;
        this.condition = condition;
        this.transmiter = transmiter;
    }

    @Override
    public Score calculate(Map<String, String> params) {
        Score score = Score.zero();
        T sourceResult = source.query.execute(params);
        score = score.add(source.check.scoreOver(sourceResult));
        if (condition.test(sourceResult)) {
            Map<String, String> newParams = new HashMap<>();
            newParams.putAll(params);
            newParams.putAll(transmiter.apply(sourceResult));
            P sinkResult = sink.query.execute(newParams);
            score = score.add(sink.check.scoreOver(sinkResult));
            return score;
        }
        return score;
    }

    @Override
    public String id() {
        return id;
    }
}

interface Query<T> {
    T execute(Map<String, String> params);

    default T measureAndExecute(Map<String, String> params) {
        Long start = System.nanoTime();
        T execute = execute(params);
        Long end = System.nanoTime();
        System.out.println("Elapsed time of " + this.getClass() + ": " + Duration.ofNanos(end - start).toMillis());
        return execute;
    }

    RuleSource ruleSource();
}

interface ScoreCheck<T> {
    Score scoreOver(T data);
}

record Score(int score) {
    static Score zero() {
        return new Score(0);
    }

    static Score of(int score) {
        return new Score(score);
    }

    Score add(Score score) {
        return new Score(this.score() + score.score());
    }
}


