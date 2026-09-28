<template>
  <AppLayout variant="default">
    <PageContainer
      :title="t('technicalData.page.title')"
      :subtitle="t('technicalData.page.subtitle')"
      :loading="isLoading"
      variant="fullwidth"
    >
    <template #actions>
      <div class="header-actions">
        <Badge
          v-if="selectedRows.length > 0"
          :value="selectedRows.length"
          severity="info"
          class="selection-badge"
        >
          <span class="selection-text">{{ t('technicalData.page.actions.selected', { count: selectedRows.length }) }}</span>
        </Badge>

        <Button
          v-if="selectedRows.length > 0"
          :label="t('technicalData.page.actions.deleteSelected')"
          icon="pi pi-trash"
          severity="danger"
          size="small"
          @click="bulkDeleteGpsPoints"
          :disabled="selectedRows.length === 0"
          class="bulk-delete-button"
        />
        <Button
          :label="exportButtonLabel"
          icon="pi pi-download"
          @click="handleExportCSV"
          :loading="exportLoading"
          :disabled="!hasData || totalRecords === 0"
        />
        <Button
          v-if="hasData"
          :label="t('technicalData.page.actions.deleteAllData')"
          icon="pi pi-trash"
          severity="danger"
          outlined
          size="small"
          @click="showDeleteAllDialog = true"
          class="delete-all-button"
        />
      </div>
    </template>

    <PullToRefreshIndicator
      :enabled="isPullEnabled"
      :refreshing="isPullRefreshing"
      :distance="pullDistance"
      :state="pullState"
    >

    <!-- Summary Statistics -->
    <div class="stats-grid">
      <BaseCard variant="subtle" class="stat-card">
        <MetricItem
          icon="pi pi-database"
          icon-color="primary"
          :value="summaryStats.totalPoints"
          :label="t('technicalData.page.stats.totalPoints')"
          :formatter="formatNumber"
        />
      </BaseCard>

      <BaseCard variant="subtle" class="stat-card">
        <MetricItem
          icon="pi pi-calendar-plus"
          icon-color="secondary"
          :value="summaryStats.pointsToday"
          :label="t('technicalData.page.stats.pointsToday')"
          :formatter="formatNumber"
        />
      </BaseCard>

      <BaseCard variant="subtle" class="stat-card">
        <MetricItem
          icon="pi pi-play"
          icon-color="success"
          :value="summaryStats.firstPointDate"
          :label="t('technicalData.page.stats.firstPoint')"
          :formatter="formatDate"
        />
      </BaseCard>

      <BaseCard variant="subtle" class="stat-card">
        <MetricItem
          icon="pi pi-stop"
          icon-color="info"
          :value="summaryStats.lastPointDate"
          :label="t('technicalData.page.stats.lastPoint')"
          :formatter="timezone.timeAgo"
        />
      </BaseCard>
    </div>

    <!-- Date Filter -->
    <BaseCard class="filter-section">
      <div class="filter-header">
        <h3 class="filter-title">{{ t('technicalData.page.filters.title') }}</h3>
        <div class="filter-header-actions">
          <Button
            v-if="activeFilterCount > 0"
            :label="t('technicalData.page.filters.activeCount', { count: activeFilterCount })"
            severity="info"
            size="small"
            outlined
            :badge="activeFilterCount.toString()"
          />
          <Button
            v-if="hasActiveFilters"
            :label="t('technicalData.page.filters.clearAll')"
            severity="secondary"
            size="small"
            text
            @click="clearAllFilters"
          />
          <Button
            :icon="showAdvancedFilters ? 'pi pi-chevron-up' : 'pi pi-chevron-down'"
            :label="showAdvancedFilters ? t('technicalData.page.filters.hideAdvanced') : t('technicalData.page.filters.showAdvanced')"
            text
            size="small"
            @click="showAdvancedFilters = !showAdvancedFilters"
          />
        </div>
      </div>

      <!-- Quick Date Presets -->
      <div class="quick-presets">
        <Button :label="t('technicalData.page.filters.today')" size="small" outlined @click="setToday" />
        <Button :label="t('technicalData.page.filters.yesterday')" size="small" outlined @click="setYesterday" />
        <Button :label="t('technicalData.page.filters.last7Days')" size="small" outlined @click="setLast7Days" />
        <Button :label="t('technicalData.page.filters.last30Days')" size="small" outlined @click="setLast30Days" />
      </div>

      <div class="filter-controls">
        <div class="date-time-filter-group">
          <div class="date-time-field">
            <label class="filter-label" for="gps-start-date-time-input">{{ t('technicalData.page.filters.from') }}</label>
            <DatePicker
              input-id="gps-start-date-time-input"
              v-model="startDateTime"
              show-time
              hour-format="24"
              show-icon
              icon-display="input"
              :date-format="timezone.getPrimeVueDatePickerFormat()"
              :placeholder="t('technicalData.page.filters.startPlaceholder')"
              class="date-picker date-time-picker"
            />
          </div>
          <div class="date-time-field">
            <label class="filter-label" for="gps-end-date-time-input">{{ t('technicalData.page.filters.to') }}</label>
            <DatePicker
              input-id="gps-end-date-time-input"
              v-model="endDateTime"
              show-time
              hour-format="24"
              show-icon
              icon-display="input"
              :date-format="timezone.getPrimeVueDatePickerFormat()"
              :placeholder="t('technicalData.page.filters.endPlaceholder')"
              class="date-picker date-time-picker"
            />
          </div>
        </div>
        <Button
          :label="t('technicalData.page.filters.apply')"
          icon="pi pi-check"
          size="small"
          :disabled="!canApplyDateFilter"
          @click="applyDateFilter"
          class="date-filter-apply-button"
        />
        <Button
          v-if="hasDateFilter || hasDateFilterDraft"
          :label="t('technicalData.page.filters.clear')"
          severity="secondary"
          size="small"
          text
          icon="pi pi-times"
          @click="clearDateFilter"
        />
      </div>
      <div v-if="dateFilterValidationMessage" class="filter-validation-message">
        <i class="pi pi-exclamation-triangle"></i>
        <span>{{ dateFilterValidationMessage }}</span>
      </div>

      <!-- Advanced Filters -->
      <div v-if="showAdvancedFilters" class="advanced-filters">
        <div class="filter-row">
          <div class="filter-field">
            <label class="filter-label">{{ t('technicalData.page.filters.sourceTypesLabel') }}</label>
            <MultiSelect
              v-model="filters.sourceTypes"
              :options="sourceTypeOptions"
              option-label="label"
              option-value="value"
              :placeholder="t('technicalData.page.filters.allSources')"
              display="chip"
              class="filter-input"
              :showToggleAll="false"
            />
          </div>
        </div>

        <div class="filter-row">
          <div class="filter-field">
            <label class="filter-label">{{ t('technicalData.page.filters.accuracyLabel') }}</label>
            <div class="range-inputs">
              <InputNumber
                v-model="filters.accuracyMin"
                :placeholder="t('technicalData.page.filters.min')"
                :min="0"
                :max="10000"
                class="range-input"
              />
              <span class="range-separator">{{ t('technicalData.page.filters.rangeTo') }}</span>
              <InputNumber
                v-model="filters.accuracyMax"
                :placeholder="t('technicalData.page.filters.max')"
                :min="0"
                :max="10000"
                class="range-input"
              />
            </div>
          </div>

          <div class="filter-field">
            <label class="filter-label">{{ t('technicalData.page.filters.speedLabel') }}</label>
            <div class="range-inputs">
              <InputNumber
                v-model="filters.speedMin"
                :placeholder="t('technicalData.page.filters.min')"
                :min="0"
                :max="500"
                class="range-input"
              />
              <span class="range-separator">{{ t('technicalData.page.filters.rangeTo') }}</span>
              <InputNumber
                v-model="filters.speedMax"
                :placeholder="t('technicalData.page.filters.max')"
                :min="0"
                :max="500"
                class="range-input"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- Active Filter Chips -->
      <div v-if="hasActiveFilters" class="active-filter-chips">
        <Chip
          v-if="hasDateFilter"
          :label="t('technicalData.page.filters.dateChip', { range: formatDateRange(appliedStartDateTime, appliedEndDateTime) })"
          removable
          @remove="clearDateFilter"
        />
        <Chip
          v-if="filters.accuracyMin !== null || filters.accuracyMax !== null"
          :label="t('technicalData.page.filters.accuracyChip', { min: filters.accuracyMin || 0, max: filters.accuracyMax || '∞' })"
          removable
          @remove="filters.accuracyMin = null; filters.accuracyMax = null"
        />
        <Chip
          v-if="filters.speedMin !== null || filters.speedMax !== null"
          :label="t('technicalData.page.filters.speedChip', { min: filters.speedMin || 0, max: filters.speedMax || '∞' })"
          removable
          @remove="filters.speedMin = null; filters.speedMax = null"
        />
        <Chip
          v-if="filters.sourceTypes && filters.sourceTypes.length > 0"
          :label="t('technicalData.page.filters.sourcesChip', { count: filters.sourceTypes.length })"
          removable
          @remove="filters.sourceTypes = []"
        />
      </div>
    </BaseCard>

    <BaseCard class="filter-section telemetry-mapping-section">
      <div class="filter-header">
        <h3 class="filter-title">{{ t('technicalData.page.telemetry.title') }}</h3>
        <div class="filter-header-actions">
          <small class="text-muted">{{ t('technicalData.page.telemetry.hint') }}</small>
          <Button
            :icon="showTelemetryMappingAdvanced ? 'pi pi-chevron-up' : 'pi pi-chevron-down'"
            :label="showTelemetryMappingAdvanced ? t('technicalData.page.telemetry.hideMapping') : t('technicalData.page.telemetry.showMapping')"
            text
            size="small"
            @click="showTelemetryMappingAdvanced = !showTelemetryMappingAdvanced"
          />
        </div>
      </div>

      <div v-if="showTelemetryMappingAdvanced" class="advanced-filters telemetry-mapping-content">
        <div class="telemetry-mapping-controls">
          <div class="telemetry-mapping-select">
            <label class="filter-label" for="telemetry-source-type">{{ t('technicalData.page.telemetry.sourceTypeLabel') }}</label>
            <Select
              id="telemetry-source-type"
              v-model="selectedTelemetrySourceType"
              :options="telemetrySourceTypeOptions"
              optionLabel="label"
              optionValue="value"
              class="telemetry-source-select"
            />
          </div>

          <div class="telemetry-mapping-actions">
            <Button
              :label="t('technicalData.page.telemetry.resetToDefaults')"
              icon="pi pi-refresh"
              text
              :loading="telemetryResetting"
              @click="resetTelemetryMapping"
            />
            <Button
              :label="t('technicalData.page.telemetry.addRow')"
              icon="pi pi-plus"
              outlined
              @click="addTelemetryMappingRow"
            />
            <Button
              :label="t('technicalData.page.telemetry.saveMapping')"
              icon="pi pi-save"
              :loading="telemetrySaving"
              @click="saveTelemetryMapping"
            />
          </div>
        </div>

        <div v-if="telemetryLoading" class="telemetry-loading">
          {{ t('technicalData.page.telemetry.loading') }}
        </div>

        <div v-else class="telemetry-table-wrapper">
          <table class="telemetry-table">
            <thead>
            <tr>
              <th>{{ t('technicalData.page.telemetry.columns.key') }}</th>
              <th>{{ t('technicalData.page.telemetry.columns.label') }}</th>
              <th>{{ t('technicalData.page.telemetry.columns.type') }}</th>
              <th>{{ t('technicalData.page.telemetry.columns.unit') }}</th>
              <th>{{ t('technicalData.page.telemetry.columns.gpsData') }}</th>
              <th>{{ t('technicalData.page.telemetry.columns.currentPopup') }}</th>
              <th>{{ t('technicalData.page.telemetry.columns.order') }}</th>
              <th></th>
            </tr>
            </thead>
            <tbody>
            <tr v-if="telemetryMappingRows.length === 0">
              <td colspan="8" class="telemetry-empty">{{ t('technicalData.page.telemetry.empty') }}</td>
            </tr>
            <tr v-for="(entry, index) in telemetryMappingRows" :key="`${entry.key || 'new'}-${index}`">
              <td>
                <InputText v-model="entry.key" :placeholder="t('technicalData.page.telemetry.keyPlaceholder')" class="telemetry-input" />
              </td>
              <td>
                <InputText v-model="entry.label" :placeholder="t('technicalData.page.telemetry.labelPlaceholder')" class="telemetry-input" />
              </td>
              <td>
                <Select
                  v-model="entry.type"
                  :options="telemetryTypeOptions"
                  optionLabel="label"
                  optionValue="value"
                  class="telemetry-input"
                />
              </td>
              <td>
                <InputText v-model="entry.unit" :placeholder="t('technicalData.page.telemetry.unitPlaceholder')" class="telemetry-input telemetry-unit" />
              </td>
              <td class="telemetry-check-cell">
                <Checkbox v-model="entry.showInGpsData" binary />
              </td>
              <td class="telemetry-check-cell">
                <Checkbox v-model="entry.showInCurrentPopup" binary />
              </td>
              <td>
                <InputNumber v-model="entry.order" :min="0" :step="10" class="telemetry-input telemetry-order" />
              </td>
              <td class="telemetry-remove-cell">
                <Button
                  icon="pi pi-trash"
                  severity="danger"
                  text
                  size="small"
                  @click="removeTelemetryMappingRow(index)"
                />
              </td>
            </tr>
            </tbody>
          </table>
        </div>

        <div class="telemetry-boolean-grid" v-if="telemetryBooleanRows.length > 0">
          <div
            v-for="(entry, index) in telemetryBooleanRows"
            :key="`bool-${entry.key || index}`"
            class="telemetry-boolean-row"
          >
            <div class="telemetry-boolean-title">{{ entry.label || entry.key || t('technicalData.page.telemetry.booleanDefaultLabel', { index: index + 1 }) }}</div>
            <div class="telemetry-boolean-fields">
              <InputText
                v-model="entry.trueValues"
                :placeholder="t('technicalData.page.telemetry.trueValuesPlaceholder')"
                class="telemetry-input"
              />
              <InputText
                v-model="entry.falseValues"
                :placeholder="t('technicalData.page.telemetry.falseValuesPlaceholder')"
                class="telemetry-input"
              />
            </div>
          </div>
        </div>
      </div>
    </BaseCard>

    <!-- Filtered Results Banner -->
    <BaseCard v-if="hasActiveFilters && filteredRecordsText" class="filtered-banner">
      <div class="filtered-banner-content">
        <i class="pi pi-filter"></i>
        <span class="filtered-text">{{ filteredRecordsText }}</span>
      </div>
    </BaseCard>

    <!-- GPS Points Table -->
    <BaseCard class="table-section">
      <DataTable
        v-if="!isMobile"
        :value="gpsPointsWithDelta"
        :loading="tableLoading"
        paginator
        :rows="pageSize"
        :total-records="totalRecords"
        :first="currentPage * pageSize"
        lazy
        @page="onPageChange"
        @sort="onSort"
        @row-click="handleRowClick"
        v-model:sort-field="sortField"
        v-model:sort-order="sortOrder"
        data-key="id"
        responsive-layout="scroll"
        class="gps-data-table"
        v-model:selection="selectedRows"
        selection-mode="multiple"
        :pt="{
          root: 'bg-surface-0 dark:bg-surface-950',
          header: 'bg-surface-50 dark:bg-surface-900 border-surface-200 dark:border-surface-700',
          tbody: 'bg-surface-0 dark:bg-surface-950',
          row: 'bg-surface-0 dark:bg-surface-950 hover:bg-surface-50 dark:hover:bg-surface-800',
          cell: 'text-surface-900 dark:text-surface-100 border-surface-200 dark:border-surface-700',
          paginator: 'bg-surface-50 dark:bg-surface-900 border-surface-200 dark:border-surface-700'
        }"
      >
        <template #paginatorstart>
          <span class="paginator-info">{{ t('technicalData.page.table.pageOf', { current: currentPage + 1, total: totalPages.toLocaleString() }) }}</span>
        </template>
        <template #paginatorend>
          <span class="paginator-info">{{ t('technicalData.page.table.totalCount', { count: totalRecords.toLocaleString() }) }}</span>
        </template>
        <template #header>
          <div class="table-header">
            <div class="table-header-left">
              <span class="table-title">{{ t('technicalData.page.table.title') }}</span>
              <span class="table-subtitle">
                <template v-if="totalRecords > 0">
                  {{ t('technicalData.page.table.showing', { start: currentPage * pageSize + 1, end: Math.min((currentPage + 1) * pageSize, totalRecords), total: totalRecords.toLocaleString() }) }}
                  <span v-if="hasActiveFilters && summaryStats.filteredPoints" class="filtered-info">
                    ({{ t('technicalData.page.table.filteredFrom', { total: summaryStats.totalPoints.toLocaleString() }) }})
                  </span>
                </template>
                <template v-else>
                  {{ t('technicalData.page.table.noPointsFound') }}
                </template>
              </span>
            </div>
            <div class="table-header-right">
              <label class="page-size-label">{{ t('technicalData.page.table.rowsPerPage') }}</label>
              <Dropdown
                v-model="pageSize"
                :options="pageSizeOptions"
                class="page-size-dropdown"
              />
            </div>
          </div>
        </template>

        <template #empty>
          <div class="empty-state">
            <i class="pi pi-map-marker empty-icon"></i>
            <h3>{{ t('technicalData.page.table.emptyTitle') }}</h3>
            <p>{{ t('technicalData.page.table.emptyDescription') }}</p>
          </div>
        </template>

        <!-- Selection Column -->
        <Column selectionMode="multiple" headerStyle="width: 3rem" class="selection-col">
          <template #body="slotProps">
            <Checkbox
              :model-value="isRowSelected(slotProps.data)"
              @change="handleCheckboxChange($event, slotProps.data)"
              binary
            />
          </template>
        </Column>

        <Column field="timestamp" :header="t('technicalData.page.table.columns.date')" sortable class="timestamp-col">
          <template #body="slotProps">
            <div class="timestamp-cell">
              <span class="timestamp-date">{{ formatTimestamp(slotProps.data.timestamp).date }}</span>
              <span class="timestamp-time">{{ formatTimestamp(slotProps.data.timestamp).time }}</span>
            </div>
          </template>
        </Column>

        <Column :header="t('technicalData.page.table.columns.delta')" class="delta-col" v-if="!isMobile">
          <template #body="slotProps">
            <span v-if="slotProps.data.timeDeltaDisplay">{{ slotProps.data.timeDeltaDisplay }}</span>
            <span v-else class="null-value">-</span>
          </template>
        </Column>

        <Column :header="t('technicalData.page.table.columns.location')" class="coordinates-col">
          <template #body="slotProps">
            <div class="coordinates-cell">
              <span class="coordinate-line">{{ slotProps.data.coordinates.lat.toFixed(6) }}</span>
              <span class="coordinate-separator">,</span>
              <span class="coordinate-line">{{ slotProps.data.coordinates.lng.toFixed(6) }}</span>
            </div>
          </template>
        </Column>

        <Column field="velocity" sortable :header="t('technicalData.page.table.columns.speed')" class="numeric-col speed-col">
          <template #body="slotProps">
            <span v-if="slotProps.data.velocity !== null && slotProps.data.velocity >= 0">{{ formatSpeed(slotProps.data.velocity.toFixed(1)) }}</span>
            <span v-else class="null-value">-</span>
          </template>
        </Column>

        <Column field="accuracy" sortable :header="t('technicalData.page.table.columns.accuracy')" class="numeric-col accuracy-col" v-if="!isMobile">
          <template #body="slotProps">
            <span v-if="slotProps.data.accuracy">{{ formatDistance(slotProps.data.accuracy.toFixed(1)) }}</span>
            <span v-else class="null-value">-</span>
          </template>
        </Column>

        <Column field="altitude" :header="t('technicalData.page.table.columns.altitude')" class="numeric-col altitude-col" v-if="!isMobile && !isTablet">
          <template #body="slotProps">
            <span v-if="slotProps.data.altitude">{{ formatDistance(Math.round(slotProps.data.altitude)) }}</span>
            <span v-else class="null-value">-</span>
          </template>
        </Column>

        <Column field="battery" :header="t('technicalData.page.table.columns.battery')" sortable class="numeric-col battery-col" v-if="!isMobile && !isTablet">
          <template #body="slotProps">
            <span v-if="slotProps.data.battery !== null && slotProps.data.battery >= 0">{{ Math.round(slotProps.data.battery) }}%</span>
            <span v-else class="null-value">-</span>
          </template>
        </Column>

        <Column :header="t('technicalData.page.table.columns.telemetry')" class="telemetry-col" v-if="!isMobile">
          <template #body="slotProps">
            <div
              v-if="slotProps.data.telemetryGpsData && slotProps.data.telemetryGpsData.length > 0"
              class="telemetry-cell"
            >
              <div
                v-for="item in slotProps.data.telemetryGpsData"
                :key="`${slotProps.data.id}-${item.key}`"
                class="telemetry-item"
              >
                <span class="telemetry-label">{{ item.label }}:</span>
                <span class="telemetry-value">{{ formatTelemetryValue(item) }}</span>
              </div>
            </div>
            <span v-else class="null-value">-</span>
          </template>
        </Column>

        <Column field="sourceType" :header="t('technicalData.page.table.columns.source')" class="source-col" v-if="!isMobile">
          <template #body="slotProps">
            <Tag
              :value="slotProps.data.sourceType || t('common.unknown')"
              :severity="getSourceSeverity(slotProps.data.sourceType)"
              class="source-tag"
            />
          </template>
        </Column>

        <Column :header="t('technicalData.page.table.columns.actions')" class="actions-col">
          <template #body="slotProps">
            <div class="actions-buttons">
              <Button
                icon="pi pi-pencil"
                severity="secondary"
                size="small"
                text
                @click="editGpsPoint(slotProps.data)"
                v-tooltip.top="t('technicalData.page.table.editTooltip')"
                class="action-button edit-button"
              />
              <Button
                icon="pi pi-trash"
                severity="danger"
                size="small"
                text
                @click="deleteGpsPoint(slotProps.data)"
                v-tooltip.top="t('technicalData.page.table.deleteTooltip')"
                class="action-button delete-button"
              />
            </div>
          </template>
        </Column>
      </DataTable>

      <div v-else class="mobile-gps-list-panel">
        <div class="table-header mobile-gps-header">
          <div class="table-header-left">
            <span class="table-title">{{ t('technicalData.page.table.title') }}</span>
            <span class="table-subtitle">
              <template v-if="totalRecords > 0">
                {{ t('technicalData.page.table.showing', { start: currentPage * pageSize + 1, end: Math.min((currentPage + 1) * pageSize, totalRecords), total: totalRecords.toLocaleString() }) }}
                <span v-if="hasActiveFilters && summaryStats.filteredPoints" class="filtered-info">
                  ({{ t('technicalData.page.table.filteredFrom', { total: summaryStats.totalPoints.toLocaleString() }) }})
                </span>
              </template>
              <template v-else>
                {{ t('technicalData.page.table.noPointsFound') }}
              </template>
            </span>
          </div>
          <div class="table-header-right">
            <label class="page-size-label">{{ t('technicalData.page.table.rowsShort') }}</label>
            <Dropdown
              v-model="pageSize"
              :options="pageSizeOptions"
              class="page-size-dropdown mobile-page-size-dropdown"
            />
          </div>
        </div>

        <div class="mobile-gps-toolbar">
          <label class="mobile-select-page-control">
            <Checkbox
              :model-value="allVisibleGpsRowsSelected"
              :disabled="gpsPointsWithDelta.length === 0"
              binary
              @change="toggleVisibleGpsRowsSelection"
              :aria-label="t('technicalData.page.mobile.selectAllAriaLabel')"
            />
            <span>{{ allVisibleGpsRowsSelected ? t('technicalData.page.mobile.clearPage') : t('technicalData.page.mobile.selectPage') }}</span>
          </label>

          <div class="mobile-sort-control">
            <label class="mobile-sort-label" for="mobile-gps-sort">{{ t('technicalData.page.mobile.sort') }}</label>
            <Dropdown
              input-id="mobile-gps-sort"
              v-model="sortField"
              :options="mobileSortOptions"
              option-label="label"
              option-value="value"
              class="mobile-sort-dropdown"
              @change="onMobileSortFieldChange"
            />
            <Button
              :icon="sortOrder === 1 ? 'pi pi-sort-amount-up-alt' : 'pi pi-sort-amount-down'"
              severity="secondary"
              outlined
              size="small"
              class="mobile-sort-direction-button"
              @click="toggleMobileSortDirection"
              v-tooltip.top="sortOrder === 1 ? t('technicalData.page.mobile.ascending') : t('technicalData.page.mobile.descending')"
              :aria-label="sortOrder === 1 ? t('technicalData.page.mobile.sortAscendingAriaLabel') : t('technicalData.page.mobile.sortDescendingAriaLabel')"
            />
          </div>
        </div>

        <div v-if="tableLoading" class="mobile-gps-loading">
          <i class="pi pi-spin pi-spinner"></i>
          <span>{{ t('technicalData.page.mobile.loading') }}</span>
        </div>

        <template v-else-if="gpsPointsWithDelta.length > 0">
          <div class="mobile-gps-list" role="list">
            <div
              v-for="point in gpsPointsWithDelta"
              :key="point.id"
              class="mobile-gps-row"
              role="listitem"
            >
              <Checkbox
                :model-value="isRowSelected(point)"
                @change="handleCheckboxChange($event, point)"
                binary
                class="mobile-gps-checkbox"
                :aria-label="t('technicalData.page.mobile.selectPointAriaLabel', { date: formatTimestamp(point.timestamp).date, time: formatTimestamp(point.timestamp).time })"
              />
              <div class="mobile-gps-main">
                <div class="mobile-gps-primary">
                  <span class="mobile-gps-date">{{ formatTimestamp(point.timestamp).date }}</span>
                  <span class="mobile-gps-time">{{ formatTimestamp(point.timestamp).time }}</span>
                </div>
                <div class="mobile-gps-coordinates">
                  {{ point.coordinates.lat.toFixed(6) }}, {{ point.coordinates.lng.toFixed(6) }}
                </div>
                <div class="mobile-gps-meta">
                  <span class="mobile-gps-metric">
                    <i class="pi pi-gauge"></i>
                    <template v-if="point.velocity !== null && point.velocity >= 0">
                      {{ formatSpeed(point.velocity.toFixed(1)) }}
                    </template>
                    <template v-else>-</template>
                  </span>
                  <span class="mobile-gps-metric">
                    <i class="pi pi-bullseye"></i>
                    <template v-if="point.accuracy">
                      {{ formatDistance(point.accuracy.toFixed(1)) }}
                    </template>
                    <template v-else>-</template>
                  </span>
                  <span class="mobile-gps-metric">
                    <i class="pi pi-bolt"></i>
                    <template v-if="point.battery !== null && point.battery >= 0">
                      {{ Math.round(point.battery) }}%
                    </template>
                    <template v-else>-</template>
                  </span>
                </div>
              </div>
              <Button
                icon="pi pi-ellipsis-v"
                severity="secondary"
                text
                rounded
                size="small"
                class="mobile-gps-actions-button"
                @click="openMobileActionMenu($event, point)"
                v-tooltip.left="t('technicalData.page.mobile.actionsTooltip')"
                aria-haspopup="true"
                aria-controls="mobile-gps-action-menu"
                :aria-label="t('technicalData.page.mobile.actionsAriaLabel', { date: formatTimestamp(point.timestamp).date, time: formatTimestamp(point.timestamp).time })"
              />
            </div>
          </div>

          <Paginator
            v-if="totalRecords > pageSize"
            :first="currentPage * pageSize"
            :rows="pageSize"
            :total-records="totalRecords"
            class="mobile-gps-paginator"
            @page="onMobilePageChange"
          >
            <template #start>
              <span class="paginator-info">{{ t('technicalData.page.table.pageOf', { current: currentPage + 1, total: totalPages.toLocaleString() }) }}</span>
            </template>
            <template #end>
              <span class="paginator-info">{{ t('technicalData.page.table.totalCount', { count: totalRecords.toLocaleString() }) }}</span>
            </template>
          </Paginator>
        </template>

        <div v-else class="empty-state mobile-empty-state">
          <i class="pi pi-map-marker empty-icon"></i>
          <h3>{{ t('technicalData.page.table.emptyTitle') }}</h3>
          <p>{{ t('technicalData.page.table.emptyDescription') }}</p>
        </div>

        <Menu
          ref="mobileActionMenu"
          id="mobile-gps-action-menu"
          :model="mobileActionMenuItems"
          popup
        />
      </div>
    </BaseCard>
    
    <!-- Delete Confirmation Dialog -->
    <Dialog
      v-model:visible="showDeleteDialog"
      :header="t('technicalData.page.dialogs.delete.header')"
      :modal="true"
      :style="{ width: '25rem' }"
    >
      <div class="confirm-dialog-content">
        <i class="pi pi-exclamation-triangle confirm-icon"></i>
        <span>{{ t('technicalData.page.dialogs.delete.message') }}</span>
      </div>
      <template #footer>
        <Button
          :label="t('common.cancel')"
          severity="secondary"
          @click="showDeleteDialog = false"
          autofocus
          :disabled="deleteLoading"
        />
        <Button
          :label="t('technicalData.page.dialogs.delete.confirm')"
          severity="danger"
          @click="confirmDeleteGpsPoint"
          :loading="deleteLoading"
        />
      </template>
    </Dialog>

    <!-- Bulk Delete Confirmation Dialog -->
    <Dialog
      v-model:visible="showBulkDeleteDialog"
      :header="t('technicalData.page.dialogs.bulkDelete.header')"
      :modal="true"
      :style="{ width: '30rem' }"
    >
      <div class="confirm-dialog-content">
        <i class="pi pi-exclamation-triangle confirm-icon"></i>
        <span>
          {{ t('technicalData.page.dialogs.bulkDelete.message', { count: selectedRows.length }, selectedRows.length) }}
        </span>
      </div>
      <template #footer>
        <Button
          :label="t('common.cancel')"
          severity="secondary"
          @click="showBulkDeleteDialog = false"
          autofocus
          :disabled="bulkDeleteLoading"
        />
        <Button
          :label="t('technicalData.page.dialogs.bulkDelete.confirm')"
          severity="danger"
          @click="confirmBulkDelete"
          :loading="bulkDeleteLoading"
        />
      </template>
    </Dialog>

    <!-- Delete All GPS Data Confirmation Dialog -->
    <Dialog
      v-model:visible="showDeleteAllDialog"
      :header="t('technicalData.page.dialogs.deleteAll.header')"
      :modal="true"
      :style="{ width: '32rem' }"
    >
      <div class="confirm-dialog-content">
        <i class="pi pi-exclamation-triangle confirm-icon confirm-icon--large"></i>
        <div>
          <p class="delete-all-warning-title">{{ t('technicalData.page.dialogs.deleteAll.warningTitle') }}</p>
          <ul class="delete-all-warning-list">
            <li>
              <i18n-t keypath="technicalData.page.dialogs.deleteAll.allPoints" tag="span">
                <template #count><strong>{{ summaryStats.totalPoints.toLocaleString() }}</strong></template>
              </i18n-t>
            </li>
            <li>{{ t('technicalData.page.dialogs.deleteAll.timelineData') }}</li>
          </ul>
          <p class="delete-all-warning-note">
            <i18n-t keypath="technicalData.page.dialogs.deleteAll.note" tag="span">
              <template #not><strong>{{ t('technicalData.page.dialogs.deleteAll.notWord') }}</strong></template>
            </i18n-t>
          </p>
        </div>
      </div>
      <template #footer>
        <Button
          :label="t('common.cancel')"
          severity="secondary"
          @click="showDeleteAllDialog = false"
          autofocus
          :disabled="deleteAllLoading"
        />
        <Button
          :label="t('technicalData.page.dialogs.deleteAll.confirm')"
          severity="danger"
          @click="confirmDeleteAll"
          :loading="deleteAllLoading"
        />
      </template>
    </Dialog>

    <!-- Edit GPS Point Dialog -->
    <GpsPointEditDialog 
      :visible="showEditDialog"
      :gps-point="selectedGpsPoint"
      @close="showEditDialog = false"
      @save="handleEditSave"
    />
    </PullToRefreshIndicator>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { useI18n } from 'vue-i18n'
