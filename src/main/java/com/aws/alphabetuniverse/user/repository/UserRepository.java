package com.aws.alphabetuniverse.user.repository;

import com.aws.alphabetuniverse.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

import java.time.LocalDateTime;
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

    // Khóa User theo Cognito Sub khi Auth Sync.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT u
            FROM User u
            WHERE u.cognitoSub = :cognitoSub
            """)
    Optional<User> findByCognitoSubForUpdate(
            @Param("cognitoSub") String cognitoSub
    );
    // Tạo User nếu chưa tồn tại.
    // Nếu Cognito Sub đã tồn tại thì bỏ qua INSERT.
    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query(
            value = """
                    INSERT INTO public.users (
                        cognito_sub,
                        email,
                        display_name,
                        role,
                        status,
                        created_at,
                        updated_at
                    )
                    VALUES (
                        :cognitoSub,
                        :email,
                        :displayName,
                        :role,
                        :status,
                        :createdAt,
                        :updatedAt
                    )
                    ON CONFLICT (cognito_sub)
                    DO NOTHING
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("cognitoSub") String cognitoSub,
            @Param("email") String email,
            @Param("displayName") String displayName,
            @Param("role") String role,
            @Param("status") String status,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    // Kiểm tra email đã thuộc User khác hay chưa.
    boolean existsByEmailIgnoreCaseAndIdNot(
            String email,
            Long id
    );
}
