package com.itsectest.user.api;

public record RegisterUserCommand(String fullname, String username, String email, String rawPassword) {
}
