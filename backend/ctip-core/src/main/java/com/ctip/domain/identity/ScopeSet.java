package com.ctip.domain.identity;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** API key 的權限範圍。每項須符合 permission code 格式(不變量 K3 的格式面)。 */
public record ScopeSet(Set<String> values) {

    private static final Pattern CODE = Pattern.compile("^[a-z]+:[a-z-]+$");

    public ScopeSet {
        if (values == null) {
            throw new IllegalArgumentException("scopes 不得為 null");
        }
        values.forEach(scope -> {
            if (scope == null || !CODE.matcher(scope).matches()) {
                throw new IllegalArgumentException("scope 格式不符:" + scope);
            }
        });
        values = Set.copyOf(new LinkedHashSet<>(values));
    }

    public boolean contains(String scope) {
        return values.contains(scope);
    }

    /** 不變量 K3/K4:必須是給定權限集合的子集。 */
    public boolean isSubsetOf(Set<String> granted) {
        return granted.containsAll(values);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
