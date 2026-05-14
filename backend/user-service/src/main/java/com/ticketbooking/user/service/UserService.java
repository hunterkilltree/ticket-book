package com.ticketbooking.user.service;

import com.ticketbooking.common.exception.GeneralNotFoundException;
import com.ticketbooking.user.dto.UserResponse;
import com.ticketbooking.user.entity.User;
import com.ticketbooking.user.mapper.UserMapper;
import com.ticketbooking.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new GeneralNotFoundException("User", id));
        return userMapper.toResponse(user);
    }
}
