<template>
  <div class="debug-report">
    <!-- Summary -->
    <div class="report-summary" :class="`report-summary--${state}`">
      <i :class="summaryIcon"></i>
      <div>
        <h3 class="report-summary-title">{{ t(`data.debugImport.report.summary.${state}.title`) }}</h3>
        <p class="report-summary-text">
          {{ t(`data.debugImport.report.summary.${state}.text`, comparisonCounts) }}
        </p>
      </div>
      <Button
          v-if="timelineLink"
          :label="t('data.debugImport.report.openTimeline')"
          icon="pi pi-external-link"
          size="small"
          outlined
          class="report-summary-action"
          @click="$emit('open-timeline', timelineLink)"
      />
    </div>

    <!-- What the user reported -->
    <section v-if="report.issue?.description || report.issue?.time" class="report-section">
      <h4 class="report-section-title">{{ t('data.debugImport.report.issueTitle') }}</h4>
      <p v-if="report.issue?.time" class="report-issue-time">
        <i class="pi pi-clock"></i>
        {{ formatTime(report.issue.time) }}
      </p>
      <p v-if="report.issue?.description" class="report-issue-text">{{ report.issue.description }}</p>
    </section>

    <!-- Warnings -->
    <section v-if="warnings.length" class="report-section">
      <h4 class="report-section-title">{{ t('data.debugImport.report.warningsTitle') }}</h4>
      <ul class="report-warnings">
        <li v-for="warning in warnings" :key="warning.code" class="report-warning">
          <i class="pi pi-exclamation-triangle"></i>
          <span>{{ warningText(warning) }}</span>
        </li>
      </ul>
    </section>

    <!-- Comparison -->
    <section v-if="report.comparison?.available" class="report-section">
      <h4 class="report-section-title">{{ t('data.debugImport.report.comparisonTitle') }}</h4>
      <p class="report-section-help">
        {{ t('data.debugImport.report.comparisonWhyHelp') }}
      </p>
      <p class="report-section-help">
        {{ t('data.debugImport.report.comparisonHelp', {
          from: formatTime(report.comparison.from),
          to: formatTime(report.comparison.to)
        }) }}
      </p>
      <div class="report-table-wrapper">
        <table class="report-table">
          <thead>
          <tr>
            <th>{{ t('data.debugImport.report.columns.status') }}</th>
            <th>{{ t('data.debugImport.report.columns.event') }}</th>
            <th>{{ t('data.debugImport.report.columns.expected') }}</th>
            <th>{{ t('data.debugImport.report.columns.actual') }}</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="(row, index) in report.comparison.rows" :key="index" :class="`row--${row.status.toLowerCase()}`">
            <td>
              <Tag :value="t(`data.debugImport.report.status.${row.status}`)" :severity="statusSeverity(row.status)" />
            </td>
            <td class="report-event-type">{{ t(`data.debugImport.report.eventType.${(row.expected || row.actual).type}`) }}</td>
            <td>
              <EventCell v-if="row.expected" :event="row.expected" :highlight="differingFields(row)" :format-time="formatTime"
                         :show-why="row.status !== 'MATCH'" />
              <span v-else class="report-none">—</span>
            </td>
            <td>
              <EventCell v-if="row.actual" :event="row.actual" :highlight="differingFields(row)" :format-time="formatTime"
                         :show-why="row.status !== 'MATCH'" />
              <span v-else class="report-none">—</span>
            </td>
          </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- Details -->
    <section class="report-section">
      <h4 class="report-section-title">{{ t('data.debugImport.report.detailsTitle') }}</h4>
      <dl class="report-details">
        <dt>{{ t('data.debugImport.report.details.exportVersion') }}</dt>
        <dd>{{ report.exportVersion }}</dd>
        <dt>{{ t('data.debugImport.report.details.appVersions') }}</dt>
        <dd>{{ report.exporterAppVersion || '?' }} → {{ report.importerAppVersion || '?' }}</dd>
        <dt>{{ t('data.debugImport.report.details.exportDate') }}</dt>
        <dd>{{ formatTime(report.exportDate) }}</dd>
        <dt>{{ t('data.debugImport.report.details.timezone') }}</dt>
        <dd>
          {{ report.timezone || '?' }}
          <span v-if="report.timezone" class="report-muted">
            ({{ report.timezoneApplied ? t('data.debugImport.report.details.applied') : t('data.debugImport.report.details.notApplied') }})
          </span>
        </dd>
        <template v-if="report.requestedRange">
          <dt>{{ t('data.debugImport.report.details.requestedRange') }}</dt>
          <dd>{{ formatTime(report.requestedRange.start) }} – {{ formatTime(report.requestedRange.end) }}</dd>
        </template>
        <template v-if="report.exportedRange">
          <dt>{{ t('data.debugImport.report.details.exportedRange') }}</dt>
          <dd>{{ formatTime(report.exportedRange.start) }} – {{ formatTime(report.exportedRange.end) }}</dd>
        </template>
        <dt>{{ t('data.debugImport.report.details.imported') }}</dt>
        <dd>{{ t('data.debugImport.report.details.importedValue', report.imported || {}) }}</dd>
        <dt>{{ t('data.debugImport.report.details.timelineSettings') }}</dt>
        <dd>{{ report.timelineConfigApplied ? t('data.debugImport.report.details.applied') : t('data.debugImport.report.details.notApplied') }}</dd>
        <template v-if="report.water?.boatEnabled">
          <dt>{{ t('data.debugImport.report.details.water') }}</dt>
          <dd>{{ t('data.debugImport.report.details.waterValue', { count: report.water.flagsImported }) }}</dd>
        </template>
      </dl>
    </section>

    <section v-if="report.timelineStatus" class="report-section">
      <h4 class="report-section-title">{{ t('data.debugImport.report.timelineStatusTitle') }}</h4>
      <dl class="report-details">
        <dt>{{ t('data.debugImport.report.timelineStatus.lastBuilt') }}</dt>
        <dd>{{ formatTime(report.timelineStatus.last_built_at) || '?' }}</dd>
        <dt>{{ t('data.debugImport.report.timelineStatus.pointsAfterBuild') }}</dt>
        <dd>{{ report.timelineStatus.points_received_after_last_build ?? '?' }}</dd>
        <dt>{{ t('data.debugImport.report.timelineStatus.pendingRebuild') }}</dt>
        <dd>
          <template v-if="report.timelineStatus.pending_rebuild">
            {{ t('data.debugImport.report.timelineStatus.pendingFrom', {
              from: formatTime(report.timelineStatus.pending_rebuild.rebuild_from),
              attempts: report.timelineStatus.pending_rebuild.failed_attempts
            }) }}
            <div v-if="report.timelineStatus.pending_rebuild.last_error" class="report-error">
              {{ report.timelineStatus.pending_rebuild.last_error }}
            </div>
          </template>
          <template v-else>{{ t('data.debugImport.report.timelineStatus.none') }}</template>
        </dd>
      </dl>
      <div v-if="report.timelineStatus.regeneration_campaigns?.length" class="report-table-wrapper">
        <table class="report-table report-table--compact">
          <thead>
          <tr>
            <th>{{ t('data.debugImport.report.campaigns.reason') }}</th>
            <th>{{ t('data.debugImport.report.campaigns.status') }}</th>
            <th>{{ t('data.debugImport.report.campaigns.completed') }}</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="campaign in report.timelineStatus.regeneration_campaigns" :key="campaign.campaign_key">
            <td>{{ campaign.reason || campaign.campaign_key }}</td>
            <td>
              {{ campaign.status }}
              <div v-if="campaign.last_error" class="report-error">{{ campaign.last_error }}</div>
            </td>
            <td>{{ formatTime(campaign.completed_at) || '—' }}</td>
          </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="report.gpsSources?.length" class="report-section">
      <h4 class="report-section-title">{{ t('data.debugImport.report.gpsSourcesTitle') }}</h4>
      <p class="report-section-help">{{ t('data.debugImport.report.gpsSourcesHelp') }}</p>
      <div class="report-table-wrapper">
        <table class="report-table report-table--compact">
          <thead>
          <tr>
            <th>{{ t('data.debugImport.report.gpsSources.source') }}</th>
            <th>{{ t('data.debugImport.report.gpsSources.accuracyFilter') }}</th>
            <th>{{ t('data.debugImport.report.gpsSources.duplicateDetection') }}</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="(source, index) in report.gpsSources" :key="index" :class="{ 'row--inactive': !source.active }">
            <td>
              {{ source.source_type }}
              <span v-if="!source.active" class="report-muted">({{ t('data.debugImport.report.gpsSources.inactive') }})</span>
            </td>
            <td>
              <template v-if="source.filter_inaccurate_data">
                {{ t('data.debugImport.report.gpsSources.accuracyValue', {
                  accuracy: source.max_allowed_accuracy_meters ?? '—',
                  speed: source.max_allowed_speed_kmh ?? '—'
                }) }}
              </template>
              <template v-else>{{ t('data.debugImport.report.gpsSources.off') }}</template>
            </td>
            <td>
              <template v-if="source.duplicate_detection_enabled">
                {{ t('data.debugImport.report.gpsSources.duplicateValue', {
                  minutes: source.duplicate_detection_threshold_minutes ?? '—'
                }) }}
              </template>
              <template v-else>{{ t('data.debugImport.report.gpsSources.off') }}</template>
            </td>
          </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, defineComponent, h } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import { formatDistance, formatDuration } from '@/utils/calculationsHelpers'
