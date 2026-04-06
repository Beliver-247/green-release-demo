package com.greenrelease.service;

import com.greenrelease.core.model.User;

import java.util.Arrays;
import java.util.List;

/**
 * Service for managing users - immutable in-memory list
 */
public class UserService {

    private static final List<User> USERS = Arrays.asList(
            new User(1, "Alice Johnson", "alice@example.com"),
            new User(2, "Bob Smith", "bob@example.com"),
            new User(3, "Charlie Brown", "charlie@example.com")
    );

    /**
     * Get all users
     */
    public List<User> getAllUsers() {
        return USERS;
    }

    /**
     * Get user by ID, returns null if not found
     */
    public User getUserById(int id) {
        return USERS.stream()
                .filter(user -> user.getId() == id)
                .findFirst()
                .orElse(null);
    }
}
