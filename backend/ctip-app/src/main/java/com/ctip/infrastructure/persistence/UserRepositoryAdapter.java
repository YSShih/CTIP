package com.ctip.infrastructure.persistence;

import com.ctip.application.port.UserRepository;
import com.ctip.domain.user.EmailAddress;
import com.ctip.domain.user.User;
import com.ctip.domain.user.UserId;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** UserRepository port 的 JPA 實作。 */
@Repository
@Transactional
class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpa;
    private final UserMapper mapper;

    UserRepositoryAdapter(UserJpaRepository jpa, UserMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UserId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(EmailAddress email) {
        return jpa.findByEmail(email.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(EmailAddress email) {
        return jpa.existsByEmail(email.value());
    }

    @Override
    public User save(User user) {
        UserEntity entity = jpa.findById(user.id().value()).orElseGet(UserEntity::new);
        mapper.updateEntity(user, entity);
        return mapper.toDomain(jpa.save(entity));
    }
}
