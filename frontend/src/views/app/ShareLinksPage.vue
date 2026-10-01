<template>
  <AppLayout>
    <PageContainer>
      <div class="share-links-page">

        <!-- Page Header -->
        <div class="gp-page-header" v-if="shareLinksStore.links.length !== 0 || shareLinksStore.isLoading">
          <div class="gp-page-header-content">
            <div class="gp-page-header-text">
              <h1 class="gp-page-title">{{ t('sharing.shareLinksPage.title') }}</h1>
              <p class="gp-page-subtitle">
                {{ t('sharing.shareLinksPage.description') }}
              </p>
            </div>
            <div class="gp-page-actions">
              <Button
                  :label="t('sharing.shareLinksPage.createNew')"
                  icon="pi pi-plus"
                  @click="(event) => menu.toggle(event)"
                  class="create-link-btn"
                  aria-haspopup="true"
                  aria-controls="create_menu"
              />
            </div>
          </div>
        </div>

        <!-- Menu rendered unconditionally so it's available in empty state too -->
        <Menu ref="menu" id="create_menu" :model="createMenuItems" :popup="true" />
        <Menu ref="copyMenu" id="link_copy_menu" :model="copyMenuItems" :popup="true" />

        <!-- Share Links Content -->
        <div class="share-links-content">
          <!-- Loading State -->
          <div v-if="shareLinksStore.isLoading && shareLinksStore.links.length === 0"
               class="loading-state">
            <ProgressSpinner/>
            <p>{{ t('sharing.shareLinksPage.loading') }}</p>
          </div>

          <!-- Error State -->
          <Message v-if="shareLinksStore.getError"
                   severity="error"
                   :closable="true"
                   @close="shareLinksStore.clearError()">
            {{ formatApiErrorDetail(shareLinksStore.getError) }}
          </Message>

          <!-- Links List -->
          <div v-if="!shareLinksStore.isLoading || shareLinksStore.links.length > 0"
               class="links-container">

            <!-- Timeline Shares Section -->
            <div v-if="activeTimelineShares.length > 0 || expiredTimelineShares.length > 0" class="share-type-section">
              <h2 class="type-title">
                <i class="pi pi-calendar"></i>
                {{ t('sharing.shareLinksPage.timelineSharesHeader') }}
              </h2>

              <!-- Active Timeline Links -->
              <div v-if="activeTimelineShares.length > 0" class="links-section">
                <h3 class="section-subtitle">{{ t('sharing.shareLinksPage.activeCount', { count: activeTimelineShares.length }) }}</h3>
                <div class="links-grid">
                <Card v-for="link in activeTimelineShares"
                      :key="link.id"
                      class="link-card active timeline-card">
                  <template #content>
                    <div class="link-header">
                      <div class="link-info">
                        <h3 class="link-title">{{ link.name || t('sharing.shareLinksPage.untitledLink') }}</h3>
                        <div class="link-meta">
                          <span class="link-date">{{ t('sharing.shareLinksPage.createdOn', { date: formatDate(link.created_at) }) }}</span>
                          <span class="link-expires">{{ t('sharing.shareLinksPage.expiresOn', { date: formatDate(link.expires_at) }) }}</span>
                        </div>
                      </div>
                      <div class="link-status">
                        <Tag severity="success" :value="t('sharing.shareLinksPage.statusActive')"/>
                      </div>
                    </div>

                    <div class="link-details">
                      <div class="link-url-section">
                        <label class="url-label">{{ t('sharing.shareLinksPage.shareUrlLabel') }}</label>
                        <div class="url-input-group">
                          <InputText
                              :value="getShareUrl(link)"
                              readonly
                              class="share-url-input"
                          />
                          <Button
                              icon="pi pi-copy"
                              @click="openCopyMenu($event, link)"
                              class="copy-btn"
                              aria-haspopup="true"
                              aria-controls="link_copy_menu"
                              v-tooltip="t('sharing.shareLinksPage.copyLinkTooltip')"
                          />
                        </div>
                      </div>

                      <div class="link-settings">
                        <div class="setting-item">
                          <span class="setting-label">{{ t('sharing.shareLinksPage.typeLabel') }}</span>
                          <span class="setting-value">{{ link.share_type === 'TIMELINE' ? t('sharing.shareLinksPage.typeTimeline') : t('sharing.shareLinksPage.typeLiveLocation') }}</span>
                        </div>
                        <div class="setting-item">
                          <span class="setting-label">{{ t('sharing.shareLinksPage.passwordProtectedLabel') }}</span>
                          <span class="setting-value">{{ link.has_password ? t('sharing.shareLinksPage.yes') : t('sharing.shareLinksPage.no') }}</span>
                        </div>
                        <div class="setting-item">
                          <span class="setting-label">{{ t('sharing.shareLinksPage.viewCountLabel') }}</span>
                          <span class="setting-value">{{ link.view_count || 0 }}</span>
                        </div>
                        <div v-if="link.share_type !== 'TIMELINE'" class="setting-item">
                          <span class="setting-label">{{ t('sharing.shareLinksPage.showHistoryLabel') }}</span>
                          <span class="setting-value">{{ formatShowHistory(link) }}</span>
                        </div>
                      </div>

                      <!-- Timeline-specific info -->
                      <div v-if="link.share_type === 'TIMELINE'" class="timeline-info">
                        <div class="setting-item">
                          <span class="setting-label">{{ t('sharing.shareLinksPage.dateRangeLabel') }}</span>
                          <span class="setting-value">
                            {{ t('sharing.shareLinksPage.dateRangeValue', { start: formatDate(link.start_date), end: formatDate(link.end_date) }) }}
                          </span>
                        </div>
                        <div class="setting-item">
                          <span class="setting-label">{{ t('sharing.shareLinksPage.statusLabel') }}</span>
                          <Tag :value="link.timeline_status"
                               :severity="getTimelineStatusSeverity(link.timeline_status)" />
                        </div>
                        <div v-if="getShareLinkedTrip(link)" class="setting-item trip-workspace-setting">
                          <span class="setting-label">{{ t('sharing.shareLinksPage.tripPlannerLabel') }}</span>
                          <span class="setting-value trip-workspace-value">
                            <Button
                              :label="getShareLinkedTripLabel(getShareLinkedTrip(link))"
                              icon="pi pi-compass"
                              outlined
                              size="small"
                              class="trip-workspace-btn"
                              @click="openTripWorkspace(getShareLinkedTrip(link))"
                            />
                          </span>
                        </div>
                      </div>
                    </div>

                    <div class="link-actions">
                      <Button
                          :label="t('sharing.shareLinksPage.edit')"
                          icon="pi pi-pencil"
                          severity="secondary"
                          @click="editLink(link)"
                          class="edit-btn"
                      />
                      <Button
                          :label="t('sharing.shareLinksPage.delete')"
                          icon="pi pi-trash"
                          severity="danger"
                          @click="confirmDeleteLink(link)"
                          class="delete-btn"
                      />
                    </div>
                  </template>
                </Card>
              </div>
            </div>

            <!-- Expired Timeline Links -->
            <div v-if="expiredTimelineShares.length > 0" class="links-section">
              <h3 class="section-subtitle">{{ t('sharing.shareLinksPage.expiredCount', { count: expiredTimelineShares.length }) }}</h3>
              <div class="links-grid">
                <Card v-for="link in expiredTimelineShares"
                      :key="link.id"
                      class="link-card expired timeline-card">
                  <template #content>
                    <div class="link-header">
                      <div class="link-info">
                        <h3 class="link-title">{{ link.name || t('sharing.shareLinksPage.untitledLink') }}</h3>
                        <div class="link-meta">
                          <span class="link-date">{{ t('sharing.shareLinksPage.createdOn', { date: formatDate(link.created_at) }) }}</span>
                          <span class="link-expires">{{ t('sharing.shareLinksPage.expiredOn', { date: formatDate(link.expires_at) }) }}</span>
                        </div>
                      </div>
                      <div class="link-status">
                        <Tag severity="danger" :value="t('sharing.shareLinksPage.statusExpired')"/>
                      </div>
                    </div>

                    <div class="link-actions">
                      <Button
                          :label="t('sharing.shareLinksPage.delete')"
                          icon="pi pi-trash"
                          severity="danger"
                          @click="confirmDeleteLink(link)"
                          class="delete-btn"
                      />
                    </div>
                  </template>
                </Card>
              </div>
            </div>
            </div>

            <!-- Live Location Shares Section -->
            <div v-if="activeLiveLocationShares.length > 0 || expiredLiveLocationShares.length > 0" class="share-type-section">
              <h2 class="type-title">
                <i class="pi pi-map-marker"></i>
                {{ t('sharing.shareLinksPage.liveLocationSharesHeader') }}
              </h2>

              <!-- Active Live Location Links -->
              <div v-if="activeLiveLocationShares.length > 0" class="links-section">
                <h3 class="section-subtitle">{{ t('sharing.shareLinksPage.activeCount', { count: activeLiveLocationShares.length }) }}</h3>
                <div class="links-grid">
                  <Card v-for="link in activeLiveLocationShares"
                        :key="link.id"
                        class="link-card active live-location-card">
                    <template #content>
                      <div class="link-header">
                        <div class="link-info">
                          <h3 class="link-title">{{ link.name || t('sharing.shareLinksPage.untitledLink') }}</h3>
                          <div class="link-meta">
                            <span class="link-date">{{ t('sharing.shareLinksPage.createdOn', { date: formatDate(link.created_at) }) }}</span>
                            <span class="link-expires">{{ t('sharing.shareLinksPage.expiresOn', { date: formatDate(link.expires_at) }) }}</span>
                          </div>
                        </div>
                        <div class="link-status">
                          <Tag severity="success" :value="t('sharing.shareLinksPage.statusActive')"/>
                        </div>
                      </div>

                      <div class="link-details">
                        <div class="link-url-section">
                          <label class="url-label">{{ t('sharing.shareLinksPage.shareUrlLabel') }}</label>
                          <div class="url-input-group">
                            <InputText
                                :value="getShareUrl(link)"
                                readonly
                                class="share-url-input"
                            />
                            <Button
                                icon="pi pi-copy"
                                @click="openCopyMenu($event, link)"
                                class="copy-btn"
                                aria-haspopup="true"
                                aria-controls="link_copy_menu"
                                v-tooltip="t('sharing.shareLinksPage.copyLinkTooltip')"
                            />
                          </div>
                        </div>

                        <div class="link-settings">
                          <div class="setting-item">
                            <span class="setting-label">{{ t('sharing.shareLinksPage.typeLabel') }}</span>
                            <span class="setting-value">{{ t('sharing.shareLinksPage.typeLiveLocation') }}</span>
                          </div>
                          <div class="setting-item">
                            <span class="setting-label">{{ t('sharing.shareLinksPage.passwordProtectedLabel') }}</span>
                            <span class="setting-value">{{ link.has_password ? t('sharing.shareLinksPage.yes') : t('sharing.shareLinksPage.no') }}</span>
                          </div>
                          <div class="setting-item">
                            <span class="setting-label">{{ t('sharing.shareLinksPage.viewCountLabel') }}</span>
                            <span class="setting-value">{{ link.view_count || 0 }}</span>
                          </div>
                          <div class="setting-item">
                            <span class="setting-label">{{ t('sharing.shareLinksPage.showHistoryLabel') }}</span>
                            <span class="setting-value">{{ formatShowHistory(link) }}</span>
                          </div>
                        </div>
                      </div>

                      <div class="link-actions">
                        <Button
                            :label="t('sharing.shareLinksPage.edit')"
                            icon="pi pi-pencil"
                            severity="secondary"
                            @click="editLink(link)"
                            class="edit-btn"
                        />
                        <Button
                            :label="t('sharing.shareLinksPage.delete')"
                            icon="pi pi-trash"
                            severity="danger"
                            @click="confirmDeleteLink(link)"
                            class="delete-btn"
                        />
                      </div>
                    </template>
                  </Card>
                </div>
              </div>

              <!-- Expired Live Location Links -->
              <div v-if="expiredLiveLocationShares.length > 0" class="links-section">
                <h3 class="section-subtitle">{{ t('sharing.shareLinksPage.expiredCount', { count: expiredLiveLocationShares.length }) }}</h3>
                <div class="links-grid">
                  <Card v-for="link in expiredLiveLocationShares"
                        :key="link.id"
                        class="link-card expired live-location-card">
                    <template #content>
                      <div class="link-header">
                        <div class="link-info">
                          <h3 class="link-title">{{ link.name || t('sharing.shareLinksPage.untitledLink') }}</h3>
                          <div class="link-meta">
                            <span class="link-date">{{ t('sharing.shareLinksPage.createdOn', { date: formatDate(link.created_at) }) }}</span>
                            <span class="link-expires">{{ t('sharing.shareLinksPage.expiredOn', { date: formatDate(link.expires_at) }) }}</span>
                          </div>
                        </div>
                        <div class="link-status">
                          <Tag severity="danger" :value="t('sharing.shareLinksPage.statusExpired')"/>
                        </div>
                      </div>

                      <div class="link-actions">
                        <Button
                            :label="t('sharing.shareLinksPage.delete')"
                            icon="pi pi-trash"
                            severity="danger"
                            @click="confirmDeleteLink(link)"
                            class="delete-btn"
                        />
                      </div>
                    </template>
                  </Card>
                </div>
              </div>
            </div>

            <!-- Empty State -->
            <div v-if="shareLinksStore.links.length === 0 && !shareLinksStore.isLoading"
                 class="empty-state">
              <i class="pi pi-share-alt empty-icon"></i>
              <h3>{{ t('sharing.shareLinksPage.empty.title') }}</h3>
              <p>{{ t('sharing.shareLinksPage.empty.message') }}</p>
              <Button
                  :label="t('sharing.shareLinksPage.empty.createFirst')"
                  icon="pi pi-plus"
                  @click="(event) => menu.toggle(event)"
                  class="empty-action-btn"
                  aria-haspopup="true"
                  aria-controls="create_menu"
              />
            </div>
          </div>
        </div>

        <!-- Create/Edit Link Dialog -->
        <Dialog
            v-model:visible="showCreateDialog"
            :header="liveSuccessState ? t('sharing.shareLinksPage.dialog.headerCreated') : (editingLink ? t('sharing.shareLinksPage.dialog.headerEdit') : t('sharing.shareLinksPage.dialog.headerCreate'))"
            :modal="true"
            :closable="true"
            :draggable="false"
            :style="{width: '90vw', maxWidth: '700px'}"
            :breakpoints="{'960px': '90vw', '640px': '95vw'}"
            @hide="closeDialog"
        >
          <ShareLinkSuccessState
              v-if="liveSuccessState && createdLiveShare"
              :share="createdLiveShare"
              :base-url="shareLinksStore.baseUrl"
              @create-another="resetLiveCreateForm"
              @done="closeDialog"
          />

          <form v-else @submit.prevent="submitLinkForm" class="link-form">
            <div class="form-group">
              <label for="name" class="form-label">{{ t('sharing.shareLinksPage.dialog.nameLabel') }}</label>
              <InputText
                  id="name"
                  v-model="linkForm.name"
                  :placeholder="t('sharing.shareLinksPage.dialog.namePlaceholder')"
                  class="form-input"
              />
            </div>

            <div class="form-group">
              <label class="form-label">{{ t('sharing.shareLinksPage.dialog.scopeLabel') }}</label>
              <div class="scope-options">
                <div class="scope-option">
                  <RadioButton
                      id="current-only"
                      v-model="linkForm.show_history"
                      :value="false"
                  />
                  <label for="current-only" class="scope-label">
                    <strong>{{ t('sharing.shareLinksPage.dialog.currentLocationOnly') }}</strong>
                    <span class="scope-description">{{ t('sharing.shareLinksPage.dialog.currentLocationOnlyHint') }}</span>
                  </label>
                </div>
                <div class="scope-option">
                  <RadioButton
                      id="with-history"
                      v-model="linkForm.show_history"
                      :value="true"
                  />
                  <label for="with-history" class="scope-label">
                    <strong>{{ t('sharing.shareLinksPage.dialog.locationHistory') }}</strong>
                    <span class="scope-description">{{ t('sharing.shareLinksPage.dialog.locationHistoryHint') }}</span>
                  </label>
                </div>
              </div>
            </div>

            <div v-if="linkForm.show_history" class="form-group">
              <label for="history-hours" class="form-label">{{ t('sharing.shareLinksPage.dialog.historyDurationLabel') }}</label>
              <InputNumber id="history-hours" v-model="linkForm.history_hours" :min="1" :suffix="t('sharing.shareLinksPage.dialog.historyDurationSuffix')" class="form-input" />
              <small class="p-text-secondary">{{ t('sharing.shareLinksPage.dialog.historyDurationHint') }}</small>
            </div>

            <div class="form-group">
              <label for="expires_at" class="form-label">{{ t('sharing.shareLinksPage.dialog.expiresAtLabel') }}</label>
              <Calendar
                  id="expires_at"
                  v-model="linkForm.expires_at"
                  :minDate="minDate"
                  showTime
                  hourFormat="24"
                  :dateFormat="timezone.getPrimeVueDatePickerFormat()"
                  class="form-input"
              />
            </div>

            <div class="form-group">
              <div class="checkbox-wrapper">
                <Checkbox
                    id="has_password"
                    v-model="linkForm.has_password"
                    :binary="true"
                />
                <label for="has_password" class="checkbox-label">{{ t('sharing.shareLinksPage.dialog.hasPasswordLabel') }}</label>
              </div>
            </div>

            <div v-if="linkForm.has_password" class="form-group">
              <label for="password" class="form-label">{{ t('sharing.timelineDialog.fields.password') }}</label>
              <Password
                  id="password"
                  v-model="linkForm.password"
                  :placeholder="t('sharing.timelineDialog.fields.passwordPlaceholder')"
                  class="form-input"
                  :feedback="false"
                  :class="{'p-invalid': formErrors.password}"
              />
              <small v-if="formErrors.password" class="p-error">
                {{ formErrors.password }}
              </small>
            </div>

            <div class="form-group">
              <label for="live-map-render-mode" class="form-label">{{ t('sharing.shareLinksPage.dialog.mapRenderModeLabel') }}</label>
              <Dropdown
                  id="live-map-render-mode"
                  v-model="linkForm.map_render_mode"
                  :options="mapRenderModeOptions"
                  optionLabel="label"
                  optionValue="value"
                  class="form-input"
              />
              <small class="p-text-secondary">
                {{ t('sharing.shareLinksPage.dialog.mapRenderModeHint') }}
              </small>
            </div>

            <div class="form-group">
              <div class="checkbox-wrapper">
                <Checkbox
                    id="use_custom_tiles"
                    v-model="linkForm.use_custom_tiles"
                    :binary="true"
                />
                <label for="use_custom_tiles" class="checkbox-label">{{ t('sharing.timelineDialog.fields.useCustomTiles') }}</label>
              </div>
            </div>

            <div v-if="linkForm.use_custom_tiles" class="custom-tiles-section">
              <Message severity="warn" :closable="false" class="security-warning">
                <div class="warning-content">
                  <i class="pi pi-exclamation-triangle"></i>
                  <div>
                    <strong>{{ t('sharing.timelineDialog.fields.securityNoticeTitle') }}</strong>
                    {{ t('sharing.timelineDialog.fields.securityNoticeBody') }}
                  </div>
                </div>
              </Message>

              <div class="form-group">
                <label for="custom-tile-url" class="form-label">{{ t('sharing.timelineDialog.fields.customTileUrl') }}</label>
                <InputText
                    id="custom-tile-url"
                    v-model="linkForm.custom_map_tile_url"
                    :placeholder="t('sharing.timelineDialog.fields.customTileUrlPlaceholder')"
                    class="form-input"
                    :class="{'p-invalid': formErrors.custom_map_tile_url}"
                />
                <small class="p-text-secondary">
                  {{ t('sharing.timelineDialog.fields.customTileUrlHelp') }}
                </small>
                <small v-if="formErrors.custom_map_tile_url" class="p-error">
                  {{ formErrors.custom_map_tile_url }}
                </small>
              </div>
            </div>

            <div class="form-group">
              <div class="checkbox-wrapper">
                <Checkbox
                    id="use_custom_style"
                    v-model="linkForm.use_custom_style"
                    :binary="true"
                />
                <label for="use_custom_style" class="checkbox-label">{{ t('sharing.timelineDialog.fields.useCustomStyle') }}</label>
              </div>
            </div>

            <div v-if="linkForm.use_custom_style" class="custom-tiles-section">
              <div class="form-group">
                <label for="custom-style-url" class="form-label">{{ t('sharing.timelineDialog.fields.customStyleUrl') }}</label>
                <InputText
                    id="custom-style-url"
                    v-model="linkForm.custom_map_style_url"
                    :placeholder="t('sharing.timelineDialog.fields.customStyleUrlPlaceholder')"
                    class="form-input"
                    :class="{'p-invalid': formErrors.custom_map_style_url}"
                />
                <small class="p-text-secondary">
                  {{ t('sharing.shareLinksPage.dialog.customStyleUrlHint') }}
                </small>
                <small v-if="formErrors.custom_map_style_url" class="p-error">
                  {{ formErrors.custom_map_style_url }}
                </small>
              </div>
            </div>

            <div class="form-actions">
              <Button
                  :label="t('common.cancel')"
                  severity="secondary"
                  @click="closeDialog"
                  type="button"
                  class="cancel-btn"
              />
              <Button
                  :label="editingLink ? t('sharing.shareLinksPage.dialog.updateLink') : t('sharing.timelineDialog.actions.createLink')"
                  type="submit"
                  :loading="shareLinksStore.isLoading"
                  class="submit-btn"
              />
            </div>
          </form>
        </Dialog>

        <!-- Timeline Share Dialog -->
        <TimelineShareDialog
            v-model:visible="showTimelineDialog"
            :editing-share="editingLink"
            @created="handleTimelineCreated"
            @updated="handleTimelineUpdated"
        />

        <!-- Delete Confirmation Dialog -->
        <Dialog
            v-model:visible="showDeleteDialog"
            :header="t('sharing.shareLinksPage.deleteDialog.header')"
            :modal="true"
            :closable="true"
            :draggable="false"
            class="delete-dialog"
        >
          <div class="delete-content">
            <i class="pi pi-exclamation-triangle warning-icon"></i>
            <div class="delete-message">
              <h3>{{ t('sharing.shareLinksPage.deleteDialog.title') }}</h3>
              <p>{{ t('sharing.shareLinksPage.deleteDialog.message', { name: linkToDelete?.name || t('sharing.shareLinksPage.deleteDialog.defaultLinkName') }) }}</p>
              <p class="warning-text">{{ t('sharing.shareLinksPage.deleteDialog.warning') }}</p>
            </div>
          </div>
          <div class="delete-actions">
            <Button
                :label="t('common.cancel')"
                severity="secondary"
                @click="showDeleteDialog = false"
                class="cancel-btn"
            />
            <Button
                :label="t('sharing.shareLinksPage.delete')"
                severity="danger"
                @click="deleteLink"
                :loading="shareLinksStore.isLoading"
                class="delete-btn"
            />
          </div>
        </Dialog>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import {ref, reactive, onMounted, computed, watch} from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {useToast} from 'primevue/usetoast'
