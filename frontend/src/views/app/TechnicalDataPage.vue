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

    <GpsPointFiltersPanel :filters-state="gpsPointFilters" />

    <TelemetryMappingSection />

    <!-- Filtered Results Banner -->
    <BaseCard v-if="hasActiveFilters && filteredRecordsText" class="filtered-banner">
      <div class="filtered-banner-content">
        <i class="pi pi-filter"></i>
        <span class="filtered-text">{{ filteredRecordsText }}</span>
      </div>
    </BaseCard>

    <!-- Staged GPS point edits, applied together -->
    <Message v-if="hasPendingEdits" severity="warn" :closable="false" class="pending-edits-banner">
      <div class="pending-edits-content">
        <div class="pending-edits-text">
          <i class="pi pi-pencil"></i>
          <span>{{ t('technicalData.page.pendingEdits.message', { count: pendingCount }, pendingCount) }}</span>
        </div>
        <div class="pending-edits-actions">
          <Button
            :label="t('technicalData.page.pendingEdits.revertAll')"
            icon="pi pi-undo"
            size="small"
            severity="secondary"
            outlined
            :disabled="applyEditsLoading"
            @click="revertAllPendingEdits"
          />
          <Button
            :label="t('technicalData.page.pendingEdits.apply')"
            icon="pi pi-check"
            size="small"
            :loading="applyEditsLoading"
            @click="applyPendingEdits"
          />
        </div>
      </div>
    </Message>

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
        :row-class="pendingRowClass"
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
          <div class="gp-empty-state">
            <i class="pi pi-map-marker gp-empty-state-icon"></i>
            <h3 class="gp-empty-state-title">{{ t('technicalData.page.table.emptyTitle') }}</h3>
            <p class="gp-empty-state-message">{{ t('technicalData.page.table.emptyDescription') }}</p>
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
            <div class="timestamp-cell" :title="timezone.getLocationTimezoneHint(slotProps.data.locationTimezone) || undefined">
              <span class="timestamp-date">{{ formatTimestamp(slotProps.data.timestamp, slotProps.data.locationTimezone).date }}</span>
              <span class="timestamp-time">{{ formatTimestamp(slotProps.data.timestamp, slotProps.data.locationTimezone).time }}</span>
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
                v-if="slotProps.data.pendingEdit"
                icon="pi pi-undo"
                severity="warn"
                size="small"
                text
                :disabled="applyEditsLoading"
                @click="revertPendingEdit(slotProps.data.id)"
                v-tooltip.top="t('technicalData.page.pendingEdits.revertTooltip')"
                class="action-button revert-button"
              />
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
              :class="pendingRowClass(point)"
              role="listitem"
            >
              <Checkbox
                :model-value="isRowSelected(point)"
                @change="handleCheckboxChange($event, point)"
                binary
                class="mobile-gps-checkbox"
                :aria-label="t('technicalData.page.mobile.selectPointAriaLabel', { date: formatTimestamp(point.timestamp, point.locationTimezone).date, time: formatTimestamp(point.timestamp, point.locationTimezone).time })"
              />
              <div class="mobile-gps-main">
                <div class="mobile-gps-primary" :title="timezone.getLocationTimezoneHint(point.locationTimezone) || undefined">
                  <span class="mobile-gps-date">{{ formatTimestamp(point.timestamp, point.locationTimezone).date }}</span>
                  <span class="mobile-gps-time">{{ formatTimestamp(point.timestamp, point.locationTimezone).time }}</span>
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
                :aria-label="t('technicalData.page.mobile.actionsAriaLabel', { date: formatTimestamp(point.timestamp, point.locationTimezone).date, time: formatTimestamp(point.timestamp, point.locationTimezone).time })"
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

        <div v-else class="gp-empty-state mobile-empty-state">
          <i class="pi pi-map-marker gp-empty-state-icon"></i>
          <h3 class="gp-empty-state-title">{{ t('technicalData.page.table.emptyTitle') }}</h3>
          <p class="gp-empty-state-message">{{ t('technicalData.page.table.emptyDescription') }}</p>
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
      :pending-changes="selectedGpsPoint ? getPendingChanges(selectedGpsPoint.id) : null"
      @close="showEditDialog = false"
      @save="handleEditSave"
      @revert="handleEditRevert"
    />
    <ConfirmDialog :group="unsavedEditsConfirmGroup" />
    </PullToRefreshIndicator>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { useI18n } from 'vue-i18n'
