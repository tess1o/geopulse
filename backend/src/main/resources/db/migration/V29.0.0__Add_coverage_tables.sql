-- Coverage grid cells for lifetime exploration map
CREATE TABLE coverage_cells
(
    user_id    UUID                     NOT NULL,
    grid_m     INTEGER                  NOT NULL,
    cell_x     BIGINT                   NOT NULL,
    cell_y     BIGINT                   NOT NULL,
    first_seen TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_seen  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    seen_count BIGINT                   NOT NULL DEFAULT 1,
    CONSTRAINT pk_coverage_cells PRIMARY KEY (user_id, grid_m, cell_x, cell_y)
);

ALTER TABLE coverage_cells
    ADD CONSTRAINT fk_coverage_cells_user
        FOREIGN KEY (user_id) REFERENCES users (id);

CREATE INDEX idx_coverage_cells_user_grid
    ON coverage_cells (user_id, grid_m);

CREATE INDEX idx_coverage_cells_user_grid_xy
    ON coverage_cells (user_id, grid_m, cell_x, cell_y);

-- Per-user coverage processing state
CREATE TABLE coverage_state
(
    user_id        UUID                        NOT NULL,
    last_processed TIMESTAMP WITHOUT TIME ZONE,
    updated_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_coverage_state PRIMARY KEY (user_id)
);

ALTER TABLE coverage_state
    ADD CONSTRAINT fk_coverage_state_user
        FOREIGN KEY (user_id) REFERENCES users (id);
