package ola.hd.longtermstorage.config;

import ola.hd.longtermstorage.component.CustomAuthenticationEntryPoint;
import ola.hd.longtermstorage.component.TokenProvider;
import ola.hd.longtermstorage.filter.JwtFilter;
import ola.hd.longtermstorage.utils.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.BeanIds;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final TokenProvider tokenProvider;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    @Value("${useKeycloak:false}")
    private boolean useKeycloak;

    @Autowired
    public SecurityConfig(TokenProvider tokenProvider,
                          CustomAuthenticationEntryPoint customAuthenticationEntryPoint) {
        this.tokenProvider = tokenProvider;
        this.customAuthenticationEntryPoint = customAuthenticationEntryPoint;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // @formatter:off
        http
            .csrf().disable()
            .cors().and()
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
            .exceptionHandling()
                .authenticationEntryPoint(this.customAuthenticationEntryPoint)
                .and()
            .authorizeRequests()
                .antMatchers("/export/**",
                        "/export-full/**",
                        "/download",
                        "/download-file/**",
                        "/login",
                        "/search*/**",
                        "/configuration/**", "/swagger*/**", "/webjars/**").permitAll()
                .anyRequest().authenticated()
                .and()
            .httpBasic();
        if (useKeycloak) {
            http.oauth2ResourceServer().jwt();
            Utils.logWarn("Using Keycloak");
        } else {
            http.addFilterBefore(new JwtFilter(this.tokenProvider), BasicAuthenticationFilter.class);
            Utils.logWarn("Not using Keycloak");
        }
        // @formatter:on
    }

    @Bean(BeanIds.AUTHENTICATION_MANAGER)
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }
}