import { useTechnicalDataStore } from '@/stores/technicalData'
import { useTimezone } from '@/composables/useTimezone'
import { usePullToRefresh } from '@/composables/usePullToRefresh'
import { useGpsPointFilters } from '@/composables/useGpsPointFilters'
import { usePendingGpsEdits } from '@/composables/usePendingGpsEdits'
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
import GpsPointFiltersPanel from '@/components/technical-data/GpsPointFiltersPanel.vue'
import TelemetryMappingSection from '@/components/technical-data/TelemetryMappingSection.vue'

// PrimeVue
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import Badge from 'primevue/badge'
import Dropdown from 'primevue/dropdown'
import Checkbox from 'primevue/checkbox'
import Menu from 'primevue/menu'
import Paginator from 'primevue/paginator'
import Message from 'primevue/message'
import ConfirmDialog from 'primevue/confirmdialog'
import {formatDistance, formatSpeed} from "../../utils/calculationsHelpers";

// Store and utils
const technicalDataStore = useTechnicalDataStore()
const toast = useToast()
const confirm = useConfirm()
const route = useRoute()
const router = useRouter()
const gpsPointFilters = useGpsPointFilters({ onChange: () => refreshAfterFilterChange() })
const { hasActiveFilters, buildFilterParams } = gpsPointFilters

// Reactive state
const isMobile = ref(false)
const isTablet = ref(false)
const pageSize = ref(50)
const currentPage = ref(0)
const sortField = ref('timestamp')
const sortOrder = ref(-1) // -1 for descending, 1 for ascending

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

// Staged edits, applied together in one request
const {
  pendingCount,
  hasPendingEdits,
  isPending,
  getChanges: getPendingChanges,
  getOriginal: getOriginalGpsPoint,
  stage: stageGpsPointEdit,
  revert: revertPendingEdit,
  revertAll: revertAllPendingEdits,
  dropIds: dropPendingEdits,
  applyTo: applyPendingEdit,
  toRequestEdits: pendingEditsRequest
} = usePendingGpsEdits()
const applyEditsLoading = ref(false)
const unsavedEditsConfirmGroup = 'gps-data-unsaved-changes'

// Delete all states
const showDeleteAllDialog = ref(false)
const deleteAllLoading = ref(false)

// Shift-click selection tracking
const lastSelectedIndex = ref(null)
// Computed properties
const summaryStats = computed(() => technicalDataStore.summaryStats)
const gpsPoints = computed(() => technicalDataStore.gpsPoints)
const totalRecords = computed(() => technicalDataStore.totalRecords)
const gpsPointsWithDelta = computed(() =>
  gpsPoints.value.map((point, index, points) => ({
    ...applyPendingEdit(point),
    timeDeltaDisplay: formatTimeDelta(point.timestamp, points[index - 1]?.timestamp)
  }))
)

const pendingRowClass = (row) => (row.pendingEdit ? 'pending-edit-row' : '')

const hasData = computed(() => summaryStats.value.totalPoints > 0)
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

