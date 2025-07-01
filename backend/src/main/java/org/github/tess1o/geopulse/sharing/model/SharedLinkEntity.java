package org.github.tess1o.geopulse.sharing.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shared_link")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class SharedLinkEntity extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID id;

    private String name;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "password")
    private String password;

    @Column(name = "show_history")
    private boolean showHistory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserEntity user;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "view_count")
    @Builder.Default
    private Integer viewCount = 0;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (viewCount == null) {
            viewCount = 0;
        }
    }
}