import { useTechnicalDataStore } from '@/stores/technicalData'
import { useGpsSourcesStore } from '@/stores/gpsSources'
import { useTimezone } from '@/composables/useTimezone'
import { usePullToRefresh } from '@/composables/usePullToRefresh'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const timezone = useTimezone()
const { t } = useI18n()

// Components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import MetricItem from '@/components/ui/data/MetricItem.vue'
import PullToRefreshIndicator from '@/components/ui/PullToRefreshIndicator.vue'
import GpsPointEditDialog from '@/components/dialogs/GpsPointEditDialog.vue'

// PrimeVue
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import DatePicker from 'primevue/datepicker'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import MultiSelect from 'primevue/multiselect'
import InputNumber from 'primevue/inputnumber'
import Chip from 'primevue/chip'
import Badge from 'primevue/badge'
import Dropdown from 'primevue/dropdown'
import Checkbox from 'primevue/checkbox'
import Menu from 'primevue/menu'
import Paginator from 'primevue/paginator'
import {formatDistance, formatSpeed} from "../../utils/calculationsHelpers";

// Store and utils
const technicalDataStore = useTechnicalDataStore()
const gpsSourcesStore = useGpsSourcesStore()
const toast = useToast()
const route = useRoute()
const router = useRouter()

// Reactive state
const isMobile = ref(false)
const isTablet = ref(false)
const startDateTime = ref(null)
const endDateTime = ref(null)
const appliedStartDateTime = ref(null)
const appliedEndDateTime = ref(null)
const pageSize = ref(50)
const currentPage = ref(0)
const sortField = ref('timestamp')
const sortOrder = ref(-1) // -1 for descending, 1 for ascending