import {useShareLinksStore} from '@/stores/shareLinks'
import { useTripsStore } from '@/stores/trips'
import { useTimezone } from '@/composables/useTimezone'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import TimelineShareDialog from '@/components/sharing/TimelineShareDialog.vue'
import ShareLinkSuccessState from '@/components/sharing/ShareLinkSuccessState.vue'
import Menu from 'primevue/menu'
import Message from 'primevue/message'
import { copyToClipboard as copyTextToClipboard } from '@/utils/clipboardUtils'
import { findMatchingTripForShareLink } from '@/utils/tripHelpers'
import { readCachedUserProfile } from '@/utils/userProfileCache'
import { buildShareLinkOptions, buildShareUrl } from '@/utils/shareLinkUrls'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const { t } = useI18n()
const timezone = useTimezone()

const router = useRouter()
const toast = useToast()
const shareLinksStore = useShareLinksStore()
const tripsStore = useTripsStore()

// Create menu items for dropdown
const createMenuItems = computed(() => [
  {
    label: t('sharing.shareLinksPage.createMenu.liveLocation'),
    icon: 'pi pi-map-marker',
    command: () => {
      showCreateDialog.value = true
    }
  },
  {
    label: t('sharing.shareLinksPage.createMenu.timeline'),
    icon: 'pi pi-calendar',
    command: () => {
      showTimelineDialog.value = true
    }
  }
])

