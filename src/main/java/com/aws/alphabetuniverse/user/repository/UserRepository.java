package com.aws.alphabetuniverse.user.repository;

import com.aws.alphabetuniverse.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // Ở đây chúng ta có thể viết là: User findByEmail(String email);
    // Nhưng tại sao ko viết vậy mà dùng Optional
    // vì nếu như tìm ko thấy thì sẽ null sẽ gây ra lỗi NullPointExeption
    Optional<User> findByCognitoSub(String cognitoSub);
    Optional<User> findByEmail(String email);
}