// Advanced filters
const showAdvancedFilters = ref(false)
const showTelemetryMappingAdvanced = ref(false)
const filters = ref({
  sourceTypes: [],
  accuracyMin: null,
  accuracyMax: null,
  speedMin: null,
  speedMax: null
})

// Source type options for multi-select
// Brand/format names (OwnTracks, GPSLogger, Overland, Traccar, Google Timeline, GPX, Dawarich,
// Home Assistant, Colota, GeoJSON, CSV) are never translated - see locales/en/locationSources.js.
// "Manual Reconstruction" is a real label, so the whole array is a computed to stay reactive to it.
const sourceTypeOptions = computed(() => [
  { label: 'OwnTracks', value: 'OWNTRACKS' },
  { label: 'GPSLogger', value: 'GPSLOGGER' },
  { label: 'Overland', value: 'OVERLAND' },
  { label: 'Traccar', value: 'TRACCAR' },
  { label: 'Google Timeline', value: 'GOOGLE_TIMELINE' },
  { label: 'GPX', value: 'GPX' },
  { label: 'Dawarich', value: 'DAWARICH' },
  { label: 'Home Assistant', value: 'HOME_ASSISTANT' },
  { label: 'Colota', value: 'COLOTA' },
  { label: 'GeoJSON', value: 'GEOJSON' },
  { label: 'CSV', value: 'CSV' },
  { label: t('technicalData.page.filters.sourceOptions.manualReconstruction'), value: 'MANUAL' }
])

// Page size options
const pageSizeOptions = ref([25, 50, 100, 200, 500])
const mobileSortOptions = computed(() => [
  { label: t('technicalData.page.table.columns.date'), value: 'timestamp' },
  { label: t('technicalData.page.table.columns.speed'), value: 'velocity' }
])

// Loading states
const isLoading = ref(false)
const tableLoading = ref(false)
const exportLoading = ref(false)

// Edit/Delete states
const showEditDialog = ref(false)
const showDeleteDialog = ref(false)
const selectedGpsPoint = ref(null)

// Bulk delete states
const selectedRows = ref([])
const showBulkDeleteDialog = ref(false)
const bulkDeleteLoading = ref(false)
const deleteLoading = ref(false)
const mobileActionMenu = ref()
const mobileActionPoint = ref(null)

// Delete all states
const showDeleteAllDialog = ref(false)
const deleteAllLoading = ref(false)

// Shift-click selection tracking
const lastSelectedIndex = ref(null)
const telemetryLoading = ref(false)
const telemetrySaving = ref(false)
const telemetryResetting = ref(false)
const selectedTelemetrySourceType = ref('OWNTRACKS')
const telemetryMappingRows = ref([])

// Brand names (OwnTracks, GPSLogger) are never translated.
const telemetrySourceTypeOptions = [
  { label: 'OwnTracks', value: 'OWNTRACKS' },
  { label: 'GPSLogger', value: 'GPSLOGGER' }
]

const telemetryTypeOptions = computed(() => [
  { label: t('technicalData.page.telemetry.typeOptions.boolean'), value: 'boolean' },
  { label: t('technicalData.page.telemetry.typeOptions.number'), value: 'number' },
  { label: t('technicalData.page.telemetry.typeOptions.string'), value: 'string' }
])

// Computed properties
const summaryStats = computed(() => technicalDataStore.summaryStats)
const gpsPoints = computed(() => technicalDataStore.gpsPoints)
const totalRecords = computed(() => technicalDataStore.totalRecords)
const gpsPointsWithDelta = computed(() =>
  gpsPoints.value.map((point, index, points) => ({
    ...point,
    timeDeltaDisplay: formatTimeDelta(point.timestamp, points[index - 1]?.timestamp)
  }))
)

