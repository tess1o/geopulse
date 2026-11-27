-- Both duplicate the primary key (user_id, grid_m, cell_x, cell_y), which serves the same lookups.
DROP INDEX IF EXISTS idx_coverage_cells_user_grid_xy;
DROP INDEX IF EXISTS idx_coverage_cells_user_grid;
