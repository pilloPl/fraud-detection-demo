package io.pillopl.fraud_detencion;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

record RulesRequest(Set<RequestedRule<?>> requestedRules) {

    boolean isValid() {
        Graf<String, String> graph = new Graf<>();
        for (RequestedRule<?> rule : requestedRules) {
            graph.addNode(rule.id());
            if (rule.dependsOn() != null) {
                graph.addEdge(rule.id(), rule.dependsOn().dependentOnRule(), rule.dependsOn().type().name());
            }
        }
        return !graph.hasCycles();
    }

    RulesConfig createConfig() {
        if (!isValid()) {
            return new RulesConfig(new HashSet<>());
        }
        Set<Rule> rules = new HashSet<>();
        Set<String> usedIds = new HashSet<>();

        rules.addAll(nonQueriedRules(usedIds));
        //dependent queried rules
        rules.addAll(groupDependentQueriedRules(usedIds));

        //grouped queried rules (by source and by view)
        rules.addAll(groupBySameDataSource(usedIds));

        //rest of the queried rules
        rules.addAll(doesNotGroupQueriedRules(usedIds));
        return new RulesConfig(rules);

    }

    private Set<Rule> nonQueriedRules(Set<String> usedIds) {
        Set<Rule> rules = new HashSet<>();
        List<NonQueriedRule> list = requestedRules.stream().filter(r -> r.query().isEmpty()).map(r -> new NonQueriedRule(r.id(), (ScoreCheck<Map<String, String>>) r.scoreCheck())).toList();
        rules.addAll(list);
        usedIds.addAll(list.stream().map(NonQueriedRule::id).toList());
        return rules;
    }

    private Set<Rule> groupDependentQueriedRules(Set<String> usedIds) {
        //TODO: does not support chains yet
        Set<Rule> rules = new HashSet<>();
        Set<RequestedRule<?>> rulesWithDependency = fitlerUsedIds(usedIds)
                .stream().filter(r -> r.dependsOn() != null).collect(toSet());
        for (RequestedRule<?> rule : rulesWithDependency) {
            Optional<RequestedRule<?>> poteniallyDependentOn = requestedRules.stream().filter(r -> r.id().equals(rule.dependsOn().dependentOnRule())).findFirst();
            if (poteniallyDependentOn.isPresent()) {
                RequestedRule<?> dependentOnRule = poteniallyDependentOn.get();
                String dependentOnRuleId = dependentOnRule.id();
                String ruleId = rule.id();
                DependentRule<?, ?> dependentRule = new DependentRule<>(ruleId + dependentOnRuleId, new QueriedRule<>(dependentOnRuleId, dependentOnRule.scoreCheck(), dependentOnRule.query().get()), new QueriedRule<>(ruleId, rule.scoreCheck(), rule.query().get()), rule.dependsOn().condition(), rule.dependsOn().transmiter());
                rules.add(dependentRule);
                usedIds.add(ruleId);
                usedIds.add(dependentOnRuleId);

            }
        }
        return rules;
    }

    private Set<Rule> groupBySameDataSource(Set<String> usedIds) {
        Set<Rule> rules = new HashSet<>();
        Map<String, Map<String, List<RequestedRule<?>>>> rulesGroupedByDataSourceAndView =
                requestedRules.stream()
                        .filter(rule -> rule.query().isPresent())
                        .collect(groupingBy(r -> r.ruleSource().datasource(),
                                groupingBy(r -> r.ruleSource().view())));
        for (String dataSource : rulesGroupedByDataSourceAndView.keySet()) { //TODO: if datasource supports multi-query it makes sense to group a query
            Map<String, List<RequestedRule<?>>> groupedByView = rulesGroupedByDataSourceAndView.get(dataSource);

            for (String view : groupedByView.keySet()) {
                if (groupedByView.get(view).size() > 1) {
                    List<RequestedRule<?>> rulesWithTheSameQuery = groupedByView.get(view);
                    RequestedRule<?> rule = rulesWithTheSameQuery.getFirst();
                    Rule objectSingleSourceQueriedRule = buildSingleSourceRule(rule.query().get(), rulesWithTheSameQuery);
                    rules.add(objectSingleSourceQueriedRule);
                    usedIds.addAll(rulesWithTheSameQuery.stream().map(RequestedRule::id).toList());
                }
            }
        }
        return rules;
    }

    private <T> SingleSourceQueriedRule<T> buildSingleSourceRule(Query<?> query, List<RequestedRule<?>> rules) { //group by the same view in the same datasource (cache)
        List<RequestedRule<T>> grouped = rules.stream()
                .map(r -> (RequestedRule<T>) r)
                .toList();

        List<ScoreCheck<T>> checks = grouped.stream()
                .map(RequestedRule::scoreCheck)
                .toList();
        String id = grouped.stream().map(RequestedRule::id).collect(Collectors.joining());
        return new SingleSourceQueriedRule<>(id, checks, (Query<T>) query);
    }

    private Set<RequestedRule<?>> fitlerUsedIds(Set<String> usedIds) {
        return requestedRules.stream().filter(r -> !usedIds.contains(r.id())).collect(toSet());
    }

    private Set<Rule> doesNotGroupQueriedRules(Set<String> usedIds) {
        Set<RequestedRule<?>> rest = fitlerUsedIds(usedIds);
        List<QueriedRule<Object>> simpleQueriedRule = rest.stream().map(rule -> new QueriedRule<>(rule.id(), rule.scoreCheck(), rule.query().get())).toList();
        usedIds.addAll(rest.stream().map(RequestedRule::id).toList());
        return new HashSet<>(simpleQueriedRule);
    }

}

record RequestedRule<T>(String id, Optional<Query<T>> query, ScoreCheck<T> scoreCheck, RuleDependency dependsOn,
                        RuleSource ruleSource) {


}

record RuleSource(String datasource, String view) {
};

record RuleDependency<T>(String dependentOnRule, Type type, Predicate<T> condition,
                         Function<T, Map<String, String>> transmiter) {

    RuleDependency(String dependentOnRule, Type type) {
        this(dependentOnRule, type, null, null);
    }

    enum Type {
        Forced, NeedsData
    }
}

class RulesConfigRepo {

    RulesConfig findConfig() {
        return null;
    }
}


class Graf<N, E> {

    private final Map<N, List<Edge<N, E>>> adjacencyList = new HashMap<>();

    record Edge<N, E>(N to, E label) {
    }

    void addNode(N node) {
        adjacencyList.putIfAbsent(node, new ArrayList<>());
    }

    void addEdge(N from, N to, E label) {
        addNode(from);
        addNode(to);
        adjacencyList.get(from).add(new Edge<>(to, label));
    }

    boolean hasCycles() {
        Set<N> visited = new HashSet<>();
        Set<N> onPath = new HashSet<>();

        for (N node : adjacencyList.keySet()) {
            if (!visited.contains(node)) {
                if (dfs(node, visited, onPath)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean dfs(N node, Set<N> visited, Set<N> onPath) {
        if (onPath.contains(node)) {
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }
        onPath.add(node);
        for (Edge<N, E> edge : adjacencyList.getOrDefault(node, List.of())) {
            if (dfs(edge.to(), visited, onPath)) {
                return true;
            }
        }
        onPath.remove(node);
        visited.add(node);
        return false;
    }
}