const hasData = computed(() => summaryStats.value.totalPoints > 0)
const isValidDateValue = (date) => date instanceof Date && Number.isFinite(date.getTime())
const hasDateFilterDraft = computed(() => Boolean(startDateTime.value || endDateTime.value))
const hasCompleteDateFilterDraft = computed(() =>
  isValidDateValue(startDateTime.value) && isValidDateValue(endDateTime.value)
)
const dateFilterValidationMessage = computed(() => {
  if (!hasCompleteDateFilterDraft.value) return ''
  if (startDateTime.value.getTime() > endDateTime.value.getTime()) {
    return t('technicalData.page.filters.fromBeforeTo')
  }
  return ''
})
const canApplyDateFilter = computed(() =>
  hasCompleteDateFilterDraft.value && !dateFilterValidationMessage.value
)
const hasDateFilter = computed(() =>
  isValidDateValue(appliedStartDateTime.value) && isValidDateValue(appliedEndDateTime.value)
)

const hasActiveFilters = computed(() => {
  return hasDateFilter.value ||
         (filters.value.accuracyMin !== null && filters.value.accuracyMin !== undefined) ||
         (filters.value.accuracyMax !== null && filters.value.accuracyMax !== undefined) ||
         (filters.value.speedMin !== null && filters.value.speedMin !== undefined) ||
         (filters.value.speedMax !== null && filters.value.speedMax !== undefined) ||
         (filters.value.sourceTypes && filters.value.sourceTypes.length > 0)
})

const activeFilterCount = computed(() => {
  let count = 0
  if (hasDateFilter.value) count++
  if (filters.value.accuracyMin !== null || filters.value.accuracyMax !== null) count++
  if (filters.value.speedMin !== null || filters.value.speedMax !== null) count++
  if (filters.value.sourceTypes && filters.value.sourceTypes.length > 0) count++
  return count
})

const filteredRecordsText = computed(() => {
  if (!hasActiveFilters.value) return ''

  const filtered = totalRecords.value
  const unfilteredTotal = summaryStats.value.totalPoints

  if (filtered === 0) {
    return t('technicalData.page.filters.noPointsForPeriod')
  }

  // Show comparison: filtered count vs unfiltered total
  if (filtered < unfilteredTotal) {
    return t('technicalData.page.filters.showingOfTotal', {
      filtered: filtered.toLocaleString(),
      total: unfilteredTotal.toLocaleString()
    })
  }

  return t('technicalData.page.filters.showingCount', { count: filtered.toLocaleString() })
})

const exportButtonLabel = computed(() => {
  // Priority 1: Manual selection (regardless of filters)
  if (selectedRows.value.length > 0) {
    return t('technicalData.page.actions.exportCsvSelected', { count: selectedRows.value.length })
  }

  // Priority 2: Filters applied, no manual selection
  if (hasActiveFilters.value) {
    if (totalRecords.value === 0) {
      return t('technicalData.page.actions.exportCsvZero')
    }
    return t('technicalData.page.actions.exportCsvFiltered', { count: totalRecords.value.toLocaleString() })
  }

  // Priority 3: No filters and no manual selection
  return t('technicalData.page.actions.exportCsv')
})

const totalPages = computed(() => {
  if (!totalRecords.value || !pageSize.value) return 0
  return Math.ceil(totalRecords.value / pageSize.value)
})

const allVisibleGpsRowsSelected = computed(() => {
  if (gpsPointsWithDelta.value.length === 0) return false
  return gpsPointsWithDelta.value.every(row => isRowSelected(row))
})

const telemetryBooleanRows = computed(() =>
  telemetryMappingRows.value.filter(entry => entry.type === 'boolean')
)

const mobileActionMenuItems = computed(() => [
  {
    label: t('technicalData.page.table.editTooltip'),
    icon: 'pi pi-pencil',
    command: () => {
      if (mobileActionPoint.value) {
        editGpsPoint(mobileActionPoint.value)
      }
    }
  },
  {
    label: t('technicalData.page.table.deleteTooltip'),
    icon: 'pi pi-trash',
    command: () => {
      if (mobileActionPoint.value) {
        deleteGpsPoint(mobileActionPoint.value)
      }
    }
  }
])

const refreshGpsData = async () => {
  const selectedIds = new Set(selectedRows.value.map(row => row.id))

  await Promise.all([
    loadSummaryStats(),
    loadGPSPoints()
  ])

  if (selectedIds.size > 0) {
    selectedRows.value = gpsPoints.value.filter(row => selectedIds.has(row.id))
    lastSelectedIndex.value = null
  }
}

const {
  isPullEnabled,
  isPullRefreshing,
  pullDistance,
  pullState
} = usePullToRefresh({
  listenOnDocument: true,
  onRefresh: refreshGpsData
})

// Removed - using timezone composable directly

// Methods
const formatNumber = (value) => {
  if (!value && value !== 0) return '0'
  return new Intl.NumberFormat().format(value)
}

const formatDate = (value) => {
  if (!value) return '-'
  return timezone.formatDateDisplay(value)
}

const formatTimestamp = (timestamp) => {
  if (!timestamp) return { date: '-', time: '-' }
  return {
    date: timezone.formatDateDisplay(timestamp),
    time: timezone.formatTime(timestamp, { withSeconds: true })
  }
}

const formatTimeDelta = (timestamp, previousTimestamp) => {
  if (!timestamp || !previousTimestamp) return null

  const currentMs = Date.parse(timestamp)
  const previousMs = Date.parse(previousTimestamp)
  if (!Number.isFinite(currentMs) || !Number.isFinite(previousMs)) return null

  const totalSeconds = Math.abs(Math.round((currentMs - previousMs) / 1000))
  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60

  if (days > 0) return `${days}d ${hours}h`
  if (hours > 0) return `${hours}h ${minutes}m`
  if (minutes > 0) return `${minutes}m ${String(seconds).padStart(2, '0')}s`
  return `${seconds}s`
}

const formatTelemetryValue = (item) => {
  if (!item) return '-'
  const value = item.value ?? '-'
  if (!item.unit) return value
  if (item.unit === '%') return `${value}${item.unit}`
  return `${value} ${item.unit}`
}

const formatDateRange = (startDate, endDate) => {
  if (!startDate || !endDate) return ''
  const start = formatPickerDateTime(startDate)
  const end = formatPickerDateTime(endDate)
  return `${start} - ${end}`
}

const formatDateForAPI = (date) => {
  if (!date) return null

  // Preserve selected hour/minute from DatePicker while still interpreting in user timezone.
  const { start } = timezone.createDateTimeRangeFromPicker(date, date)
  return start
}

const getFirstQueryValue = (value) => {
  if (Array.isArray(value)) return value[0]
  return typeof value === 'string' ? value : null
}

const utcInstantToPickerDate = (value) => {
  const utcValue = getFirstQueryValue(value)
  if (!utcValue) return null

  const dateTime = timezone.fromUtc(utcValue)
  if (!dateTime.isValid()) return null

  return new Date(
    dateTime.year(),
    dateTime.month(),
    dateTime.date(),
    dateTime.hour(),
    dateTime.minute(),
    dateTime.second(),
    dateTime.millisecond()
  )
}

const hydrateDateFilterFromRouteQuery = () => {
  const queryStart = utcInstantToPickerDate(route.query.startTime)
  const queryEnd = utcInstantToPickerDate(route.query.endTime)

  if (!isValidDateValue(queryStart) || !isValidDateValue(queryEnd) || queryStart.getTime() > queryEnd.getTime()) {
    return
  }

  startDateTime.value = queryStart
  endDateTime.value = queryEnd
  appliedStartDateTime.value = new Date(queryStart.getTime())
  appliedEndDateTime.value = new Date(queryEnd.getTime())
}

const formatPickerDateTime = (date) => {
  const utcDateTime = formatDateForAPI(date)
  return utcDateTime ? timezone.formatDateTimeDisplay(utcDateTime) : '-'
}

const getSourceSeverity = (sourceType) => {
  const severityMap = {
    'OWNTRACKS': 'success',
    'GPSLOGGER': 'success',
    'OVERLAND': 'info',
    'TRACCAR': 'info',
    'MANUAL': 'warning',
    'IMPORT': 'secondary',
    'GOOGLE_TIMELINE': 'danger',
    'DAWARICH' : 'danger',
    'COLOTA': 'success',
    'GPX': 'contrast'
  }
  return severityMap[sourceType] || 'contrast'
}

const cloneTelemetryRows = (rows = []) => {
  if (!Array.isArray(rows)) return []
  return rows.map((entry, index) => ({
    key: entry.key || '',
    label: entry.label || entry.key || '',
    type: entry.type || 'string',
    unit: entry.unit || '',
    order: Number.isFinite(entry.order) ? entry.order : (index + 1) * 10,
    trueValues: Array.isArray(entry.trueValues) ? [...entry.trueValues] : (entry.trueValues || ''),
    falseValues: Array.isArray(entry.falseValues) ? [...entry.falseValues] : (entry.falseValues || ''),
    showInGpsData: entry.enabled === false ? false : (entry.showInGpsData ?? true),
    showInCurrentPopup: entry.enabled === false ? false : (entry.showInCurrentPopup ?? true)
  }))
}

const serializeTelemetryValues = (value) => {
  if (Array.isArray(value)) {
    return value.map(v => String(v || '').trim()).filter(v => v.length > 0)
  }
  if (!value) return []
  return String(value)
    .split(',')
    .map(v => v.trim())
    .filter(v => v.length > 0)
}

const buildTelemetryPayload = () => telemetryMappingRows.value
  .filter(entry => String(entry.key || '').trim().length > 0)
  .map((entry, index) => ({
    key: String(entry.key || '').trim(),
    label: String(entry.label || entry.key || '').trim(),
    type: entry.type || 'string',
    unit: entry.unit ? String(entry.unit).trim() : null,
    enabled: Boolean(entry.showInGpsData || entry.showInCurrentPopup),
    order: Number.isFinite(entry.order) ? entry.order : (index + 1) * 10,
    trueValues: serializeTelemetryValues(entry.trueValues),
    falseValues: serializeTelemetryValues(entry.falseValues),
    showInGpsData: entry.showInGpsData ?? true,
    showInCurrentPopup: entry.showInCurrentPopup ?? true
  }))

const loadTelemetryMapping = async (sourceType = selectedTelemetrySourceType.value) => {
  telemetryLoading.value = true
  try {
    const response = await gpsSourcesStore.fetchTelemetryMapping(sourceType)
    telemetryMappingRows.value = cloneTelemetryRows(response?.mapping)
  } catch (error) {
    console.error('Error loading telemetry mapping:', error)
    telemetryMappingRows.value = []
    toast.add({
      severity: 'error',
      summary: t('technicalData.page.toasts.telemetryLoadFailedSummary'),
      detail: formatApiErrorDetail(error, t('technicalData.page.toasts.telemetryLoadFailedFallback')),
      life: 5000
    })
  } finally {
    telemetryLoading.value = false
  }
}

const addTelemetryMappingRow = () => {
  const nextOrder = ((telemetryMappingRows.value.length || 0) + 1) * 10
  telemetryMappingRows.value = [
    ...telemetryMappingRows.value,
    {
      key: '',
      label: '',
      type: 'string',
      unit: '',
      order: nextOrder,
      trueValues: [],
      falseValues: [],
      showInGpsData: true,
      showInCurrentPopup: true
    }
  ]
}

const removeTelemetryMappingRow = (index) => {
  const next = [...telemetryMappingRows.value]
  next.splice(index, 1)
  telemetryMappingRows.value = next
}

const saveTelemetryMapping = async () => {
  telemetrySaving.value = true
  try {
    const response = await gpsSourcesStore.updateTelemetryMapping(
      selectedTelemetrySourceType.value,
      buildTelemetryPayload()
    )
    telemetryMappingRows.value = cloneTelemetryRows(response?.mapping)
    toast.add({
      severity: 'success',
      summary: t('technicalData.page.toasts.telemetrySavedSummary'),
      detail: t('technicalData.page.toasts.telemetrySavedDetail'),
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('technicalData.page.toasts.saveFailedSummary'),
      detail: formatApiErrorDetail(error, t('technicalData.page.toasts.saveTelemetryFailedFallback')),
      life: 5000
    })
  } finally {
    telemetrySaving.value = false
  }
}

const resetTelemetryMapping = async () => {
  telemetryResetting.value = true
  try {
    const response = await gpsSourcesStore.resetTelemetryMapping(selectedTelemetrySourceType.value)
    telemetryMappingRows.value = cloneTelemetryRows(response?.mapping)
    toast.add({
      severity: 'success',
      summary: t('technicalData.page.toasts.telemetryResetSummary'),
      detail: t('technicalData.page.toasts.telemetryResetDetail'),
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('technicalData.page.toasts.resetFailedSummary'),
      detail: formatApiErrorDetail(error, t('technicalData.page.toasts.resetTelemetryFailedFallback')),
      life: 5000
    })
  } finally {
    telemetryResetting.value = false
  }
}