import { explanationLines } from '@/utils/timelineExplanation'
import {
  comparisonState,
  differingFields,
  formatReportDuration,
  formatReportTime,
  sortedWarnings,
  timelineQuery
} from '@/utils/debugImportReport'

const props = defineProps({
  report: {
    type: Object,
    required: true
  }
})

defineEmits(['open-timeline'])

const { t, te } = useI18n()

const state = computed(() => comparisonState(props.report.comparison))
const summaryIcon = computed(() => ({
  match: 'pi pi-check-circle',
  differs: 'pi pi-exclamation-circle',
  unavailable: 'pi pi-info-circle'
})[state.value])
const comparisonCounts = computed(() => ({
  matching: props.report.comparison?.matching ?? 0,
  different: props.report.comparison?.different ?? 0,
  missing: props.report.comparison?.missing ?? 0,
  extra: props.report.comparison?.extra ?? 0
}))
const warnings = computed(() => sortedWarnings(props.report.warnings || []))
const timelineLink = computed(() => timelineQuery(props.report))

const formatTime = value => formatReportTime(value, props.report.timezone)

const statusSeverity = status => ({
  MATCH: 'success',
  DIFFERENT: 'warn',
  MISSING: 'danger',
  EXTRA: 'info'
})[status] || 'secondary'

