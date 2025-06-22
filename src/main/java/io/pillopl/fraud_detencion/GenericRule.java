package io.pillopl.fraud_detencion;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

class GenericRule<T> implements Rule {

    final ScoreCheck<T> check;
    final Query<T> query;

    GenericRule(Predicate<T> check, Query<T> query, Score score) {
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
}

class SingleSourceRule<T> implements Rule {

    private final List<ScoreCheck<T>> checks;
    private final Query<T> query;

    SingleSourceRule(List<ScoreCheck<T>> checks, Query<T> query) {
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
}

class DependentRule<T, P> implements Rule {

    private final GenericRule<T> source;
    private final GenericRule<P> sink;
    private final Predicate<T> condition;
    private final Function<T, Map<String, String>> transmiter;

    DependentRule(GenericRule<T> source, GenericRule<P> sink, Predicate<T> condition, Function<T, Map<String, String>> transmiter) {
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
}

interface Rule {
    Score calculate(Map<String, String> params);

    default Score calculateAndMeasure(Map<String, String> params) {
        Long start = System.nanoTime();
        Score score = calculate(params);
        Long end = System.nanoTime();
        System.out.println("Elapsed time of " + this.getClass() + ": " + Duration.ofNanos(end - start).toMillis());
        return score;
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


