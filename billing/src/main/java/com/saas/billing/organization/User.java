package com.saas.billing.organization;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User{

    @Id
    @GeneratedValue(strategy=GenerationType.UUID)
    @Column(updatable=false, nullable=false)
    private UUID id;


    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="org_id",nullable=false)
    private Organization org;

    @Column(nullable=false,unique=true)
    private String email;

    @Column(name="password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(name="created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate(){
        this.createdAt=LocalDateTime.now();
        if(this.role==null){
            this.role=UserRole.ADMIN;
        }
    }

    public enum  UserRole{
        ADMIN, MEMBER
    }

}