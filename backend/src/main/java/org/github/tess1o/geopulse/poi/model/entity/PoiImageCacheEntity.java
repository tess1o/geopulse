package org.github.tess1o.geopulse.poi.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Image bytes from Wikimedia Commons, served to the browser through our own endpoint.
 *
 * <p>The licence columns are non-negotiable: Commons licences are per-file, so a row
 * without attribution must never exist, let alone be served (enforced by a table
 * constraint as well as in code).
 */
@Entity
@Table(name = "poi_image_cache")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PoiImageCacheEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@code '<file>@<width>'} so different renditions of the same file coexist. */
    @Column(name = "image_key", nullable = false, length = 400)
    private String imageKey;

    @Column(name = "file_name", nullable = false, length = 400)
    private String fileName;

    @Column(name = "remote_url", nullable = false)
    private String remoteUrl;

    @Column(name = "file_page_url")
    private String filePageUrl;

    @Column(name = "content_type", nullable = false, length = 80)
    private String contentType;

    @Column(name = "content_length", nullable = false)
    private Long contentLength;

    /**
     * Raw bytes. Deliberately NOT annotated {@code @Lob}: on PostgreSQL that makes
     * Hibernate treat the field as an OID large object and bind a bigint, which does not
     * match the {@code bytea} column. A plain {@code byte[]} maps to {@code bytea}.
     */
    @Column(name = "image_bytes", nullable = false)
    @ToString.Exclude
    private byte[] imageBytes;

    @Column(name = "author", length = 300)
    private String author;

    @Column(name = "license_name", nullable = false, length = 120)
    private String licenseName;

    @Column(name = "license_url")
    private String licenseUrl;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
