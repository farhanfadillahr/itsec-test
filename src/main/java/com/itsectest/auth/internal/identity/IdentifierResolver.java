package com.itsectest.auth.internal.identity;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.itsectest.user.api.UserAccount;

@Component
public class IdentifierResolver {

    private final List<IdentifierStrategy> strategies;

    public IdentifierResolver(List<IdentifierStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    public Optional<UserAccount> resolve(String identifier) {
        return strategyFor(identifier).flatMap(strategy -> strategy.resolve(identifier));
    }

    public String kindOf(String identifier) {
        return strategyFor(identifier).map(IdentifierStrategy::kind).orElse("UNKNOWN");
    }

    private Optional<IdentifierStrategy> strategyFor(String identifier) {
        return strategies.stream().filter(strategy -> strategy.supports(identifier)).findFirst();
    }
}
