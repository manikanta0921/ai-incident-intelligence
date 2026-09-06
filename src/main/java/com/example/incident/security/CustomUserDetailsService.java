package com.example.incident.security;

import com.example.incident.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The bridge between our database and Spring Security.
 *
 * When Spring Security needs to know "who is this user and what can they do?",
 * it calls loadUserByUsername(). We look the user up by email and adapt our
 * entity into the UserDetails contract via CustomUserDetails.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email.toLowerCase())
                .map(user -> new CustomUserDetails(
                        user.getId(),
                        user.getEmail(),
                        user.getPassword(),
                        user.getRole()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }
}