const handleResize = () => {
  isMobile.value = window.innerWidth < 768
  isTablet.value = window.innerWidth >= 768 && window.innerWidth < 1024
  if (isMobile.value && !mobileSortOptions.value.some(option => option.value === sortField.value)) {
    sortField.value = 'timestamp'
    sortOrder.value = -1
  }
  pageSize.value = isMobile.value ? 25 : 50
}

const refreshAfterFilterChange = async () => {
  currentPage.value = 0
  selectedRows.value = [] // Clear selection when filters change
  lastSelectedIndex.value = null // Reset shift-click tracking
  await loadGPSPoints()
  await loadSummaryStats()
}

const applyDateFilter = async () => {
  if (!canApplyDateFilter.value) return
  appliedStartDateTime.value = new Date(startDateTime.value.getTime())
  appliedEndDateTime.value = new Date(endDateTime.value.getTime())
  await refreshAfterFilterChange()
}

const clearDateFilter = async () => {
  startDateTime.value = null
  endDateTime.value = null
  appliedStartDateTime.value = null
  appliedEndDateTime.value = null
  await refreshAfterFilterChange()
}

// Quick date preset methods
const setToday = () => {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0)
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59)
  startDateTime.value = start
  endDateTime.value = end
  applyDateFilter()
}

const setYesterday = () => {
  const yesterday = new Date()
  yesterday.setDate(yesterday.getDate() - 1)
  const start = new Date(yesterday.getFullYear(), yesterday.getMonth(), yesterday.getDate(), 0, 0, 0)
  const end = new Date(yesterday.getFullYear(), yesterday.getMonth(), yesterday.getDate(), 23, 59, 59)
  startDateTime.value = start
  endDateTime.value = end
  applyDateFilter()
}

const setLast7Days = () => {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 7)
  start.setHours(0, 0, 0, 0)
  end.setHours(23, 59, 59, 999)
  startDateTime.value = start
  endDateTime.value = end
  applyDateFilter()
}

const setLast30Days = () => {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 30)
  start.setHours(0, 0, 0, 0)
  end.setHours(23, 59, 59, 999)
  startDateTime.value = start
  endDateTime.value = end
  applyDateFilter()
}

const clearAllFilters = async () => {
  startDateTime.value = null
  endDateTime.value = null
  appliedStartDateTime.value = null
  appliedEndDateTime.value = null
  filters.value = {
    sourceTypes: [],
    accuracyMin: null,
    accuracyMax: null,
    speedMin: null,
    speedMax: null
  }
  await refreshAfterFilterChange()
}

const onPageChange = async (event) => {
  currentPage.value = event.page
  lastSelectedIndex.value = null // Reset shift-click tracking on page change
  await loadGPSPoints()
}

const onSort = async (event) => {
  sortField.value = event.sortField
  sortOrder.value = event.sortOrder
  currentPage.value = 0 // Reset to first page on sort
  await loadGPSPoints()
}

const onMobileSortFieldChange = async () => {
  currentPage.value = 0
  await loadGPSPoints()
}

const toggleMobileSortDirection = async () => {
  sortOrder.value = sortOrder.value === 1 ? -1 : 1
  currentPage.value = 0
  await loadGPSPoints()
}

const onMobilePageChange = async (event) => {
  await onPageChange(event)
}

const toggleVisibleGpsRowsSelection = () => {
  const visibleRows = gpsPointsWithDelta.value
  if (visibleRows.length === 0) return

  if (allVisibleGpsRowsSelected.value) {
    const visibleIds = new Set(visibleRows.map(row => row.id))
    selectedRows.value = selectedRows.value.filter(row => !visibleIds.has(row.id))
  } else {
    const selectedIds = new Set(selectedRows.value.map(row => row.id))
    selectedRows.value = [
      ...selectedRows.value,
      ...visibleRows.filter(row => !selectedIds.has(row.id))
    ]
  }

  lastSelectedIndex.value = null
}

const openMobileActionMenu = (event, gpsPoint) => {
  mobileActionPoint.value = gpsPoint
  mobileActionMenu.value?.toggle(event)
}

const loadSummaryStats = async () => {
  try {
    isLoading.value = true

    // Build filter params for summary
    const params = buildFilterParams()

    await technicalDataStore.fetchSummaryStats(params)
  } catch (error) {
    console.error('Error loading summary stats:', error)
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: t('technicalData.page.toasts.summaryLoadFailedDetail'),
      life: 3000
    })
  } finally {
    isLoading.value = false
  }
}

// Helper to build filter params
const buildFilterParams = () => {
  const params = {}

  // Date/time range
  if (hasDateFilter.value) {
    params.from = formatDateForAPI(appliedStartDateTime.value)
    params.to = formatDateForAPI(appliedEndDateTime.value)
  }

  // Advanced filters
  if (filters.value.accuracyMin !== null && filters.value.accuracyMin !== undefined) {
    params.accuracyMin = filters.value.accuracyMin
  }
  if (filters.value.accuracyMax !== null && filters.value.accuracyMax !== undefined) {
    params.accuracyMax = filters.value.accuracyMax
  }
  if (filters.value.speedMin !== null && filters.value.speedMin !== undefined) {
    params.speedMin = filters.value.speedMin
  }
  if (filters.value.speedMax !== null && filters.value.speedMax !== undefined) {
    params.speedMax = filters.value.speedMax
  }
  if (filters.value.sourceTypes && filters.value.sourceTypes.length > 0) {
    params.sourceTypes = filters.value.sourceTypes.join(',')
  }

  return params
}

const loadGPSPoints = async () => {
  try {
    tableLoading.value = true

    // Start with pagination and sorting
    const params = {
      page: currentPage.value + 1,
      size: pageSize.value,
      sortBy: sortField.value,
      sortDirection: sortOrder.value === 1 ? 'asc' : 'desc'
    }

    // Merge with filter params
    Object.assign(params, buildFilterParams())

    await technicalDataStore.fetchGPSPoints(params)
  } catch (error) {
    console.error('Error loading GPS points:', error)
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: t('technicalData.page.toasts.pointsLoadFailedDetail'),
      life: 3000
    })
  } finally {
    tableLoading.value = false
  }
}

const handleExportCSV = async () => {
  try {
    exportLoading.value = true

    // Priority 1: If manual selection is active, export only selected rows
    if (selectedRows.value.length > 0) {
      const selectedIds = selectedRows.value.map(row => row.id)
      await technicalDataStore.exportGPSPoints({}, selectedIds)

      toast.add({
        severity: 'success',
        summary: t('technicalData.page.toasts.exportStartedSummary'),
        detail: t('technicalData.page.toasts.exportSelectedDetail', { count: selectedRows.value.length }),
        life: 4000
      })
      return
    }

    // Priority 2 & 3: Export with filters or all data
    const params = buildFilterParams()
    await technicalDataStore.exportGPSPoints(params)

    const recordCount = totalRecords.value || summaryStats.value.totalPoints
    toast.add({
      severity: 'success',
      summary: t('technicalData.page.toasts.exportStartedSummary'),
      detail: t('technicalData.page.toasts.exportAllDetail', { count: recordCount.toLocaleString() }),
      life: 4000
    })
  } catch (error) {
    console.error('Error exporting GPS points:', error)
    toast.add({
      severity: 'error',
      summary: t('technicalData.page.toasts.exportFailedSummary'),
      detail: t('technicalData.page.toasts.exportFailedDetail'),
      life: 3000
    })
  } finally {
    exportLoading.value = false
  }
}

const editGpsPoint = (gpsPoint) => {
  selectedGpsPoint.value = gpsPoint
  showEditDialog.value = true
}

const deleteGpsPoint = (gpsPoint) => {
  selectedGpsPoint.value = gpsPoint
  showDeleteDialog.value = true
}

const getDeleteResultData = (result) => result?.data || result || {}

const buildDeleteSuccessDetail = (baseMessage, result) => {
  const data = getDeleteResultData(result)
  const details = [baseMessage]
  let timelineJobUrl = null

  if (data.timelineJobId) {
    timelineJobUrl = router.resolve(`/app/timeline/jobs/${data.timelineJobId}`).href
    details.push(t('technicalData.page.toasts.timelineRegenStarted'))
  } else if (data.timelineRegenerationScheduled) {
    details.push(t('technicalData.page.toasts.timelineRegenScheduled'))
  }

  if (data.coverageRebuildScheduled) {
    details.push(t('technicalData.page.toasts.coverageRebuildScheduled'))
  }

  return {
    detail: details.join(' '),
    timelineJobUrl
  }
}

const confirmDeleteGpsPoint = async () => {
  if (!selectedGpsPoint.value || deleteLoading.value) return
  
  deleteLoading.value = true
  
  try {
    const result = await technicalDataStore.deleteGpsPoint(selectedGpsPoint.value.id)
    const successDetail = buildDeleteSuccessDetail(t('technicalData.page.toasts.pointDeletedDetail'), result)

    toast.add({
      group: 'gps-delete',
      severity: 'success',
      summary: t('technicalData.page.toasts.pointDeletedSummary'),
      detail: successDetail.detail,
      data: {
        timelineJobUrl: successDetail.timelineJobUrl
      },
      life: 10000
    })

    // Refresh the data
    await loadGPSPoints()
    await loadSummaryStats()

  } catch (error) {
    console.error('Error deleting GPS point:', error)
    const errorMessage = formatApiErrorDetail(error, t('technicalData.page.toasts.deleteFailedFallback'))
    toast.add({
      severity: 'error',
      summary: t('technicalData.page.toasts.deleteFailedSummary'),
      detail: errorMessage,
      life: 5000
    })
  } finally {
    deleteLoading.value = false
    showDeleteDialog.value = false
    selectedGpsPoint.value = null
  }
}

const handleEditSave = async (updatedData) => {
  if (!selectedGpsPoint.value) return
  
  try {
    await technicalDataStore.updateGpsPoint(selectedGpsPoint.value.id, updatedData)

    toast.add({
      severity: 'success',
      summary: t('technicalData.page.toasts.pointUpdatedSummary'),
      detail: t('technicalData.page.toasts.pointUpdatedDetail'),
      life: 3000
    })

    // Refresh the data
    await loadGPSPoints()

  } catch (error) {
    console.error('Error updating GPS point:', error)
    const errorMessage = formatApiErrorDetail(error, t('technicalData.page.toasts.updateFailedFallback'))
    toast.add({
      severity: 'error',
      summary: t('technicalData.page.toasts.updateFailedSummary'),
      detail: errorMessage,
      life: 5000
    })
  } finally {
    showEditDialog.value = false
    selectedGpsPoint.value = null
  }
}

const bulkDeleteGpsPoints = () => {
  if (selectedRows.value.length === 0) return
  showBulkDeleteDialog.value = true
}

const confirmBulkDelete = async () => {
  if (selectedRows.value.length === 0 || bulkDeleteLoading.value) return

  bulkDeleteLoading.value = true

  try {
    const pointIds = selectedRows.value.map(row => row.id)
    const result = await technicalDataStore.deleteGpsPoints(pointIds)
    const deleteData = getDeleteResultData(result)

    // Extract the correct count from the response
    const deletedCount = deleteData.deletedCount ?? pointIds.length
    const successDetail = buildDeleteSuccessDetail(
      t('technicalData.page.toasts.bulkDeleteSuccessDetail', { count: deletedCount }, deletedCount),
      result
    )

    toast.add({
      group: 'gps-delete',
      severity: 'success',
      summary: t('technicalData.page.toasts.pointsDeletedSummary'),
      detail: successDetail.detail,
      data: {
        timelineJobUrl: successDetail.timelineJobUrl
      },
      life: 5000
    })

    // Clear selection and refresh data
    selectedRows.value = []
    lastSelectedIndex.value = null // Reset shift-click tracking
    await loadGPSPoints()
    await loadSummaryStats()

  } catch (error) {
    console.error('Error deleting GPS points:', error)
    const errorMessage = formatApiErrorDetail(error, t('technicalData.page.toasts.bulkDeleteFailedFallback'))
    toast.add({
      severity: 'error',
      summary: t('technicalData.page.toasts.bulkDeleteFailedSummary'),
      detail: errorMessage,
      life: 5000
    })
  } finally {
    bulkDeleteLoading.value = false
    showBulkDeleteDialog.value = false
  }
}

const confirmDeleteAll = async () => {
  if (deleteAllLoading.value) return

  deleteAllLoading.value = true

  try {
    await technicalDataStore.deleteAllGpsData()

    toast.add({
      severity: 'success',
      summary: t('technicalData.page.toasts.allDataDeletedSummary'),
      detail: t('technicalData.page.toasts.allDataDeletedDetail'),
      life: 4000
    })

    selectedRows.value = []
    lastSelectedIndex.value = null
    currentPage.value = 0

    await Promise.all([loadSummaryStats(), loadGPSPoints()])

  } catch (error) {
    console.error('Error deleting all GPS data:', error)
    const errorMessage = formatApiErrorDetail(error, t('technicalData.page.toasts.deleteAllFailedFallback'))
    toast.add({
      severity: 'error',
      summary: t('technicalData.page.toasts.deleteFailedSummary'),
      detail: errorMessage,
      life: 5000
    })
  } finally {
    deleteAllLoading.value = false
    showDeleteAllDialog.value = false
  }
}

