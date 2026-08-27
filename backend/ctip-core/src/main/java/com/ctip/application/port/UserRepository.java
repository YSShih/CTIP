package com.ctip.application.port;

import com.ctip.domain.user.EmailAddress;
import com.ctip.domain.user.User;
import com.ctip.domain.user.UserId;
import java.util.Optional;

/** User 聚合的持久化 out-port(docs/spec/01-architecture.md §1.4)。 */
public interface UserRepository {

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(EmailAddress email);

    boolean existsByEmail(EmailAddress email);

    User save(User user);
}