// Component state
const showCreateDialog = ref(false)
const showTimelineDialog = ref(false)
const showDeleteDialog = ref(false)
const editingLink = ref(null)
const linkToDelete = ref(null)
const menu = ref(null)
const copyMenu = ref(null)
const copyMenuLink = ref(null)
const createdLiveShare = ref(null)
const liveSuccessState = computed(() => !editingLink.value && !!createdLiveShare.value)

// Form state
const linkForm = reactive({
  name: '',
  expires_at: null,
  show_history: false,
  history_hours: 24,
  map_render_mode: 'VECTOR',
  has_password: false,
  password: '',
  use_custom_tiles: false,
  custom_map_tile_url: '',
  use_custom_style: false,
  custom_map_style_url: ''
})

const formErrors = reactive({
  password: null,
  custom_map_tile_url: null,
  custom_map_style_url: null
})
const mapRenderModeOptions = computed(() => [
  { label: t('sharing.timelineDialog.mapRenderModeOptions.vector'), value: 'VECTOR' },
  { label: t('sharing.timelineDialog.mapRenderModeOptions.raster'), value: 'RASTER' }
])

// Computed
const minDate = computed(() => timezone.now().toDate())

// Watch for custom tiles checkbox changes
watch(() => linkForm.use_custom_tiles, (enabled) => {
  if (enabled && !linkForm.custom_map_tile_url) {
    // Pre-fill with user's profile custom tile URL if available
    try {
      const userInfo = readCachedUserProfile()
      if (userInfo.customMapTileUrl && userInfo.customMapTileUrl.trim()) {
        linkForm.custom_map_tile_url = userInfo.customMapTileUrl.trim()
      }
    } catch (error) {
      console.error('Error reading cached user custom tile URL:', error)
    }
  }
})