const warningText = warning => {
  const params = { ...(warning.params || {}) }
  if (params.from) {
    params.from = formatTime(params.from) || params.from
  }
  return t(`data.debugImport.report.warnings.${warning.code}`, params)
}

// One stay, trip or gap: when it started and ended, how long it was, and for trips how it was travelled
const EventCell = defineComponent({
  props: {
    event: { type: Object, required: true },
    highlight: { type: Array, default: () => [] },
    formatTime: { type: Function, required: true },
    // Why each server's engine decided on the event, for rows that differ
    showWhy: { type: Boolean, default: false }
  },
  setup(cellProps) {
    const mark = (field, text) => h('span', {
      class: cellProps.highlight.includes(field) ? 'report-diff' : undefined
    }, text)
    return () => {
      const event = cellProps.event
      const lines = [
        h('div', { class: 'report-event-time' }, [
          cellProps.formatTime(event.start),
          ' – ',
          event.end ? mark('end', cellProps.formatTime(event.end)) : t('data.debugImport.report.ongoing')
        ])
      ]
      const details = []
      if (event.durationSeconds !== null && event.durationSeconds !== undefined) {
        details.push(mark('durationSeconds', formatReportDuration(event.durationSeconds)))
      }
      if (event.movementType) {
        details.push(' · ', mark('movementType', event.movementType))
      }
      if (details.length) {
        lines.push(h('div', { class: 'report-event-details' }, details))
      }
      if (cellProps.showWhy) {
        const why = explanationLines(event.explanation, {
          t, te, formatDuration, formatDistance, formatTime: cellProps.formatTime
        })
        if (why.length) {
          lines.push(h('ul', { class: 'report-event-why' }, why.map(line => h('li', line))))
        }
      }
      return h('div', lines)
    }
  }
})
</script>

