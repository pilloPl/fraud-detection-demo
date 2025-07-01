package io.pillopl.fraud_detencion;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.pillopl.fraud_detencion.RuleDependency.Type.Forced;
import static io.pillopl.fraud_detencion.RuleDependency.Type.NeedsData;
import static org.junit.jupiter.api.Assertions.*;

class RulesConfigFromRulesRequestTest {

    @Test
    void testNoDependenciesIsValid() {
        // Given
        RequestedRule<?> a = createRule("A");
        RequestedRule<?> b = createRule("B");
        RulesRequest config = new RulesRequest(Set.of(a, b));

        // When
        boolean result = config.isValid();

        // Then
        assertTrue(result);
    }

    @Test
    void testSimpleLinearDependencyIsValid() {
        // Given
        RequestedRule<?> a = createRule("A");
        RequestedRule<?> b = createRule("B", new RuleDependency("A", Forced));
        RulesRequest config = new RulesRequest(Set.of(a, b));

        // When
        boolean result = config.isValid();

        // Then
        assertTrue(result);
    }

    @Test
    void testCycleDependencyIsInvalid() {
        // Given
        RequestedRule<?> a = createRule("A", new RuleDependency("C", Forced));
        RequestedRule<?> b = createRule("B", new RuleDependency("A", NeedsData));
        RequestedRule<?> c = createRule("C", new RuleDependency("B", Forced));
        RulesRequest config = new RulesRequest(Set.of(a, b, c));

        // When
        boolean result = config.isValid();

        // Then
        assertFalse(result);
    }

    @Test
    void testDisconnectedComponentsValid() {
        // Given
        RequestedRule<?> a = createRule("A");
        RequestedRule<?> b = createRule("B", new RuleDependency("A", Forced));
        RequestedRule<?> x = createRule("X");
        RequestedRule<?> y = createRule("Y", new RuleDependency("X", NeedsData));
        RulesRequest config = new RulesRequest(Set.of(a, b, x, y));

        // When
        boolean result = config.isValid();

        // Then
        assertTrue(result);
    }

    @Test
    void testSelfLoopIsInvalid() {
        // Given
        RequestedRule<?> a = createRule("A", new RuleDependency("A", Forced));
        RulesRequest config = new RulesRequest(Set.of(a));

        // When
        boolean result = config.isValid();

        // Then
        assertFalse(result);
    }

    RequestedRule<Object> createRule(String id, RuleDependency dependsOn) {
        return new RequestedRule<>(id, null, null, dependsOn, null);
    }

    RequestedRule<Object> createRule(String id) {
        return new RequestedRule<>(id, null, null, null, null);
    }

}