package com.usamis.service;

import com.usamis.dao.UserDAO;
import com.usamis.model.Models.User;
import java.util.Optional;

/** Application/service layer: authentication policy stays out of controllers. */
public class AuthService {
    private final UserDAO users;

    public AuthService() { this(new UserDAO()); }
    public AuthService(UserDAO users) { this.users = users; }

    public Optional<User> authenticate(String username, String password) {
        if (username == null || password == null || username.isBlank() || password.isBlank()) {
            return Optional.empty();
        }
        return users.authenticate(username.trim(), password);
    }
}