<style scoped>
.debug-report {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.report-summary {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  padding: 1rem 1.25rem;
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-subtle);
  border-left: 4px solid var(--gp-border-medium);
  background: var(--gp-surface-muted);
}

.report-summary--match {
  border-left-color: var(--gp-success);
}

.report-summary--differs {
  border-left-color: var(--gp-warning);
}

.report-summary > i {
  font-size: 1.5rem;
  margin-top: 0.15rem;
}

.report-summary--match > i {
  color: var(--gp-success);
}

.report-summary--differs > i {
  color: var(--gp-warning);
}

.report-summary-title {
  margin: 0 0 0.25rem;
  font-size: 1.1rem;
  font-weight: 600;
}

.report-summary-text {
  margin: 0;
  color: var(--gp-text-secondary);
}

.report-summary-action {
  margin-left: auto;
  flex-shrink: 0;
}

.report-section-title {
  margin: 0 0 0.5rem;
  font-size: 1rem;
  font-weight: 600;
}

.report-section-help {
  margin: 0 0 0.75rem;
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
}

.report-issue-time {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0 0 0.5rem;
  font-weight: 500;
}

.report-issue-text {
  margin: 0;
  white-space: pre-wrap;
  padding: 0.75rem 1rem;
  border-radius: var(--gp-radius-small);
  background: var(--gp-surface-muted);
}

.report-warnings {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.report-warning {
  display: flex;
  gap: 0.5rem;
  align-items: flex-start;
}

.report-warning i {
  color: var(--gp-warning);
  margin-top: 0.2rem;
}

.report-table-wrapper {
  overflow-x: auto;
}

.report-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.report-table th,
.report-table td {
  text-align: left;
  padding: 0.5rem 0.75rem;
  border-bottom: 1px solid var(--gp-border-subtle);
  vertical-align: top;
}

.report-table th {
  font-weight: 600;
  color: var(--gp-text-secondary);
  white-space: nowrap;
}

.report-table--compact td,
.report-table--compact th {
  padding: 0.35rem 0.6rem;
}

.report-event-type {
  white-space: nowrap;
}

.report-event-time {
  white-space: nowrap;
}

.report-event-details {
  color: var(--gp-text-secondary);
}

.report-event-why {
  margin: 0.35rem 0 0;
  padding-left: 1rem;
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
}

.report-diff {
  font-weight: 600;
  color: var(--gp-warning-text);
}

.report-none,
.report-muted {
  color: var(--gp-text-secondary);
}

.row--inactive {
  opacity: 0.6;
}

.report-error {
  margin-top: 0.25rem;
  color: var(--gp-danger-text);
  font-size: 0.85rem;
  word-break: break-word;
}

.report-details {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 0.35rem 1rem;
  margin: 0;
}

.report-details dt {
  color: var(--gp-text-secondary);
}

.report-details dd {
  margin: 0;
}

@media (max-width: 640px) {
  .report-summary {
    flex-wrap: wrap;
  }

  .report-summary-action {
    margin-left: 0;
  }

  .report-details {
    grid-template-columns: 1fr;
  }

  .report-details dd {
    margin-bottom: 0.5rem;
  }
}
</style>
