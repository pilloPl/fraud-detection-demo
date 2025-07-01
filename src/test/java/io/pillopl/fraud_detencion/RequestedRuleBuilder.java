package io.pillopl.fraud_detencion;

import io.pillopl.fraud_detencion.RuleDependency.Type;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

class RequestedRuleBuilder<T> {

    private String id = UUID.randomUUID().toString();
    private Optional<Query<T>> query = Optional.empty();
    private ScoreCheck<T> scoreCheck;
    private RuleDependency dependsOn = null;
    private RuleSource ruleSource = new RuleSource("default", "default");

    static <T> RequestedRuleBuilder<T> create() {
        return new RequestedRuleBuilder<>();
    }

    RequestedRuleBuilder<T> id(String id) {
        this.id = id;
        return this;
    }

    RequestedRuleBuilder<T> query(Query<T> query) {
        this.query = Optional.ofNullable(query);
        return this;
    }

    RequestedRuleBuilder<T> query(Supplier<T> supplier, String dataSource, String view) {
        this.ruleSource = new RuleSource(dataSource, view);

        Query<T> q = new Query<T>() {
            @Override
            public T execute(Map<String, String> params) {
                return supplier.get();
            }

            @Override
            public RuleSource ruleSource() {
                return ruleSource;
            }
        };
        this.query = Optional.of(q);
        return this;
    }

    RequestedRuleBuilder<T> scoreCheck(ScoreCheck<T> check) {
        this.scoreCheck = check;
        return this;
    }

    RequestedRuleBuilder<T> dependsOn(String otherId, Type type) {
        this.dependsOn = new RuleDependency(otherId, type);
        return this;
    }

    RequestedRule<T> build() {
        return new RequestedRule<>(id, query, scoreCheck, dependsOn, ruleSource);
    }
}