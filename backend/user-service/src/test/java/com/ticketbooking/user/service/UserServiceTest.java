package com.ticketbooking.user.service;

import com.ticketbooking.common.exception.UnauthorizedActionException;
import com.ticketbooking.user.dto.AuthResponse;
import com.ticketbooking.user.dto.LoginRequest;
import com.ticketbooking.user.dto.RegisterRequest;
import com.ticketbooking.user.dto.UserResponse;
import com.ticketbooking.user.entity.User;
import com.ticketbooking.user.entity.UserRole;
import com.ticketbooking.user.mapper.UserMapper;
import com.ticketbooking.user.repository.UserRepository;
import com.ticketbooking.user.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserMapper userMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @InjectMocks UserService userService;

    private UserResponse sampleResponse() {
        return new UserResponse(UUID.randomUUID(), "a@b.com", "Ada", "CUSTOMER");
    }

    @Test
    void register_succeeds_and_returns_token() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password1")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtService.issue(any(User.class))).thenReturn("tok");
        when(userMapper.toResponse(any(User.class))).thenReturn(sampleResponse());

        AuthResponse res = userService.register(new RegisterRequest("a@b.com", "password1", "Ada"));

        assertEquals("tok", res.token());
        assertEquals("a@b.com", res.user().email());
    }

    @Test
    void register_duplicate_email_conflicts() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(new User()));
        assertThrows(ResponseStatusException.class,
                () -> userService.register(new RegisterRequest("a@b.com", "password1", "Ada")));
    }

    @Test
    void login_succeeds_with_correct_password() {
        User u = new User();
        u.setEmail("a@b.com");
        u.setPasswordHash("hash");
        u.setRole(UserRole.CUSTOMER);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("password1", "hash")).thenReturn(true);
        when(jwtService.issue(u)).thenReturn("tok");
        when(userMapper.toResponse(u)).thenReturn(sampleResponse());

        assertEquals("tok", userService.login(new LoginRequest("a@b.com", "password1")).token());
    }

    @Test
    void login_fails_with_wrong_password() {
        User u = new User();
        u.setEmail("a@b.com");
        u.setPasswordHash("hash");
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        assertThrows(UnauthorizedActionException.class,
                () -> userService.login(new LoginRequest("a@b.com", "wrong")));
    }
}