const mobileActionMenuItems = computed(() => [
  ...(mobileActionPoint.value && isPending(mobileActionPoint.value.id) ? [{
    label: t('technicalData.page.pendingEdits.revertTooltip'),
    icon: 'pi pi-undo',
    command: () => {
      if (mobileActionPoint.value) {
        revertPendingEdit(mobileActionPoint.value.id)
      }
    }
  }] : []),
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

const formatTimestamp = (timestamp, locationTimezone) => {
  if (!timestamp) return { date: '-', time: '-' }
  return {
    date: timezone.formatDateDisplayAt(timestamp, locationTimezone),
    time: timezone.formatTimeAt(timestamp, locationTimezone, { withSeconds: true })
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

    // Only ask for per-point timezones in the opt-in "location" mode; the default request is unchanged.
    if (timezone.isLocationTimeMode()) {
      params.includeLocationTimezones = true
    }

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
  // The dialog compares against the server values, not the staged ones shown in the row
  selectedGpsPoint.value = getOriginalGpsPoint(gpsPoint)
  showEditDialog.value = true
}

const deleteGpsPoint = (gpsPoint) => {
  selectedGpsPoint.value = gpsPoint
  showDeleteDialog.value = true
}

const getResultData = (result) => result?.data || result || {}

const buildRefreshSuccessDetail = (baseMessage, result) => {
  const data = getResultData(result)
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
    dropPendingEdits([selectedGpsPoint.value.id])
    const successDetail = buildRefreshSuccessDetail(t('technicalData.page.toasts.pointDeletedDetail'), result)

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

const closeEditDialog = () => {
  showEditDialog.value = false
  selectedGpsPoint.value = null
}

const handleEditSave = (updatedData) => {
  if (selectedGpsPoint.value) {
    stageGpsPointEdit(selectedGpsPoint.value, updatedData)
  }
  closeEditDialog()
}

const handleEditRevert = () => {
  if (selectedGpsPoint.value) {
    revertPendingEdit(selectedGpsPoint.value.id)
  }
  closeEditDialog()
}

const applyPendingEdits = async () => {
  if (!hasPendingEdits.value || applyEditsLoading.value) return

  applyEditsLoading.value = true

  try {
    const result = await technicalDataStore.updateGpsPoints(pendingEditsRequest())
    const updatedCount = getResultData(result).updatedCount ?? pendingCount.value
    const successDetail = buildRefreshSuccessDetail(
      t('technicalData.page.toasts.pointsUpdatedDetail', { count: updatedCount }, updatedCount),
      result
    )

    revertAllPendingEdits()
    toast.add({
      group: 'gps-delete',
      severity: 'success',
      summary: t('technicalData.page.toasts.pointsUpdatedSummary'),
      detail: successDetail.detail,
      data: {
        timelineJobUrl: successDetail.timelineJobUrl
      },
      life: 10000
    })

    await Promise.all([loadGPSPoints(), loadSummaryStats()])
  } catch (error) {
    console.error('Error updating GPS points:', error)
    const errorMessage = formatApiErrorDetail(error, t('technicalData.page.toasts.updateFailedFallback'))
    toast.add({
      severity: 'error',
      summary: t('technicalData.page.toasts.updateFailedSummary'),
      detail: errorMessage,
      life: 5000
    })
  } finally {
    applyEditsLoading.value = false
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
    dropPendingEdits(pointIds)
    const deleteData = getResultData(result)

    // Extract the correct count from the response
    const deletedCount = deleteData.deletedCount ?? pointIds.length
    const successDetail = buildRefreshSuccessDetail(
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
    revertAllPendingEdits()

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
onBeforeRouteLeave((to, from, next) => {
  if (to.path === from.path || !hasPendingEdits.value) {
    next()
    return
  }

  let guardResolved = false
  const resolveGuard = (allowNavigation) => {
    if (guardResolved) {
      return
    }

    guardResolved = true
    if (allowNavigation) {
      next()
    } else {
      next(false)
    }
  }

  confirm.require({
    group: unsavedEditsConfirmGroup,
    message: t('technicalData.page.pendingEdits.leaveConfirm.message', { count: pendingCount.value }, pendingCount.value),
    header: t('technicalData.page.pendingEdits.leaveConfirm.header'),
    icon: 'pi pi-exclamation-triangle',
    acceptLabel: t('technicalData.page.pendingEdits.leaveConfirm.accept'),
    rejectLabel: t('technicalData.page.pendingEdits.leaveConfirm.reject'),
    acceptClass: 'p-button-danger',
    rejectClass: 'p-button-secondary p-button-outlined',
    accept: () => resolveGuard(true),
    reject: () => resolveGuard(false),
    onHide: () => resolveGuard(false)
  })
})

const handleBeforeUnload = (event) => {
  if (!hasPendingEdits.value) {
    return
  }

  event.preventDefault()
  event.returnValue = ''
  return ''
}

onMounted(async () => {
  handleResize()
  window.addEventListener('resize', handleResize)
  window.addEventListener('beforeunload', handleBeforeUnload)
  gpsPointFilters.hydrateFromRouteQuery(route.query)
  
  await Promise.all([
    loadSummaryStats(),
    loadGPSPoints()
  ])
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

// Watchers
watch(pageSize, async () => {
  currentPage.value = 0
  await loadGPSPoints()
})
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

/* Table Section */
.table-section {
  overflow: hidden;
}

.gps-data-table :deep(.p-datatable-table) {
  width: 100%;
  min-width: 80rem;
  table-layout: fixed;
}

/* Paginator under the table, in the header colour (slate-50 / slate-900). The base rules come before the
   media queries below so the phone sizes win by source order. */
.gps-data-table :deep(.p-datatable-paginator-bottom) {
  background: var(--gp-surface-ground);
  border: 1px solid var(--gp-border);
  border-radius: 0;
  overflow: hidden;
}

.gps-data-table :deep(.p-paginator .p-paginator-pages) {
  display: flex;
  align-items: center;
  gap: 4px;
}

.gps-data-table :deep(.p-paginator .p-paginator-page) {
  min-width: 2.5rem;
  height: 2.5rem;
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

  .gps-data-table :deep(.p-datatable-table) {
    width: max-content;
    min-width: unset;
    table-layout: auto;
  }

  /* Mobile paginator optimizations */
  .gps-data-table :deep(.p-paginator) {
    flex-wrap: nowrap;
    justify-content: center;
    gap: 4px;
    padding: var(--gp-spacing-sm);
  }

  .gps-data-table :deep(.p-paginator .p-paginator-page),
  .gps-data-table :deep(.p-paginator .p-paginator-next),
  .gps-data-table :deep(.p-paginator .p-paginator-prev),  
  .gps-data-table :deep(.p-paginator .p-paginator-first),
  .gps-data-table :deep(.p-paginator .p-paginator-last) {
    min-width: 32px;
    width: 32px;
    height: 32px;
    padding: 0;
    margin: 0 1px;
    font-size: 0.8rem;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  /* Hide some pagination elements on very small screens */
  .gps-data-table :deep(.p-paginator .p-paginator-first),
  .gps-data-table :deep(.p-paginator .p-paginator-last) {
    display: none;
  }

  /* Show fewer page numbers on mobile */
  .gps-data-table :deep(.p-paginator .p-paginator-page:nth-child(n+8)) {
    display: none;
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
  font-family: var(--gp-font-mono);
}

.coordinates-cell {
  display: flex;
  align-items: baseline;
  gap: 0.25rem;
  white-space: nowrap;
}

.coordinate-line {
  font-family: var(--gp-font-mono);
  font-size: 0.8rem;
  color: var(--gp-text-primary);
}

.coordinate-separator {
  color: var(--gp-text-muted);
  font-family: var(--gp-font-mono);
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

/* Mobile GPS List */
.mobile-gps-list-panel {
  width: 100%;
  min-width: 0;
  overflow: hidden;
  background: var(--gp-surface-card);
}

.mobile-gps-header {
  padding: var(--gp-spacing-lg);
  border-bottom: 1px solid var(--gp-border);
}

.mobile-gps-toolbar {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: var(--gp-spacing-md);
  padding: var(--gp-spacing-md) var(--gp-spacing-lg);
  border-bottom: 1px solid var(--gp-border);
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

/* `.p-button.` outranks the global phone padding on `.p-button.p-button-sm` (primevue-overrides.css). */
.p-button.mobile-sort-direction-button,
.p-button.mobile-gps-actions-button {
  width: 2.25rem;
  height: 2.25rem;
  min-width: 2.25rem;
  padding: 0;
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
  border-bottom: 1px solid var(--gp-border);
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
  font-family: var(--gp-font-mono);
  font-size: 0.85rem;
  color: var(--gp-text-muted);
}

.mobile-gps-coordinates {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--gp-font-mono);
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
  background: var(--gp-surface-muted);
  color: var(--gp-text-secondary);
  font-size: 0.78rem;
  line-height: 1.25;
}

.mobile-gps-metric i {
  font-size: 0.75rem;
}

.mobile-gps-paginator {
  border-top: 1px solid var(--gp-border);
}

.mobile-empty-state {
  padding: var(--gp-spacing-xl) var(--gp-spacing-lg);
}

/* Responsive Design */
@media (max-width: 768px) {
  /* Wider side padding than PageContainer's phone default. The PageContainer root carries this page's scope id;
     the doubled class outranks PageContainer's own single-class rules. */
  .gp-page-container.gp-page-container--fullwidth {
    padding-left: var(--gp-spacing-md);
    padding-right: var(--gp-spacing-md);
    max-width: 100vw;
    overflow-x: hidden;
    box-sizing: border-box;
  }

  /* Override page content container */
  :deep(.gp-page-content) {
    overflow-x: hidden;
    box-sizing: border-box;
  }

  /* Force all content to respect viewport width */
  .stats-grid,
  .stat-card,
  .filter-section,
  .table-section {
    max-width: 100%;
    box-sizing: border-box;
    margin-left: 0;
    margin-right: 0;
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

  /* Ensure datepicker component fits */
  .filter-section :deep(.p-datepicker) {
    width: 100%;
    max-width: 100%;
  }

  .filter-section :deep(.p-inputtext) {
    width: 100%;
    max-width: 100%;
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
    flex-wrap: wrap;
    justify-content: center;
    gap: 4px;
    padding: var(--gp-spacing-sm);
  }

  .mobile-gps-paginator :deep(.p-paginator .p-paginator-page),
  .mobile-gps-paginator :deep(.p-paginator .p-paginator-next),
  .mobile-gps-paginator :deep(.p-paginator .p-paginator-prev),
  .mobile-gps-paginator :deep(.p-paginator .p-paginator-first),
  .mobile-gps-paginator :deep(.p-paginator .p-paginator-last) {
    min-width: 32px;
    width: 32px;
    height: 32px;
    padding: 0;
    margin: 0 1px;
    font-size: 0.8rem;
  }

  .mobile-gps-paginator :deep(.p-paginator .p-paginator-first),
  .mobile-gps-paginator :deep(.p-paginator .p-paginator-last),
  .mobile-gps-paginator :deep(.p-paginator-current) {
    display: none;
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

  .p-button.mobile-sort-direction-button,
  .p-button.mobile-gps-actions-button {
    width: 2rem;
    height: 2rem;
    min-width: 2rem;
  }

  /* Extra mobile paginator optimizations for very small screens */
  .gps-data-table :deep(.p-paginator) {
    gap: 2px;
    padding: var(--gp-spacing-xs);
  }

  .gps-data-table :deep(.p-paginator .p-paginator-page),
  .gps-data-table :deep(.p-paginator .p-paginator-next),
  .gps-data-table :deep(.p-paginator .p-paginator-prev) {
    min-width: 28px;
    width: 28px;
    height: 28px;
    font-size: 0.75rem;
  }

  /* Show even fewer elements on very small screens */
  .gps-data-table :deep(.p-paginator .p-paginator-page:nth-child(n+6)) {
    display: none;
  }

  .mobile-gps-paginator :deep(.p-paginator .p-paginator-page:nth-child(n+6)) {
    display: none;
  }
}

.paginator-info {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  padding: 0 0.5rem;
}

/* Actions Column */
.pending-edits-banner {
  margin-bottom: var(--gp-spacing-lg);
}

.pending-edits-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--gp-spacing-md);
  width: 100%;
}

.pending-edits-text {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
}

.pending-edits-actions {
  display: flex;
  gap: var(--gp-spacing-sm);
  flex-shrink: 0;
}

.gps-data-table :deep(.pending-edit-row > td:first-child),
.mobile-gps-row.pending-edit-row {
  box-shadow: inset 3px 0 0 var(--gp-warning);
}

.gps-data-table :deep(.pending-edit-row),
.mobile-gps-row.pending-edit-row {
  background: var(--gp-warning-soft);
}

@media (max-width: 768px) {
  .pending-edits-content {
    flex-direction: column;
    align-items: stretch;
  }

  .pending-edits-actions .p-button {
    flex: 1;
    min-height: 44px;
  }
}

.actions-buttons {
  display: flex;
  gap: var(--gp-spacing-xs);
  justify-content: center;
  align-items: center;
}

/* `.p-button.` outranks the global phone padding on `.p-button.p-button-sm` (primevue-overrides.css). The hover tint
   is PrimeVue's text-button hover for each button's severity. */
.p-button.action-button {
  min-width: 32px;
  width: 32px;
  height: 32px;
  padding: 0;
  border-radius: var(--gp-radius-small);
  transition: all 0.2s ease;
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

  .p-button.action-button {
    min-width: 28px;
    width: 28px;
    height: 28px;
    font-size: 0.75rem;
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

/* Filtered Banner */
.filtered-banner {
  margin-bottom: var(--gp-spacing-lg);
  background: var(--gp-primary-soft);
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
  .table-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .table-header-right {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