watch(() => linkForm.use_custom_style, (enabled) => {
  if (enabled && !linkForm.custom_map_style_url) {
    try {
      const userInfo = readCachedUserProfile()
      if (userInfo.customMapStyleUrl && userInfo.customMapStyleUrl.trim()) {
        linkForm.custom_map_style_url = userInfo.customMapStyleUrl.trim()
      }
    } catch (error) {
      console.error('Error reading cached user custom style URL:', error)
    }
  }
})

watch(() => linkForm.has_password, (enabled) => {
  if (!enabled) {
    linkForm.password = ''
    formErrors.password = null
  }
})

// Methods
const resetForm = () => {
  linkForm.name = ''
  linkForm.expires_at = null
  linkForm.show_history = false
  linkForm.history_hours = 24
  linkForm.map_render_mode = 'VECTOR'
  linkForm.has_password = false
  linkForm.password = ''
  linkForm.use_custom_tiles = false
  linkForm.custom_map_tile_url = ''
  linkForm.use_custom_style = false
  linkForm.custom_map_style_url = ''
  formErrors.password = null
  formErrors.custom_map_tile_url = null
  formErrors.custom_map_style_url = null
}

const closeDialog = () => {
  showCreateDialog.value = false
  editingLink.value = null
  createdLiveShare.value = null
  resetForm()
}