// Check if a row is selected
const isRowSelected = (row) => {
  return selectedRows.value.some(selectedRow => selectedRow.id === row.id)
}

// Shared shift-select logic
const selectRange = (clickedIndex) => {
  if (lastSelectedIndex.value !== null && clickedIndex !== -1) {
    const start = Math.min(lastSelectedIndex.value, clickedIndex)
    const end = Math.max(lastSelectedIndex.value, clickedIndex)

    // Get all rows in the range
    const rangeRows = gpsPoints.value.slice(start, end + 1)

    // Create a set of currently selected IDs for faster lookup
    const selectedIds = new Set(selectedRows.value.map(row => row.id))

    // Add all rows in range to selection (avoid duplicates)
    rangeRows.forEach(row => {
      if (!selectedIds.has(row.id)) {
        selectedRows.value.push(row)
      }
    })
  }
}

const setRowSelected = (row, checked) => {
  if (checked) {
    if (!isRowSelected(row)) {
      selectedRows.value = [...selectedRows.value, row]
    }
    return
  }

  selectedRows.value = selectedRows.value.filter(selectedRow => selectedRow.id !== row.id)
}

// Handle checkbox change with shift-select functionality
const handleCheckboxChange = (event, clickedRow) => {
  const clickedIndex = gpsPoints.value.findIndex(row => row.id === clickedRow.id)

  // If shift key is pressed and we have a previous selection
  if (event.shiftKey && lastSelectedIndex.value !== null) {
    selectRange(clickedIndex)
  } else {
    setRowSelected(clickedRow, event.target?.checked ?? !isRowSelected(clickedRow))
  }

  // Update last selected index
  lastSelectedIndex.value = clickedIndex
}

// Handle row click for shift-select functionality
const handleRowClick = (event) => {
  const clickedRow = event.data
  const clickedIndex = gpsPoints.value.findIndex(row => row.id === clickedRow.id)

  // If shift key is pressed, select range
  if (event.originalEvent.shiftKey) {
    selectRange(clickedIndex)
  }

  // Update last selected index
  lastSelectedIndex.value = clickedIndex
}

// Lifecycle
onMounted(async () => {
  handleResize()
  window.addEventListener('resize', handleResize)
  hydrateDateFilterFromRouteQuery()
  
  await Promise.all([
    loadSummaryStats(),
    loadGPSPoints(),
    loadTelemetryMapping(selectedTelemetrySourceType.value)
  ])
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})

// Watchers
watch(pageSize, async () => {
  currentPage.value = 0
  await loadGPSPoints()
})

watch(selectedTelemetrySourceType, async (type) => {
  await loadTelemetryMapping(type)
})

// Watch filters for changes
watch(filters, async () => {
  currentPage.value = 0
  selectedRows.value = [] // Clear selection when filters change
  lastSelectedIndex.value = null // Reset shift-click tracking
  await loadGPSPoints()
  await loadSummaryStats()
}, { deep: true })
</script>

<style scoped>
/* Stats Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--gp-spacing-md);
  margin-bottom: var(--gp-spacing-xl);
  width: 100%;
  box-sizing: border-box;
}

/* Large screen optimizations - ensure 4 cards in one row on larger monitors */
@media (min-width: 1024px) {
  .stats-grid {
    grid-template-columns: repeat(4, 1fr);
    max-width: none;
    gap: var(--gp-spacing-md);
  }
}

/* Extra large screens */
@media (min-width: 1440px) {
  .stats-grid {
    grid-template-columns: repeat(4, 1fr);
    max-width: none;
    gap: var(--gp-spacing-lg);
  }
}

/* Extra large screens - even wider spacing */
@media (min-width: 1920px) {
  .stats-grid {
    gap: var(--gp-spacing-lg);
  }
}

/* Tablet screens - maintain 2x2 grid with better spacing */
@media (min-width: 481px) and (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr 1fr;
    gap: var(--gp-spacing-md);
  }

  .stat-card {
    padding: var(--gp-spacing-md);
  }

  .stat-card :deep(.gp-metric-value) {
    font-size: 1.2rem;
    font-weight: 600;
  }

  .stat-card :deep(.gp-metric-label) {
    font-size: 0.8rem;
  }

  .stat-card :deep(.gp-metric-icon) {
    width: 32px;
    height: 32px;
    font-size: 1rem;
  }
}

.stat-card {
  padding: var(--gp-spacing-md);
}

/* Filter Section */
.filter-section {
  margin-bottom: var(--gp-spacing-lg);
}

.filter-controls {
  display: flex;
  align-items: flex-end;
  gap: var(--gp-spacing-lg);
  flex-wrap: wrap;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  flex: 1;
  min-width: 250px;
}

.filter-label {
  font-weight: 500;
  color: var(--gp-text-secondary);
  white-space: nowrap;
}

.date-picker {
  flex: 1;
  max-width: 300px;
}

.date-time-filter-group {
  display: grid;
  grid-template-columns: repeat(2, minmax(220px, 1fr));
  gap: var(--gp-spacing-md);
  flex: 1 1 560px;
  max-width: 760px;
}

.date-time-field {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
  min-width: 0;
}

.date-time-picker {
  width: 100%;
  max-width: none;
}

.date-filter-apply-button {
  flex-shrink: 0;
}

.filter-validation-message {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  margin-top: var(--gp-spacing-sm);
  color: var(--red-600);
  font-size: 0.875rem;
}

.filter-validation-message i {
  font-size: 0.875rem;
}

.telemetry-mapping-section {
  margin-bottom: var(--gp-spacing-lg);
}

