package ma.impactboard;

import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){ return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService userDetailsService(UserRepository users){
        return username -> users.findByUsername(username).map(u -> User.withUsername(u.username).password(u.passwordHash).roles(u.role.name()).build()).orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable"));
    }
    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception { return config.getAuthenticationManager(); }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.requestMatchers("/api/auth/login", "/api/auth/register", "/", "/index.html", "/styles.css", "/app.js", "/favicon.ico").permitAll().anyRequest().authenticated())
            .exceptionHandling(ex -> ex.authenticationEntryPoint((req,res,error)->res.sendError(401)))
            .logout(logout -> logout.logoutUrl("/api/auth/logout").logoutSuccessHandler((req,res,auth)->res.setStatus(204)))
            .build();
    }
}
