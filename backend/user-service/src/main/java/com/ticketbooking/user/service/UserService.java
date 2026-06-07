package com.ticketbooking.user.service;

import com.ticketbooking.common.exception.GeneralNotFoundException;
import com.ticketbooking.common.exception.UnauthorizedActionException;
import com.ticketbooking.user.dto.AuthResponse;
import com.ticketbooking.user.dto.LoginRequest;
import com.ticketbooking.user.dto.RegisterRequest;
import com.ticketbooking.user.dto.UserResponse;
import com.ticketbooking.user.entity.User;
import com.ticketbooking.user.mapper.UserMapper;
import com.ticketbooking.user.repository.UserRepository;
import com.ticketbooking.user.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        userRepository.findByEmail(req.email()).ifPresent(u -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        });
        User user = new User();
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullName(req.fullName());
        userRepository.save(user);
        return new AuthResponse(jwtService.issue(user), userMapper.toResponse(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new UnauthorizedActionException("Invalid email or password"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new UnauthorizedActionException("Invalid email or password");
        }
        return new AuthResponse(jwtService.issue(user), userMapper.toResponse(user));
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new GeneralNotFoundException("User", id));
        return userMapper.toResponse(user);
    }
}
