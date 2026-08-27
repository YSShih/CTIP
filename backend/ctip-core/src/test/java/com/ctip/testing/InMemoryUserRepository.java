package com.ctip.testing;

import com.ctip.application.port.UserRepository;
import com.ctip.domain.user.EmailAddress;
import com.ctip.domain.user.User;
import com.ctip.domain.user.UserId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 測試用 in-memory UserRepository。 */
public final class InMemoryUserRepository implements UserRepository {

    private final Map<UserId, User> byId = new LinkedHashMap<>();

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<User> findByEmail(EmailAddress email) {
        return byId.values().stream().filter(u -> u.email().equals(email)).findFirst();
    }

    @Override
    public boolean existsByEmail(EmailAddress email) {
        return findByEmail(email).isPresent();
    }

    @Override
    public User save(User user) {
        byId.put(user.id(), user);
        return user;
    }
}