const resetLiveCreateForm = () => {
  createdLiveShare.value = null
  resetForm()
}

const editLink = (link) => {
  editingLink.value = link

  // Check link type and open appropriate dialog
  if (link.share_type === 'TIMELINE') {
    // Open Timeline dialog
    showTimelineDialog.value = true
  } else {
    // Open Live Location dialog
    linkForm.name = link.name || ''
    linkForm.expires_at = link.expires_at ? timezone.fromUtc(link.expires_at).toDate() : null
    linkForm.show_history = link.show_history
    linkForm.history_hours = link.history_hours || 24
    linkForm.map_render_mode = link.map_render_mode || 'VECTOR'
    linkForm.has_password = link.has_password
    linkForm.password = '' // Don't pre-fill password for security
    linkForm.use_custom_tiles = !!(link.custom_map_tile_url)
    linkForm.custom_map_tile_url = link.custom_map_tile_url || ''
    linkForm.use_custom_style = !!(link.custom_map_style_url)
    linkForm.custom_map_style_url = link.custom_map_style_url || ''
    showCreateDialog.value = true
  }
}

const submitLinkForm = async () => {
  // Clear previous errors
  formErrors.password = null
  formErrors.custom_map_tile_url = null
  formErrors.custom_map_style_url = null

  if (linkForm.has_password) {
    const passwordLength = (linkForm.password || '').length
    if (passwordLength < 6 || passwordLength > 100) {
      formErrors.password = t('sharing.timelineDialog.errors.passwordLength')
      return
    }
  }

  // Validate custom tiles if enabled
  if (linkForm.use_custom_tiles) {
    if (!linkForm.custom_map_tile_url || !linkForm.custom_map_tile_url.trim()) {
      formErrors.custom_map_tile_url = t('sharing.timelineDialog.errors.customTileUrlRequired')
      return
    } else if (linkForm.custom_map_tile_url.length > 1000) {
      formErrors.custom_map_tile_url = t('sharing.timelineDialog.errors.urlTooLong')
      return
    }
  }

  if (linkForm.use_custom_style) {
    const styleUrl = linkForm.custom_map_style_url || ''
    const normalizedUrl = styleUrl.trim().toLowerCase()
    if (!styleUrl.trim()) {
      formErrors.custom_map_style_url = t('sharing.timelineDialog.errors.customStyleUrlRequired')
      return
    }
    if (styleUrl.length > 1000) {
      formErrors.custom_map_style_url = t('sharing.timelineDialog.errors.urlTooLong')
      return
    }
    if (!normalizedUrl.startsWith('http://') && !normalizedUrl.startsWith('https://')) {
      formErrors.custom_map_style_url = t('sharing.timelineDialog.errors.urlMustBeHttp')
      return
    }
    if (
      normalizedUrl.includes('javascript:') ||
      normalizedUrl.includes('data:') ||
      normalizedUrl.includes('file:') ||
      normalizedUrl.includes('ftp:')
    ) {
      formErrors.custom_map_style_url = t('sharing.timelineDialog.errors.urlInvalidProtocol')
      return
    }
    if (styleUrl.includes('..')) {
      formErrors.custom_map_style_url = t('sharing.timelineDialog.errors.urlInvalidFormat')
      return
    }
    const looksLikeStyleUrl = normalizedUrl.endsWith('.json') || normalizedUrl.includes('/style') || normalizedUrl.includes('/styles/')
    if (!looksLikeStyleUrl) {
      formErrors.custom_map_style_url = t('sharing.timelineDialog.errors.urlShouldBeStyleEndpoint')
      return
    }
  }

  try {
    const formData = {
      name: linkForm.name || t('sharing.shareLinksPage.untitledLink'),
      expires_at: linkForm.expires_at ? linkForm.expires_at.toISOString() : null,
      show_history: linkForm.show_history,
      history_hours: linkForm.show_history ? linkForm.history_hours : null,
      map_render_mode: linkForm.map_render_mode || 'VECTOR',
      password: linkForm.has_password ? linkForm.password : null,
      custom_map_tile_url: linkForm.use_custom_tiles ? linkForm.custom_map_tile_url : null,
      custom_map_style_url: linkForm.use_custom_style ? linkForm.custom_map_style_url : null
    }

    if (editingLink.value) {
      await shareLinksStore.updateShareLink(editingLink.value.id, formData)
      toast.add({
        severity: 'success',
        summary: t('common.success'),
        detail: t('sharing.shareLinksPage.toasts.updateSuccessDetail'),
        life: 3000
      })
    } else {
      createdLiveShare.value = await shareLinksStore.createShareLink(formData)
      return
    }

    closeDialog()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: error.userMessage || error.message || t('sharing.shareLinksPage.toasts.saveFailedDetail'),
      life: 5000
    })
  }
}

