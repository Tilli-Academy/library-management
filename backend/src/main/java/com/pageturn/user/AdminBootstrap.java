package com.pageturn.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pageturn.common.web.TextRules;

/**
 * Creates the first librarian from PAGETURN_ADMIN_EMAIL / PAGETURN_ADMIN_PASSWORD when the app starts.
 *
 * <p>There is no "make me admin" endpoint: sign-up always creates members. If the email already exists,
 * nothing is changed (the password in the environment never overwrites an existing account).
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(AdminProperties properties, UserRepository users, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = UserAccount.normalizeEmail(properties.email());
        if (email.isEmpty()) {
            if (users.countByRoleAndEnabledTrue(Role.LIBRARIAN) == 0) {
                log.warn("No librarian account exists. Set PAGETURN_ADMIN_EMAIL and PAGETURN_ADMIN_PASSWORD "
                        + "in .env and restart to create one.");
            }
            return;
        }
        var existing = users.findByEmail(email);
        if (existing.isPresent()) {
            if (existing.get().getRole() == Role.LIBRARIAN) {
                log.info("Admin account from PAGETURN_ADMIN_EMAIL already exists; leaving it unchanged.");
            }
            else {
                // Someone signed up with this email first. Never promote it automatically.
                log.warn("PAGETURN_ADMIN_EMAIL belongs to an existing MEMBER account, so no librarian was created. "
                        + "Use a different email, or promote that account in the database if it really is yours.");
            }
            return;
        }
        String password = properties.password() == null ? "" : properties.password();
        if (password.length() < 12) {
            throw new IllegalStateException("PAGETURN_ADMIN_PASSWORD must be at least 12 characters.");
        }
        AuthService.checkPasswordLength(password);
        String name = TextRules.clean(properties.name());
        users.save(new UserAccount(email, passwordEncoder.encode(password),
                name == null ? "Head Librarian" : name, Role.LIBRARIAN));
        log.info("Created librarian account for the email in PAGETURN_ADMIN_EMAIL.");
    }
}
