package com.pageturn.common.security;

import java.util.List;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import com.pageturn.user.TokenService;
import com.pageturn.user.UserAccount;
import com.pageturn.user.UserRepository;

/**
 * Turns a valid token into the signed-in user. The token only says WHO the user is; their role and
 * whether the account is still active always come from the database, never from the token.
 */
@Component
public class UserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    public UserJwtAuthenticationConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID id;
        try {
            id = UUID.fromString(jwt.getSubject());
        }
        catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidBearerTokenException("Invalid session.");
        }
        UserAccount user = users.findById(id)
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new InvalidBearerTokenException("Account not available."));
        Object version = jwt.getClaims().get(TokenService.VERSION_CLAIM);
        if (!(version instanceof Number number) || number.intValue() != user.getTokenVersion()) {
            throw new InvalidBearerTokenException("Session ended. Please sign in again.");
        }
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
        return new UserAuthentication(principal, jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }

    /** Authentication object whose principal is our {@link AuthenticatedUser}. */
    static final class UserAuthentication extends AbstractAuthenticationToken {

        private final AuthenticatedUser principal;
        private final transient Jwt jwt;

        UserAuthentication(AuthenticatedUser principal, Jwt jwt, List<SimpleGrantedAuthority> authorities) {
            super(authorities);
            this.principal = principal;
            this.jwt = jwt;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return jwt;
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }

        @Override
        public String getName() {
            return principal.id().toString();
        }
    }
}
