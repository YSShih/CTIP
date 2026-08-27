package com.ctip.application.identity;

import com.ctip.application.port.EventPublisherPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.application.port.PasswordHasherPort;
import com.ctip.application.port.UserRepository;
import com.ctip.application.rbac.RoleCode;
import com.ctip.domain.tenant.Tenant;
import com.ctip.domain.user.EmailAddress;
import com.ctip.domain.user.RawPassword;
import com.ctip.domain.user.User;
import com.ctip.domain.user.UserId;
import com.ctip.domain.user.UserSnapshot;
import com.ctip.domain.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 註冊:建立租戶 → 建立使用者 → 指派角色。不變量 U1(email 唯一)於此先行檢查。 */
@Service
public class UserRegistrar {

    private final UserRepository users;
    private final TenantProvisioner tenantProvisioner;
    private final PasswordHasherPort passwordHasher;
    private final IdGeneratorPort idGenerator;
    private final EventPublisherPort events;

    public UserRegistrar(
            UserRepository users,
            TenantProvisioner tenantProvisioner,
            PasswordHasherPort passwordHasher,
            IdGeneratorPort idGenerator,
            EventPublisherPort events) {
        this.users = users;
        this.tenantProvisioner = tenantProvisioner;
        this.passwordHasher = passwordHasher;
        this.idGenerator = idGenerator;
        this.events = events;
    }

    @Transactional
    public User register(AuthCommands.Register command) {
        EmailAddress email = EmailAddress.of(command.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException("Email already registered");
        }
        RawPassword password = new RawPassword(command.password());
        Tenant tenant = tenantProvisioner.provision(command.tenantName(), localPartOf(email));
        User user = User.register(new UserSnapshot(
                new UserId(idGenerator.nextId()),
                email,
                passwordHasher.hash(password.value()),
                command.displayName(),
                UserStatus.ACTIVE,
                tenant.id(),
                null,
                0,
                null));
        User saved = users.save(user);
        tenantProvisioner.enroll(tenant.id(), saved.id(), RoleCode.TENANT_ADMIN);
        user.pullEvents().forEach(events::publish);
        return saved;
    }

    private static String localPartOf(EmailAddress email) {
        String value = email.value();
        return value.substring(0, value.indexOf('@'));
    }
}