const confirmDeleteLink = (link) => {
  linkToDelete.value = link
  showDeleteDialog.value = true
}

const deleteLink = async () => {
  try {
    await shareLinksStore.deleteShareLink(linkToDelete.value.id)
    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('sharing.shareLinksPage.toasts.deleteSuccessDetail'),
      life: 3000
    })
    showDeleteDialog.value = false
    linkToDelete.value = null
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: error.userMessage || error.message || t('sharing.shareLinksPage.toasts.deleteFailedDetail'),
      life: 5000
    })
  }
}

const getShareUrl = (link) => {
  return buildShareUrl(link, shareLinksStore.baseUrl)
}

const copyMenuItems = computed(() => {
  const link = copyMenuLink.value
  if (!link) return []

  return buildShareLinkOptions(link, shareLinksStore.baseUrl).map((option) => ({
    label: t('sharing.linkSuccess.copyTooltip', { label: option.label }),
    icon: option.key === 'share' ? 'pi pi-link' : option.key === 'map' ? 'pi pi-map' : 'pi pi-list',
    command: () => copyToClipboard(option.url)
  }))
})

const openCopyMenu = (event, link) => {
  copyMenuLink.value = link
  copyMenu.value?.toggle(event)
}

const copyToClipboard = async (text) => {
  const success = await copyTextToClipboard(text)

  if (success) {
    toast.add({
      severity: 'success',
      summary: t('sharing.shareLinksPage.toasts.copiedSummary'),
      detail: t('sharing.shareLinksPage.toasts.copiedDetail'),
      life: 2000
    })
  } else {
    toast.add({
      severity: 'warn',
      summary: t('sharing.shareLinksPage.toasts.copyFailedSummary'),
      detail: t('sharing.shareLinksPage.toasts.copyFailedDetail'),
      life: 3000
    })
  }
}

