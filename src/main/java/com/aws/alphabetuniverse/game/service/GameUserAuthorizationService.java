
package com.aws.alphabetuniverse.game.service;

import com.aws.alphabetuniverse.user.entity.User;

import org.springframework.security.oauth2.jwt.Jwt;

public interface GameUserAuthorizationService {

    User findAuthenticatedUser(Jwt jwt);

    User requireActiveGameUser(Jwt jwt);

    void assertActiveGameUser(User user);
}
