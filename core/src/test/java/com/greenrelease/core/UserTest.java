package com.greenrelease.core;

import org.junit.Test;
import com.greenrelease.core.model.User;

import static org.junit.Assert.*;

/**
 * Unit tests for User domain model
 */
public class UserTest {

    @Test
    public void testUserCreation() {
        User user = new User(1, "Alice", "alice@example.com");

        assertEquals(1, user.getId());
        assertEquals("Alice", user.getName());
        assertEquals("alice@example.com", user.getEmail());
    }

    @Test
    public void testUserEquality() {
        User user1 = new User(1, "Alice", "alice@example.com");
        User user2 = new User(1, "Alice", "alice@example.com");

        assertEquals(user1, user2);
        assertEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    public void testUserInequality() {
        User user1 = new User(1, "Alice", "alice@example.com");
        User user2 = new User(2, "Bob", "bob@example.com");

        assertNotEquals(user1, user2);
    }
}
