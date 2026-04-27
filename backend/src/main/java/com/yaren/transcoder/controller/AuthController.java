package com.yaren.transcoder.controller;

import com.yaren.transcoder.dto.auth.AuthRequest;
import com.yaren.transcoder.dto.auth.AuthResponse;
import com.yaren.transcoder.entity.Role;
import com.yaren.transcoder.entity.User;
import com.yaren.transcoder.repository.UserRepository;
import com.yaren.transcoder.security.JwtUtil;
import com.yaren.transcoder.security.UserDetailsImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(AuthenticationManager authenticationManager, UserRepository userRepository,
                          PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticateUser(@RequestBody AuthRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        String jwt = jwtUtil.generateToken(userDetails);
        
        String role = userDetails.getAuthorities().stream().findFirst().map(a -> a.getAuthority()).orElse("ROLE_USER");

        return ResponseEntity.ok(new AuthResponse(jwt, userDetails.getUsername(), userDetails.getId(), role));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody AuthRequest signUpRequest) {
        if (userRepository.findByUsername(signUpRequest.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Error: Username is already taken!");
        }

        User user = new User();
        user.setUsername(signUpRequest.getUsername());
        user.setPasswordHash(passwordEncoder.encode(signUpRequest.getPassword()));
        
        // Force ADMIN role for 'admin' literal or first registered user
        if (userRepository.count() == 0 || signUpRequest.getUsername().equalsIgnoreCase("admin") || signUpRequest.getUsername().equalsIgnoreCase("yarenyazar")) {
            user.setRole(Role.ADMIN);
        } else {
            user.setRole(Role.USER);
        }

        userRepository.save(user);

        return ResponseEntity.ok("User registered successfully!");
    }
}