.telemetry-mapping-content {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.telemetry-mapping-controls {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.telemetry-mapping-select {
  min-width: 220px;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.telemetry-source-select {
  width: 220px;
}

.telemetry-mapping-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.telemetry-loading {
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
}

.telemetry-table-wrapper {
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
  overflow-x: auto;
  background: var(--gp-surface-white);
}

.telemetry-table {
  width: 100%;
  min-width: 980px;
  border-collapse: collapse;
}

.telemetry-table th,
.telemetry-table td {
  border-bottom: 1px solid var(--gp-border-light);
  padding: 0.45rem 0.5rem;
  text-align: left;
  vertical-align: middle;
  font-size: 0.82rem;
}

.telemetry-table th {
  white-space: nowrap;
  color: var(--gp-text-secondary);
  font-weight: 600;
  background: var(--gp-surface-light);
}

.telemetry-table tbody tr:last-child td {
  border-bottom: none;
}

.telemetry-input {
  width: 100%;
}

.telemetry-unit {
  min-width: 72px;
}

.telemetry-order {
  max-width: 90px;
}

.telemetry-check-cell {
  text-align: center;
}

.telemetry-remove-cell {
  text-align: center;
}

.telemetry-empty {
  text-align: center;
  color: var(--gp-text-muted, #6b7280);
  font-style: italic;
}

.telemetry-boolean-grid {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.telemetry-boolean-row {
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
  padding: 0.6rem;
  background: var(--gp-surface-white);
}

.telemetry-boolean-title {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.45rem;
}

.telemetry-boolean-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.5rem;
}

/* Large screen filter optimizations */
@media (min-width: 1440px) {
  .filter-controls {
    justify-content: flex-start;
    flex-wrap: nowrap;
  }
  
  .filter-group {
    flex: 0 0 auto;
    min-width: 300px;
  }
  
  .date-picker {
    max-width: 400px;
  }

  .date-time-picker {
    max-width: none;
  }
}

/* Table Section */
.table-section {
  overflow: hidden;
}

.gps-data-table :deep(.p-datatable-table) {
  width: 100%;
  min-width: 80rem;
  table-layout: fixed;
}

/* Large screen table optimizations */
@media (min-width: 1440px) {
  .table-section {
    margin: 0 -var(--gp-spacing-md); /* Extend table slightly beyond container */
  }
}

/* Mobile table constraints */
@media (max-width: 768px) {
  .table-section {
    margin: 0;
    max-width: 100%;
    overflow-x: auto;
  }

  .gps-data-table {
    max-width: 100%;
    width: 100%;
  }

  /* Force table to fit in viewport */
  .gps-data-table :deep(.p-datatable) {
    max-width: 100% !important;
    width: 100% !important;
  }

  .gps-data-table :deep(.p-datatable-wrapper) {
    overflow-x: auto;
    max-width: 100%;
  }

  .gps-data-table :deep(.p-datatable-table) {
    width: max-content;
    min-width: unset;
    table-layout: auto;
  }

  /* Mobile paginator optimizations */
  .gps-data-table :deep(.p-paginator) {
    flex-wrap: nowrap !important;
    justify-content: center !important;
    gap: 4px !important;
    padding: var(--gp-spacing-sm) !important;
  }

  .gps-data-table :deep(.p-paginator .p-paginator-page),
  .gps-data-table :deep(.p-paginator .p-paginator-next),
  .gps-data-table :deep(.p-paginator .p-paginator-prev),  
  .gps-data-table :deep(.p-paginator .p-paginator-first),
  .gps-data-table :deep(.p-paginator .p-paginator-last) {
    min-width: 32px !important;
    width: 32px !important;
    height: 32px !important;
    padding: 0 !important;
    margin: 0 1px !important;
    font-size: 0.8rem !important;
    display: flex !important;
    align-items: center !important;
    justify-content: center !important;
  }

  /* Hide some pagination elements on very small screens */
  .gps-data-table :deep(.p-paginator .p-paginator-first),
  .gps-data-table :deep(.p-paginator .p-paginator-last) {
    display: none !important;
  }

  /* Show fewer page numbers on mobile */
  .gps-data-table :deep(.p-paginator .p-paginator-page:nth-child(n+8)) {
    display: none !important;
  }

  .telemetry-mapping-controls {
    align-items: stretch;
  }

  .telemetry-source-select {
    width: 100%;
  }

  .telemetry-mapping-actions {
    width: 100%;
  }

  .telemetry-boolean-fields {
    grid-template-columns: 1fr;
  }
}

.table-header {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.table-title {
  font-weight: 600;
  font-size: 1.1rem;
  color: var(--gp-text-primary);
}

.table-subtitle {
  font-size: 0.875rem;
  color: var(--gp-text-muted);
}

/* Table Columns */
.gps-data-table :deep(.timestamp-col) {
  min-width: 11rem;
  width: 12%;
}

.gps-data-table :deep(.delta-col) {
  min-width: 6.25rem;
  width: 7%;
  text-align: right;
}

.gps-data-table :deep(.coordinates-col) {
  min-width: 14rem;
  width: 14%;
}

.gps-data-table :deep(.numeric-col) {
  text-align: right;
}

.gps-data-table :deep(.speed-col),
.gps-data-table :deep(.accuracy-col),
.gps-data-table :deep(.altitude-col) {
  min-width: 7rem;
  width: 8%;
}

.gps-data-table :deep(.battery-col) {
  min-width: 6.25rem;
  width: 7%;
}

.gps-data-table :deep(.source-col) {
  min-width: 9rem;
  width: 10%;
}

.gps-data-table :deep(.telemetry-col) {
  min-width: 12rem;
  width: 17%;
}

.gps-data-table :deep(.selection-col) {
  min-width: 3rem;
  width: 3%;
  text-align: center;
}

.gps-data-table :deep(.actions-col) {
  min-width: 5.5rem;
  width: 6%;
  text-align: center;
}

.gps-data-table :deep(.selection-col .p-datatable-column-header-content),
.gps-data-table :deep(.actions-col .p-datatable-column-header-content) {
  justify-content: center;
}

.gps-data-table :deep(.delta-col .p-datatable-column-header-content),
.gps-data-table :deep(.numeric-col .p-datatable-column-header-content) {
  justify-content: flex-end;
}

/* Cell Content */
.timestamp-cell {
  display: flex;
  align-items: baseline;
  gap: 0.35rem;
  white-space: nowrap;
}

.timestamp-date {
  font-weight: 500;
  font-size: 0.875rem;
  color: var(--gp-text-primary);
}

.timestamp-time {
  font-size: 0.75rem;
  color: var(--gp-text-muted);
  font-family: monospace;
}

.coordinates-cell {
  display: flex;
  align-items: baseline;
  gap: 0.25rem;
  white-space: nowrap;
}

.coordinate-line {
  font-family: monospace;
  font-size: 0.8rem;
  color: var(--gp-text-primary);
}

.coordinate-separator {
  color: var(--gp-text-muted);
  font-family: monospace;
  font-size: 0.8rem;
}

.telemetry-cell {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.telemetry-item {
  font-size: 0.72rem;
  line-height: 1.2;
}

.telemetry-label {
  color: var(--gp-text-secondary);
  margin-right: 0.25rem;
}

.telemetry-value {
  color: var(--gp-text-primary);
  font-weight: 500;
}

.null-value {
  color: var(--gp-text-muted);
  font-style: italic;
}

.source-tag {
  font-size: 0.75rem;
}

/* Empty State */
.empty-state {
  text-align: center;
  padding: var(--gp-spacing-xxl) var(--gp-spacing-lg);
}

.empty-icon {
  font-size: 3rem;
  color: var(--gp-text-muted);
  margin-bottom: var(--gp-spacing-lg);
  display: block;
}

.empty-state h3 {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-secondary);
  margin: 0 0 var(--gp-spacing-md);
}

.empty-state p {
  color: var(--gp-text-muted);
  margin: 0;
}

/* Mobile GPS List */
.mobile-gps-list-panel {
  width: 100%;
  min-width: 0;
  overflow: hidden;
  background: var(--gp-surface-white);
}

.mobile-gps-header {
  padding: var(--gp-spacing-lg);
  border-bottom: 1px solid var(--gp-border-light);
}

.mobile-gps-toolbar {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: var(--gp-spacing-md);
  padding: var(--gp-spacing-md) var(--gp-spacing-lg);
  border-bottom: 1px solid var(--gp-border-light);
}

.mobile-select-page-control {
  display: inline-flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  min-width: max-content;
  color: var(--gp-text-secondary);
  font-size: 0.85rem;
  font-weight: 500;
}

.mobile-sort-control {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--gp-spacing-sm);
  width: 100%;
  min-width: 0;
}

.mobile-sort-label {
  font-size: 0.85rem;
  font-weight: 500;
  color: var(--gp-text-secondary);
}

.mobile-sort-dropdown {
  width: 100%;
  min-width: 0;
}

.mobile-sort-direction-button,
.mobile-gps-actions-button {
  width: 2.25rem !important;
  height: 2.25rem !important;
  min-width: 2.25rem !important;
  padding: 0 !important;
}

.mobile-gps-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--gp-spacing-sm);
  min-height: 10rem;
  padding: var(--gp-spacing-xl) var(--gp-spacing-lg);
  color: var(--gp-text-secondary);
}

.mobile-gps-list {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;
}

.mobile-gps-row {
  display: grid;
  grid-template-columns: 2rem minmax(0, 1fr) 2.25rem;
  align-items: center;
  gap: var(--gp-spacing-sm);
  width: 100%;
  min-width: 0;
  padding: var(--gp-spacing-md) var(--gp-spacing-lg);
  border-bottom: 1px solid var(--gp-border-light);
  box-sizing: border-box;
}

.mobile-gps-row:last-child {
  border-bottom: none;
}

.mobile-gps-checkbox {
  justify-self: center;
}

.mobile-gps-main {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  min-width: 0;
}

.mobile-gps-primary {
  display: flex;
  align-items: baseline;
  gap: var(--gp-spacing-sm);
  min-width: 0;
}

.mobile-gps-date {
  font-weight: 600;
  color: var(--gp-text-primary);
}

.mobile-gps-time {
  font-family: monospace;
  font-size: 0.85rem;
  color: var(--gp-text-muted);
}

.mobile-gps-coordinates {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: monospace;
  font-size: 0.82rem;
  color: var(--gp-text-secondary);
}

.mobile-gps-meta {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  flex-wrap: wrap;
  min-width: 0;
}

.mobile-gps-metric {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  max-width: 100%;
  padding: 0.15rem 0.45rem;
  border-radius: var(--gp-radius-small);
  background: var(--gp-surface-light);
  color: var(--gp-text-secondary);
  font-size: 0.78rem;
  line-height: 1.25;
}

.mobile-gps-metric i {
  font-size: 0.75rem;
}

.mobile-gps-paginator {
  border-top: 1px solid var(--gp-border-light);
}

.mobile-empty-state {
  padding: var(--gp-spacing-xl) var(--gp-spacing-lg);
}

/* Responsive Design */
@media (max-width: 768px) {
  /* Override PageContainer padding for mobile with balanced margins */
  :deep(.gp-page-container--fullwidth) {
    padding-left: var(--gp-spacing-md) !important;
    padding-right: var(--gp-spacing-md) !important;
    max-width: 100vw !important;
    overflow-x: hidden;
    box-sizing: border-box;
  }

  /* Override page content container */
  :deep(.gp-page-content) {
    max-width: 100% !important;
    overflow-x: hidden;
    box-sizing: border-box;
  }

  /* Force all content to respect viewport width */
  .stats-grid,
  .filter-section,
  .table-section {
    max-width: 100%;
    box-sizing: border-box;
    margin-left: 0;
    margin-right: 0;
  }

  /* Ensure BaseCards don't exceed viewport */
  .stat-card,
  .filter-section,
  .table-section {
    margin-left: 0 !important;
    margin-right: 0 !important;
    box-sizing: border-box;
  }

  .stats-grid {
    grid-template-columns: 1fr 1fr;
    gap: var(--gp-spacing-sm);
    margin-left: 0;
    margin-right: 0;
    width: 100%;
    box-sizing: border-box;
  }

  .stat-card {
    min-width: 0; /* Allow cards to shrink */
    width: 100%;
    padding: var(--gp-spacing-sm);
    box-sizing: border-box;
    overflow: hidden; /* Prevent content overflow */
  }

  /* Ensure MetricItem content respects card boundaries */
  .stat-card :deep(.gp-metric-item) {
    gap: var(--gp-spacing-sm);
    padding: var(--gp-spacing-sm) 0;
  }

  .stat-card :deep(.gp-metric-content) {
    min-width: 0;
    overflow: hidden;
  }

  .stat-card :deep(.gp-metric-value) {
    font-size: 1.1rem;
    font-weight: 600;
    word-break: break-word;
    line-height: 1.2;
  }

  .stat-card :deep(.gp-metric-label) {
    font-size: 0.75rem;
    word-break: break-word;
    line-height: 1.3;
    margin-top: 0.125rem;
  }

  .stat-card :deep(.gp-metric-icon) {
    width: 28px;
    height: 28px;
    font-size: 0.9rem;
  }

  .filter-controls {
    flex-direction: column;
    align-items: stretch;
    gap: var(--gp-spacing-md);
    width: 100%;
    max-width: 100%;
    box-sizing: border-box;
  }

  .filter-group {
    flex-direction: column;
    align-items: stretch;
    min-width: unset;
    width: 100%;
    max-width: 100%;
  }

  .date-time-filter-group {
    grid-template-columns: 1fr;
    flex-basis: auto;
    max-width: 100%;
    width: 100%;
  }

  .date-picker,
  .date-time-picker {
    max-width: 100%;
    width: 100%;
    min-width: unset;
  }

  .date-filter-apply-button,
  .filter-controls > .p-button {
    width: 100%;
    justify-content: center;
  }

  /* Ensure datepicker component fits */
  .filter-section :deep(.p-datepicker) {
    width: 100% !important;
    max-width: 100% !important;
  }

  .filter-section :deep(.p-inputtext) {
    width: 100% !important;
    max-width: 100% !important;
    box-sizing: border-box;
  }

  .gps-data-table :deep(.timestamp-col) {
    min-width: 105px;
    width: 105px;
  }

  .gps-data-table :deep(.coordinates-col) {
    min-width: 100px;
    width: 100px;
  }

  .gps-data-table :deep(.numeric-col) {
    min-width: 80px;
    width: 80px;
  }

  .gps-data-table :deep(.telemetry-col) {
    min-width: 190px;
    width: 190px;
  }

  .timestamp-cell,
  .coordinates-cell {
    align-items: flex-start;
    flex-direction: column;
    gap: 0.125rem;
    white-space: normal;
  }

  .coordinate-separator {
    display: none;
  }

  .coordinate-line {
    font-size: 0.75rem;
  }

  .empty-state {
    padding: var(--gp-spacing-xl) var(--gp-spacing-md);
  }

  .mobile-gps-header {
    gap: var(--gp-spacing-md);
  }

  .mobile-gps-toolbar {
    grid-template-columns: 1fr;
  }

  .mobile-page-size-dropdown {
    min-width: 72px;
  }

  .mobile-gps-paginator {
    max-width: 100%;
    overflow: hidden;
  }

  .mobile-gps-paginator :deep(.p-paginator) {
    flex-wrap: wrap !important;
    justify-content: center !important;
    gap: 4px !important;
    padding: var(--gp-spacing-sm) !important;
  }

  .mobile-gps-paginator :deep(.p-paginator .p-paginator-page),
  .mobile-gps-paginator :deep(.p-paginator .p-paginator-next),
  .mobile-gps-paginator :deep(.p-paginator .p-paginator-prev),
  .mobile-gps-paginator :deep(.p-paginator .p-paginator-first),
  .mobile-gps-paginator :deep(.p-paginator .p-paginator-last) {
    min-width: 32px !important;
    width: 32px !important;
    height: 32px !important;
    padding: 0 !important;
    margin: 0 1px !important;
    font-size: 0.8rem !important;
  }

  .mobile-gps-paginator :deep(.p-paginator .p-paginator-first),
  .mobile-gps-paginator :deep(.p-paginator .p-paginator-last),
  .mobile-gps-paginator :deep(.p-paginator-current) {
    display: none !important;
  }
}

/* Very small screens - keep 2x2 grid but adjust spacing */
@media (max-width: 480px) {
  .stats-grid {
    grid-template-columns: 1fr 1fr;
    gap: var(--gp-spacing-xs);
  }

  .stat-card {
    width: 100%;
    padding: var(--gp-spacing-xs) var(--gp-spacing-sm);
    box-sizing: border-box;
    overflow: hidden;
  }

  /* Adjust text sizing for very small screens */
  .stat-card :deep(.gp-metric-value) {
    font-size: 1rem;
    line-height: 1.1;
  }

  .stat-card :deep(.gp-metric-label) {
    font-size: 0.7rem;
    line-height: 1.2;
  }

  .stat-card :deep(.gp-metric-icon) {
    width: 24px;
    height: 24px;
    font-size: 0.8rem;
  }
  
  .timestamp-date,
  .timestamp-time {
    font-size: 0.8rem;
  }

  .coordinate-line {
    font-size: 0.7rem;
  }

  .mobile-gps-header,
  .mobile-gps-toolbar,
  .mobile-gps-row {
    padding-left: var(--gp-spacing-md);
    padding-right: var(--gp-spacing-md);
  }

  .mobile-gps-row {
    grid-template-columns: 1.75rem minmax(0, 1fr) 2rem;
    gap: var(--gp-spacing-xs);
  }

  .mobile-gps-primary {
    flex-wrap: wrap;
    row-gap: 0.1rem;
  }

  .mobile-gps-date {
    font-size: 0.92rem;
  }

  .mobile-gps-time,
  .mobile-gps-coordinates {
    font-size: 0.78rem;
  }

  .mobile-gps-meta {
    gap: 0.25rem;
  }

  .mobile-gps-metric {
    font-size: 0.72rem;
    padding: 0.12rem 0.35rem;
  }

  .mobile-sort-direction-button,
  .mobile-gps-actions-button {
    width: 2rem !important;
    height: 2rem !important;
    min-width: 2rem !important;
  }

  /* Extra mobile paginator optimizations for very small screens */
  .gps-data-table :deep(.p-paginator) {
    gap: 2px !important;
    padding: var(--gp-spacing-xs) !important;
  }

  .gps-data-table :deep(.p-paginator .p-paginator-page),
  .gps-data-table :deep(.p-paginator .p-paginator-next),
  .gps-data-table :deep(.p-paginator .p-paginator-prev) {
    min-width: 28px !important;
    width: 28px !important;
    height: 28px !important;
    font-size: 0.75rem !important;
  }

  /* Show even fewer elements on very small screens */
  .gps-data-table :deep(.p-paginator .p-paginator-page:nth-child(n+6)) {
    display: none !important;
  }

  .mobile-gps-paginator :deep(.p-paginator .p-paginator-page:nth-child(n+6)) {
    display: none !important;
  }
}

/* Dark Mode */
.p-dark .table-title {
  color: var(--gp-text-primary);
}

.p-dark .table-subtitle {
  color: var(--gp-text-muted);
}

.p-dark .timestamp-date {
  color: var(--gp-text-primary);
}

.p-dark .timestamp-time {
  color: var(--gp-text-muted);
}

.p-dark .coordinate-line {
  color: var(--gp-text-primary);
}

.p-dark .telemetry-label {
  color: var(--gp-text-muted);
}

.p-dark .telemetry-value {
  color: var(--gp-text-primary);
}

.p-dark .null-value {
  color: var(--gp-text-muted);
}

.p-dark .empty-icon {
  color: var(--gp-text-muted);
}

.p-dark .empty-state h3 {
  color: var(--gp-text-secondary);
}

.p-dark .empty-state p {
  color: var(--gp-text-muted);
}

.p-dark .mobile-gps-list-panel {
  background: var(--gp-surface-dark);
}

.p-dark .mobile-gps-header,
.p-dark .mobile-gps-toolbar,
.p-dark .mobile-gps-row,
.p-dark .mobile-gps-paginator {
  border-color: var(--gp-border-dark);
}

.p-dark .mobile-gps-date {
  color: var(--gp-text-primary);
}

.p-dark .mobile-gps-time {
  color: var(--gp-text-muted);
}

.p-dark .mobile-gps-coordinates,
.p-dark .mobile-sort-label,
.p-dark .mobile-select-page-control {
  color: var(--gp-text-secondary);
}

.p-dark .mobile-gps-metric {
  background: var(--gp-surface-light);
  color: var(--gp-text-secondary);
}

/* PrimeVue DataTable Dark Mode Overrides */
.p-dark .gps-data-table :deep(.p-datatable) {
  background: var(--gp-surface-dark) !important;
  color: var(--gp-text-primary) !important;
}

.p-dark .gps-data-table :deep(.p-datatable-header) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .gps-data-table :deep(.p-datatable-tbody > tr) {
  background: var(--gp-surface-dark) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .gps-data-table :deep(.p-datatable-tbody > tr:hover) {
  background: var(--gp-surface-light) !important;
}

.p-dark .gps-data-table :deep(.p-datatable-tbody > tr > td) {
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .gps-data-table :deep(.p-datatable-thead > tr > th) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .gps-data-table :deep(.p-datatable-paginator-bottom),
.p-dark .gps-data-table :deep(.p-paginator.p-component) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border: 1px solid var(--gp-border-dark) !important;
  border-top: 1px solid var(--gp-border-dark) !important;
  border-radius: 0 0 0 0 !important;
  overflow: hidden !important;
}

/* Fix white corners by ensuring all child elements have proper background */
.p-dark .gps-data-table :deep(.p-datatable-paginator-bottom *),
.p-dark .gps-data-table :deep(.p-paginator.p-component *) {
  background-color: inherit !important;
}

.p-dark .gps-data-table :deep(.p-datatable-paginator-bottom::before),
.p-dark .gps-data-table :deep(.p-datatable-paginator-bottom::after),
.p-dark .gps-data-table :deep(.p-paginator.p-component::before),
.p-dark .gps-data-table :deep(.p-paginator.p-component::after) {
  background: var(--gp-surface-darker) !important;
}

.p-dark .gp-data-table :deep(.p-paginator) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border: none !important;
}

.p-dark .gps-data-table :deep(.p-paginator .p-paginator-page),
.p-dark .gps-data-table :deep(.p-paginator .p-paginator-next),
.p-dark .gps-data-table :deep(.p-paginator .p-paginator-prev),
.p-dark .gps-data-table :deep(.p-paginator .p-paginator-first),
.p-dark .gps-data-table :deep(.p-paginator .p-paginator-last) {
  color: var(--gp-text-primary) !important;
  background: transparent !important;
  border: 1px solid var(--gp-border-dark) !important;
  margin: 0 2px !important;
}

.p-dark .gps-data-table :deep(.p-paginator .p-paginator-page:hover),
.p-dark .gps-data-table :deep(.p-paginator .p-paginator-next:hover),
.p-dark .gps-data-table :deep(.p-paginator .p-paginator-prev:hover),
.p-dark .gps-data-table :deep(.p-paginator .p-paginator-first:hover),
.p-dark .gps-data-table :deep(.p-paginator .p-paginator-last:hover) {
  background: var(--gp-surface-light) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-medium) !important;
}

.p-dark .gps-data-table :deep(.p-paginator .p-paginator-page.p-highlight),
.p-dark .gps-data-table :deep(.p-paginator .p-paginator-page-selected) {
  background: var(--gp-primary) !important;
  color: white !important;
  border-color: var(--gp-primary) !important;
}

.p-dark .gps-data-table :deep(.p-paginator .p-paginator-current) {
  color: var(--gp-text-secondary) !important;
}


/* Fix the table wrapper to ensure proper corner styling */
.p-dark .gps-data-table :deep(.p-datatable-wrapper) {
  border-radius: var(--gp-radius-medium) !important;
  overflow: hidden !important;
  background: var(--gp-surface-dark) !important;
}

.p-dark .gps-data-table :deep(.p-datatable) {
  border-radius: var(--gp-radius-medium) !important;
  overflow: hidden !important;
}

/* Ensure the table container has proper rounded corners */
.p-dark .table-section :deep(.p-card-body) {
  padding: 0 !important;
  border-radius: var(--gp-radius-medium) !important;
  overflow: hidden !important;
}

/* Light mode paginator fixes */
.gps-data-table :deep(.p-datatable-paginator-bottom),
.gps-data-table :deep(.p-paginator.p-component) {
  background: var(--gp-surface-light) !important;
  border: 1px solid var(--gp-border-light) !important;
  border-top: 1px solid var(--gp-border-light) !important;
  border-radius: 0 0 0 0 !important;
  overflow: hidden !important;
}

/* Light mode selected page styling */
.gps-data-table :deep(.p-paginator .p-paginator-page.p-highlight),
.gps-data-table :deep(.p-paginator .p-paginator-page-selected) {
  background: var(--gp-primary) !important;
  color: white !important;
  border-color: var(--gp-primary) !important;
}

/* Paginator styling */
.gps-data-table :deep(.p-paginator .p-paginator-pages) {
  display: flex;
  align-items: center;
  gap: 4px;
}

.gps-data-table :deep(.p-paginator .p-paginator-page) {
  min-width: 2.5rem;
  height: 2.5rem;
}

.paginator-info {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  padding: 0 0.5rem;
}

.p-dark .paginator-info {
  color: var(--gp-text-secondary);
}

/* Remove unwanted focus/active borders on page header */
:deep(.gp-page-header) {
  outline: none !important;
  border: none !important;
  box-shadow: none !important;
}

:deep(.gp-page-header):focus,
:deep(.gp-page-header):active,
:deep(.gp-page-header):focus-within {
  outline: none !important;
  border: none !important;
  box-shadow: none !important;
}

/* Remove focus borders from page container */
:deep(.gp-page-container) {
  outline: none !important;
}

:deep(.gp-page-container):focus,
:deep(.gp-page-container):active {
  outline: none !important;
  border: none !important;
  box-shadow: none !important;
}

/* Actions Column */
.actions-buttons {
  display: flex;
  gap: var(--gp-spacing-xs);
  justify-content: center;
  align-items: center;
}

.action-button {
  min-width: 32px !important;
  width: 32px !important;
  height: 32px !important;
  padding: 0 !important;
  border-radius: var(--gp-radius-small);
  transition: all 0.2s ease;
}

.edit-button:hover {
  background-color: var(--gp-primary-light) !important;
  color: var(--gp-primary) !important;
}

.delete-button:hover {
  background-color: var(--p-red-50) !important;
  color: var(--p-red-600) !important;
}

/* Confirmation Dialog */
.confirm-dialog-content {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
  padding: var(--gp-spacing-md) 0;
}

.confirm-icon {
  color: var(--p-orange-500);
  font-size: 1.5rem;
  flex-shrink: 0;
}

/* Mobile Actions */
@media (max-width: 768px) {
  .gps-data-table :deep(.selection-col) {
    width: 2.5rem;
    min-width: 2.5rem;
  }

  .gps-data-table :deep(.actions-col) {
    min-width: 70px;
    width: 70px;
  }
  
  .actions-buttons {
    gap: var(--gp-spacing-xs);
  }
  
  .action-button {
    min-width: 28px !important;
    width: 28px !important;
    height: 28px !important;
    font-size: 0.75rem !important;
  }
}

/* Header Actions */
.header-actions {
  display: flex;
  gap: var(--gp-spacing-md);
  align-items: center;
}

.bulk-delete-button {
  animation: fadeInScale 0.2s ease;
}

@keyframes fadeInScale {
  from {
    opacity: 0;
    transform: scale(0.9);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

/* Mobile header actions */
@media (max-width: 768px) {
  .header-actions {
    flex-direction: row;
    flex-wrap: wrap;
    gap: var(--gp-spacing-sm);
    width: 100%;
    justify-content: flex-start;
  }

  .bulk-delete-button {
    flex: 0 0 auto;
    width: auto;
    min-width: 120px;
    max-width: 140px;
    order: 1;
  }

  .header-actions > button:not(.bulk-delete-button) {
    flex: 0 0 auto;
    width: auto;
    min-width: 100px;
    max-width: 120px;
    order: 2;
  }
}

/* Delete All Warning Dialog */
.confirm-icon--large {
  font-size: 2rem;
  align-self: flex-start;
  margin-top: 0.1rem;
}

.delete-all-warning-title {
  font-weight: 600;
  margin: 0 0 var(--gp-spacing-sm);
  color: var(--gp-text-primary);
}

.delete-all-warning-list {
  margin: 0 0 var(--gp-spacing-md);
  padding-left: 1.25rem;
  color: var(--gp-text-primary);
  line-height: 1.8;
}

.delete-all-warning-note {
  margin: 0;
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
}

/* Filter Enhancements */
.filter-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--gp-spacing-md);
  padding-bottom: var(--gp-spacing-sm);
  border-bottom: 1px solid var(--gp-border-light);
}

.filter-title {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0;
  color: var(--gp-text-primary);
}

.filter-header-actions {
  display: flex;
  gap: var(--gp-spacing-sm);
  align-items: center;
}

.quick-presets {
  display: flex;
  gap: var(--gp-spacing-sm);
  margin-bottom: var(--gp-spacing-md);
  flex-wrap: wrap;
}

.advanced-filters {
  margin-top: var(--gp-spacing-md);
  padding-top: var(--gp-spacing-md);
  border-top: 1px solid var(--gp-border-light);
}

.filter-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: var(--gp-spacing-md);
  margin-bottom: var(--gp-spacing-md);
}