const formatDate = (dateString) => {
  if (!dateString) return ''
  return `${timezone.formatDateDisplay(dateString)} ${timezone.formatTime(dateString)}`
}

const formatShowHistory = (link) => {
  if (!link.show_history) {
    return t('sharing.shareLinksPage.dialog.currentLocationOnly');
  }
  if (link.history_hours) {
    return `${t('sharing.shareLinksPage.yes')} (${link.history_hours}h)`;
  }
  return t('sharing.shareLinksPage.yes');
}

function handleTimelineCreated(share) {
  console.log('Timeline share created:', share)
  editingLink.value = null
  shareLinksStore.fetchShareLinks() // Refresh list
}

function handleTimelineUpdated(share) {
  console.log('Timeline share updated:', share)
  showTimelineDialog.value = false
  editingLink.value = null
  shareLinksStore.fetchShareLinks() // Refresh list
  toast.add({
    severity: 'success',
    summary: t('common.success'),
    detail: t('sharing.shareLinksPage.toasts.timelineUpdatedDetail'),
    life: 3000
  })
}

function getTimelineStatusSeverity(status) {
  switch (status) {
    case 'upcoming': return 'info'
    case 'active': return 'success'
    case 'completed': return 'secondary'
    default: return 'secondary'
  }
}

// Computed properties for filtering links by type and status
const activeTimelineShares = computed(() =>
  shareLinksStore.getActiveLinks.filter(link => link.share_type === 'TIMELINE')
)

const expiredTimelineShares = computed(() =>
  shareLinksStore.getExpiredLinks.filter(link => link.share_type === 'TIMELINE')
)

const activeLiveLocationShares = computed(() =>
  shareLinksStore.getActiveLinks.filter(link => link.share_type !== 'TIMELINE')
)

const expiredLiveLocationShares = computed(() =>
  shareLinksStore.getExpiredLinks.filter(link => link.share_type !== 'TIMELINE')
)

const shareLinksTripContextById = computed(() => {
  const result = new Map()
  const allTimelineShares = (shareLinksStore.links || []).filter((link) => link.share_type === 'TIMELINE')

  for (const link of allTimelineShares) {
    result.set(link.id, findMatchingTripForShareLink(link, tripsStore.trips))
  }

  return result
})

const getShareLinkedTrip = (link) => {
  return shareLinksTripContextById.value.get(link?.id) || null
}

const getShareLinkedTripLabel = (trip) => {
  if (!trip) return ''
  return trip.name || t('place.visitsTable.tripFallbackLabel', { id: trip.id })
}

const openTripWorkspace = (trip) => {
  if (!trip?.id) return
  router.push(`/app/trips/${trip.id}`)
}

// Lifecycle
onMounted(async () => {
  try {
    const [linksResult, tripsResult] = await Promise.allSettled([
      shareLinksStore.fetchShareLinks(),
      tripsStore.fetchTrips()
    ])

    if (linksResult.status === 'rejected') {
      throw linksResult.reason
    }
    if (tripsResult.status === 'rejected') {
      console.error('Failed to load trips for share link context:', tripsResult.reason)
    }
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: t('sharing.shareLinksPage.toasts.loadFailedDetail'),
      life: 5000
    })
  }
})
</script>

<style scoped>
.share-links-page {
  padding: 0;
}

.create-link-btn {
  white-space: nowrap;
}

.share-links-content {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem;
  text-align: center;
  color: var(--gp-text-secondary);
}

.share-type-section {
  margin-bottom: 3rem;
  padding: 1.5rem;
  background: var(--gp-surface-muted);
  border-radius: 12px;
  border: 1px solid var(--gp-border);
}

.type-title {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0 0 1.5rem 0;
  color: var(--gp-text-primary);
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding-bottom: 1rem;
  border-bottom: 2px solid var(--gp-border);
}

.type-title i {
  color: var(--gp-primary);
  font-size: 1.5rem;
}

.links-section {
  margin-bottom: 2rem;
}

.links-section:last-child {
  margin-bottom: 0;
}

.section-title {
  font-size: 1.25rem;
  font-weight: 600;
  margin-bottom: 1rem;
  color: var(--gp-text-primary);
}

