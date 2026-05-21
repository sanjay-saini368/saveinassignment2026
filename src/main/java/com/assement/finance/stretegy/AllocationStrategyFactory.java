package com.assement.finance.stretegy;

import com.assement.finance.Enums.AllocationStrategy;
import com.assement.finance.stretegy.RepaymentAllocationStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@Component
public class AllocationStrategyFactory {

    private final Map<AllocationStrategy, RepaymentAllocationStrategy> strategyMap;

    public AllocationStrategyFactory(List<RepaymentAllocationStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(RepaymentAllocationStrategy::getStrategy, Function.identity()));
    }

    public RepaymentAllocationStrategy resolve(AllocationStrategy strategy) {
        RepaymentAllocationStrategy resolved = strategyMap.get(strategy);
        if (resolved == null) {
            throw new IllegalArgumentException("No allocation strategy found for: " + strategy);
        }
        return resolved;
    }
}