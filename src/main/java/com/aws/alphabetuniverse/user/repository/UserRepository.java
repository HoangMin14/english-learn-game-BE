package com.aws.alphabetuniverse.user.repository;

import com.aws.alphabetuniverse.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    // Ở đây chúng ta có thể viết là: User findByEmail(String email);
    // Nhưng tại sao ko viết vậy mà dùng Optional
    // vì nếu như tìm ko thấy thì sẽ null sẽ gây ra lỗi NullPointExeption
    Optional<User> findByCognitoSub(String cognitoSub);
    Optional<User> findByEmail(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);
}