.filter-field {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
}

.range-inputs {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
}

.range-input {
  flex: 1;
}

.range-separator {
  color: var(--gp-text-muted);
  font-size: 0.9rem;
}

.filter-input {
  width: 100%;
}

.active-filter-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gp-spacing-sm);
  margin-top: var(--gp-spacing-md);
  padding-top: var(--gp-spacing-md);
  border-top: 1px solid var(--gp-border-light);
}

/* Filtered Banner */
.filtered-banner {
  margin-bottom: var(--gp-spacing-lg);
  background: var(--p-primary-50);
  border-left: 4px solid var(--gp-primary);
}

.filtered-banner-content {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-sm);
}

.filtered-banner-content i {
  color: var(--gp-primary);
  font-size: 1.2rem;
}

.filtered-text {
  font-weight: 500;
  color: var(--gp-text-primary);
}

/* Table Header Enhancements */
.table-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--gp-spacing-md);
  width: 100%;
}

.table-header-left {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  flex: 1;
  min-width: 0;
}

.table-header-right {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  flex-shrink: 0;
}

.page-size-label {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  white-space: nowrap;
}

.page-size-dropdown {
  min-width: 80px;
}

.filtered-info {
  color: var(--gp-text-muted);
  font-size: 0.85rem;
}

/* Selection Badge */
.selection-badge {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  padding: var(--gp-spacing-xs) var(--gp-spacing-sm);
  background: var(--p-primary-50);
  border-radius: var(--gp-radius-small);
}

.selection-text {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--gp-primary);
}

/* Mobile Responsive Filters */
@media (max-width: 768px) {
  .filter-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .filter-header-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .quick-presets {
    display: grid;
    grid-template-columns: 1fr 1fr;
    width: 100%;
  }

  .filter-row {
    grid-template-columns: 1fr;
  }

  .table-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .table-header-right {
    width: 100%;
    justify-content: space-between;
  }
}

/* Dark Mode Filters */
.p-dark .filter-header {
  border-color: var(--gp-border-dark);
}

.p-dark .advanced-filters {
  border-color: var(--gp-border-dark);
}

.p-dark .active-filter-chips {
  border-color: var(--gp-border-dark);
}

.p-dark .filtered-banner {
  background: var(--p-primary-900);
  border-color: var(--gp-primary);
}

.p-dark .filter-input :deep(.p-chip) {
  background: var(--p-surface-800) !important;
  color: var(--p-text-color) !important;
}

.p-dark .filter-input :deep(.p-chip .p-chip-remove-icon) {
  color: var(--p-text-color) !important;
}
</style>
