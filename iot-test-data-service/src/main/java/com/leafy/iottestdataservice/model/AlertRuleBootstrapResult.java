package com.leafy.iottestdataservice.model;

import java.util.List;

public record AlertRuleBootstrapResult(
    int createdCount,
    List<String> warnings
) {
}