.section-subtitle {
  font-size: 1.1rem;
  font-weight: 600;
  margin-bottom: 1rem;
  color: var(--gp-text-secondary);
}

.links-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  gap: 1.5rem;
}

.link-card {
  border-radius: 8px;
  transition: all 0.2s ease;
}

.link-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.link-card.expired {
  opacity: 0.7;
}

.timeline-card {
  border-left: 4px solid var(--p-blue-500);
}

.live-location-card {
  border-left: 4px solid var(--p-green-500);
}

/* Dark mode adjustments for share type sections */

.link-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1rem;
}

.link-info {
  flex: 1;
}

.link-title {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0 0 0.25rem 0;
  color: var(--gp-text-primary);
}

.link-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0 0 0.5rem 0;
}

.link-meta {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: var(--gp-text-secondary);
}

.link-details {
  margin-bottom: 1rem;
}

.link-url-section {
  margin-bottom: 1rem;
}

.url-label {
  display: block;
  font-size: 0.9rem;
  font-weight: 500;
  margin-bottom: 0.5rem;
  color: var(--gp-text-primary);
}

.url-input-group {
  display: flex;
  gap: 0.5rem;
}

.share-url-input {
  flex: 1;
  font-family: var(--gp-font-mono);
  font-size: 0.85rem;
}

.copy-btn {
  flex-shrink: 0;
}

.link-settings {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.75rem;
  background: var(--gp-surface-muted);
  border-radius: 6px;
}

.setting-item {
  display: flex;
  justify-content: space-between;
  font-size: 0.9rem;
}

.setting-label {
  color: var(--gp-text-secondary);
}

.setting-value {
  font-weight: 500;
  color: var(--gp-text-primary);
}

.timeline-info {
  margin-top: 0.75rem;
  padding: 0.75rem;
  background: var(--gp-surface-muted);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.link-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: flex-end;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 2rem;
  text-align: center;
}

.empty-icon {
  font-size: 3rem;
  color: var(--gp-text-secondary);
  margin-bottom: 1rem;
}

.empty-state h3 {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0 0 0.5rem 0;
  color: var(--gp-text-primary);
}

.empty-state p {
  color: var(--gp-text-secondary);
  margin: 0 0 1.5rem 0;
}

.share-link-dialog {
  width: 700px;
  max-width: 95vw;
}

.link-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-label {
  font-weight: 500;
  color: var(--gp-text-primary);
}

.form-input {
  width: 100%;
}

.p-error {
  color: #dc2626;
  font-weight: 600;
}

.checkbox-wrapper {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.checkbox-label {
  font-weight: 500;
  color: var(--gp-text-primary);
}

.scope-options {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.scope-option {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 1rem;
  border: 1px solid var(--gp-border);
  border-radius: 6px;
  transition: all 0.2s ease;
}

.scope-option:hover {
  background: var(--gp-surface-muted);
  border-color: var(--gp-primary);
}

.scope-label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  cursor: pointer;
  flex: 1;
}

.scope-label strong {
  font-weight: 600;
  color: var(--gp-text-primary);
}

.scope-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
}

.form-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: flex-end;
  margin-top: 1rem;
}

.delete-dialog {
  width: 400px;
  max-width: 95vw;
}

.delete-content {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.warning-icon {
  font-size: 2rem;
  color: var(--p-orange-500);
  flex-shrink: 0;
}

.delete-message h3 {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0 0 0.5rem 0;
  color: var(--gp-text-primary);
}

.delete-message p {
  margin: 0 0 0.5rem 0;
  color: var(--gp-text-secondary);
}

.warning-text {
  font-weight: 500;
  color: var(--p-orange-600);
}

.delete-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .share-type-section {
    padding: 1rem;
    margin-bottom: 2rem;
  }

  .type-title {
    font-size: 1.25rem;
    padding-bottom: 0.75rem;
  }

  .type-title i {
    font-size: 1.25rem;
  }

  .section-subtitle {
    font-size: 1rem;
  }

  .links-grid {
    grid-template-columns: 1fr;
  }

  .link-actions {
    justify-content: stretch;
  }

  .link-actions .p-button {
    flex: 1;
  }
}

/* Custom Tiles Section Styles */
.custom-tiles-section {
  margin-left: 1.75rem;
  padding-left: 1rem;
  border-left: 3px solid var(--p-orange-500);
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin-bottom: 1rem;
}

.security-warning {
  margin: 0;
}

.warning-content {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}

.warning-content i {
  font-size: 1.25rem;
  color: var(--p-orange-500);
  flex-shrink: 0;
  margin-top: 0.1rem;
}

.warning-content div {
  flex: 1;
  font-size: 0.9rem;
  line-height: 1.5;
}

.trip-workspace-setting {
  align-items: center;
}

.trip-workspace-value {
  display: inline-flex;
  align-items: center;
}

.trip-workspace-btn {
  border-radius: 999px;
  border-color: color-mix(in srgb, var(--gp-primary) 35%, var(--gp-border));
  background: color-mix(in srgb, var(--gp-primary) 8%, var(--gp-surface-card));
  color: var(--gp-primary);
  white-space: nowrap;
}

.trip-workspace-btn:hover {
  border-color: color-mix(in srgb, var(--gp-primary) 55%, var(--gp-border));
  background: color-mix(in srgb, var(--gp-primary) 14%, var(--gp-surface-card));
  color: var(--gp-primary-hover);
}

.trip-workspace-btn:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--gp-primary) 40%, white);
  outline-offset: 2px;
}
</style>
