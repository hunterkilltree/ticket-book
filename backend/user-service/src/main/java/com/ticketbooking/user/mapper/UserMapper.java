package com.ticketbooking.user.mapper;

import com.ticketbooking.user.dto.UserResponse;
import com.ticketbooking.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
    }
}
