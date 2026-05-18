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

    //@ManyToOne — many users can belong to one organization. This is the Java side of the foreign key you wrote in SQL.
    //fetch = FetchType.LAZY — when you load a User from the database, JPA does NOT automatically load the entire Organization object with it.
    //It only loads it if you actually call user.getOrg()
    //@JoinColumn(name = "org_id") — tells JPA that the foreign key column in the users table is called org_id
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

    @Column(name="created_at", updatable = false)
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