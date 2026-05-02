package com.reservation.domain.user.entity;

import com.reservation.global.common.entity.BaseEntity;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private int pointBalance;

    public static User create(String name, String email, int pointBalance) {
        User user = new User();
        user.name = name;
        user.email = email;
        user.pointBalance = pointBalance;
        return user;
    }

    public void deductPoints(int amount) {
        if (this.pointBalance < amount) {
            throw new GeneralException(ErrorCode.INSUFFICIENT_POINTS);
        }
        this.pointBalance -= amount;
    }

    public void restorePoints(int amount) {
        this.pointBalance += amount;
    }
}
