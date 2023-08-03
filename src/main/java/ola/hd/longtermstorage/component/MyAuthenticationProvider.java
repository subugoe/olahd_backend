package ola.hd.longtermstorage.component;

import ola.hd.longtermstorage.domain.MongoUser;
import ola.hd.longtermstorage.repository.mongo.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Component;

@Component
public class MyAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository userRepository;

    @Autowired
    public MyAuthenticationProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        // Already authenticated? Pass
        if (authentication.isAuthenticated()) {
            return authentication;
        }

        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        // Check for test users
        MongoUser testUser = userRepository.findByUsernameAndPassword(username, password);
        if (testUser != null) {
            return new UsernamePasswordAuthenticationToken(username, password, AuthorityUtils.createAuthorityList("ROLE_USER"));
        }

        // Incorrect username or password
        throw new BadCredentialsException("Full authentication is required to access this resource.");
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
