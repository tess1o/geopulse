<template>
  <AppLayout>
    <PageContainer>
      <div class="share-links-page">

        <!-- Page Header -->
        <div class="page-header" v-if="shareLinksStore.links.length !== 0 || shareLinksStore.isLoading">
          <!--          <div class="page-header">-->
          <div class="header-content">
            <div class="header-text">
              <h1 class="page-title">Share Links</h1>
              <p c  lass="page-description">
                Create and manage shareable links to your location data
              </p>
            </div>
            <div class="header-actions">
              <Button
                  label="Create New"
                  icon="pi pi-plus"
                  @click="(event) => menu.toggle(event)"
                  class="create-link-btn"
                  aria-haspopup="true"
                  aria-controls="create_menu"
              />
              <Menu ref="menu" id="create_menu" :model="createMenuItems" :popup="true" />
            </div>
          </div>
        </div>

        <!-- Share Links Content -->
        <div class="share-links-content">
          <!-- Loading State -->
          <div v-if="shareLinksStore.isLoading && shareLinksStore.links.length === 0"
               class="loading-state">
            <ProgressSpinner/>
            <p>Loading share links...</p>
          </div>

          <!-- Error State -->
          <Message v-if="shareLinksStore.getError"
                   severity="error"
                   :closable="true"
                   @close="shareLinksStore.clearError()">
            {{ shareLinksStore.getError }}
          </Message>

          <!-- Links List -->
          <div v-if="!shareLinksStore.isLoading || shareLinksStore.links.length > 0"
               class="links-container">

            <!-- Timeline Shares Section -->
            <div v-if="activeTimelineShares.length > 0 || expiredTimelineShares.length > 0" class="share-type-section">
              <h2 class="type-title">
                <i class="pi pi-calendar"></i>
                Timeline Shares
              </h2>

              <!-- Active Timeline Links -->
              <div v-if="activeTimelineShares.length > 0" class="links-section">
                <h3 class="section-subtitle">Active ({{ activeTimelineShares.length }})</h3>
                <div class="links-grid">
                <Card v-for="link in activeTimelineShares"
                      :key="link.id"
                      class="link-card active timeline-card">
                  <template #content>
                    <div class="link-header">
                      <div class="link-info">
                        <h3 class="link-title">{{ link.name || 'Untitled Link' }}</h3>
                        <div class="link-meta">
                          <span class="link-date">Created {{ formatDate(link.created_at) }}</span>
                          <span class="link-expires">Expires {{ formatDate(link.expires_at) }}</span>
                        </div>
                      </div>
                      <div class="link-status">
                        <Tag severity="success" value="Active"/>
                      </div>
                    </div>

                    <div class="link-details">
                      <div class="link-url-section">
                        <label class="url-label">Share URL:</label>
                        <div class="url-input-group">
                          <InputText
                              :value="getShareUrl(link)"
                              readonly
                              class="share-url-input"
                          />
                          <Button
                              icon="pi pi-copy"
                              @click="copyToClipboard(getShareUrl(link))"
                              class="copy-btn"
                              v-tooltip="'Copy to clipboard'"
                          />
                        </div>
                      </div>

                      <div class="link-settings">
                        <div class="setting-item">
                          <span class="setting-label">Type:</span>
                          <span class="setting-value">{{ link.share_type === 'TIMELINE' ? 'Timeline' : 'Live Location' }}</span>
                        </div>
                        <div class="setting-item">
                          <span class="setting-label">Password Protected:</span>
                          <span class="setting-value">{{ link.has_password ? 'Yes' : 'No' }}</span>
                        </div>
                        <div class="setting-item">
                          <span class="setting-label">View Count:</span>
                          <span class="setting-value">{{ link.view_count || 0 }}</span>
                        </div>
                        <div v-if="link.share_type !== 'TIMELINE'" class="setting-item">
                          <span class="setting-label">Show History:</span>
                          <span class="setting-value">{{ formatShowHistory(link) }}</span>
                        </div>
                      </div>

                      <!-- Timeline-specific info -->
                      <div v-if="link.share_type === 'TIMELINE'" class="timeline-info">
                        <div class="setting-item">
                          <span class="setting-label">Date Range:</span>
                          <span class="setting-value">
                            {{ formatDate(link.start_date) }} - {{ formatDate(link.end_date) }}
                          </span>
                        </div>
                        <div class="setting-item">
                          <span class="setting-label">Status:</span>
                          <Tag :value="link.timeline_status"
                               :severity="getTimelineStatusSeverity(link.timeline_status)" />
                        </div>
                      </div>
                    </div>

                    <div class="link-actions">
                      <Button
                          label="Edit"
                          icon="pi pi-pencil"
                          severity="secondary"
                          @click="editLink(link)"
                          class="edit-btn"
                      />
                      <Button
                          label="Delete"
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
              <h3 class="section-subtitle">Expired ({{ expiredTimelineShares.length }})</h3>
              <div class="links-grid">
                <Card v-for="link in expiredTimelineShares"
                      :key="link.id"
                      class="link-card expired timeline-card">
                  <template #content>
                    <div class="link-header">
                      <div class="link-info">
                        <h3 class="link-title">{{ link.name || 'Untitled Link' }}</h3>
                        <div class="link-meta">
                          <span class="link-date">Created {{ formatDate(link.created_at) }}</span>
                          <span class="link-expires">Expired {{ formatDate(link.expires_at) }}</span>
                        </div>
                      </div>
                      <div class="link-status">
                        <Tag severity="danger" value="Expired"/>
                      </div>
                    </div>

                    <div class="link-actions">
                      <Button
                          label="Delete"
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
                Live Location Shares
              </h2>

              <!-- Active Live Location Links -->
              <div v-if="activeLiveLocationShares.length > 0" class="links-section">
                <h3 class="section-subtitle">Active ({{ activeLiveLocationShares.length }})</h3>
                <div class="links-grid">
                  <Card v-for="link in activeLiveLocationShares"
                        :key="link.id"
                        class="link-card active live-location-card">
                    <template #content>
                      <div class="link-header">
                        <div class="link-info">
                          <h3 class="link-title">{{ link.name || 'Untitled Link' }}</h3>
                          <div class="link-meta">
                            <span class="link-date">Created {{ formatDate(link.created_at) }}</span>
                            <span class="link-expires">Expires {{ formatDate(link.expires_at) }}</span>
                          </div>
                        </div>
                        <div class="link-status">
                          <Tag severity="success" value="Active"/>
                        </div>
                      </div>

                      <div class="link-details">
                        <div class="link-url-section">
                          <label class="url-label">Share URL:</label>
                          <div class="url-input-group">
                            <InputText
                                :value="getShareUrl(link)"
                                readonly
                                class="share-url-input"
                            />
                            <Button
                                icon="pi pi-copy"
                                @click="copyToClipboard(getShareUrl(link))"
                                class="copy-btn"
                                v-tooltip="'Copy to clipboard'"
                            />
                          </div>
                        </div>

                        <div class="link-settings">
                          <div class="setting-item">
                            <span class="setting-label">Type:</span>
                            <span class="setting-value">Live Location</span>
                          </div>
                          <div class="setting-item">
                            <span class="setting-label">Password Protected:</span>
                            <span class="setting-value">{{ link.has_password ? 'Yes' : 'No' }}</span>
                          </div>
                          <div class="setting-item">
                            <span class="setting-label">View Count:</span>
                            <span class="setting-value">{{ link.view_count || 0 }}</span>
                          </div>
                          <div class="setting-item">
                            <span class="setting-label">Show History:</span>
                            <span class="setting-value">{{ formatShowHistory(link) }}</span>
                          </div>
                        </div>
                      </div>

                      <div class="link-actions">
                        <Button
                            label="Edit"
                            icon="pi pi-pencil"
                            severity="secondary"
                            @click="editLink(link)"
                            class="edit-btn"
                        />
                        <Button
                            label="Delete"
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
                <h3 class="section-subtitle">Expired ({{ expiredLiveLocationShares.length }})</h3>
                <div class="links-grid">
                  <Card v-for="link in expiredLiveLocationShares"
                        :key="link.id"
                        class="link-card expired live-location-card">
                    <template #content>
                      <div class="link-header">
                        <div class="link-info">
                          <h3 class="link-title">{{ link.name || 'Untitled Link' }}</h3>
                          <div class="link-meta">
                            <span class="link-date">Created {{ formatDate(link.created_at) }}</span>
                            <span class="link-expires">Expired {{ formatDate(link.expires_at) }}</span>
                          </div>
                        </div>
                        <div class="link-status">
                          <Tag severity="danger" value="Expired"/>
                        </div>
                      </div>

                      <div class="link-actions">
                        <Button
                            label="Delete"
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
              <h3>No share links yet</h3>
              <p>Create your first share link to start sharing your location data with others.</p>
              <Button
                  label="Create Your First Link"
                  icon="pi pi-plus"
                  @click="showCreateDialog = true"
                  class="empty-action-btn"
              />
            </div>
          </div>
        </div>

        <!-- Create/Edit Link Dialog -->
        <Dialog
            v-model:visible="showCreateDialog"
            :header="editingLink ? 'Edit Share Link' : 'Create Share Link'"
            :modal="true"
            :closable="true"
            :draggable="false"
            class="share-link-dialog"
        >
          <form @submit.prevent="submitLinkForm" class="link-form">
            <div class="form-group">
              <label for="name" class="form-label">Name</label>
              <InputText
                  id="name"
                  v-model="linkForm.name"
                  placeholder="Enter a name for this link"
                  class="form-input"
              />
            </div>

            <div class="form-group">
              <label class="form-label">Location Sharing Scope</label>
              <div class="scope-options">
                <div class="scope-option">
                  <RadioButton
                      id="current-only"
                      v-model="linkForm.show_history"
                      :value="false"
                  />
                  <label for="current-only" class="scope-label">
                    <strong>Current Location Only</strong>
                    <span class="scope-description">Share your most recent location</span>
                  </label>
                </div>
                <div class="scope-option">
                  <RadioButton
                      id="with-history"
                      v-model="linkForm.show_history"
                      :value="true"
                  />
                  <label for="with-history" class="scope-label">
                    <strong>Location History</strong>
                    <span class="scope-description">Share your location path and timeline</span>
                  </label>
                </div>
              </div>
            </div>

            <div v-if="linkForm.show_history" class="form-group">
              <label for="history-hours" class="form-label">History Duration (hours)</label>
              <InputNumber id="history-hours" v-model="linkForm.history_hours" :min="1"  suffix=" hours" class="form-input" />
              <small class="p-text-secondary">Specify how many hours of location history to share</small>
            </div>

            <div class="form-group">
              <label for="expires_at" class="form-label">Expires At</label>
              <Calendar
                  id="expires_at"
                  v-model="linkForm.expires_at"
                  :minDate="minDate"
                  showTime
                  hourFormat="24"
                  dateFormat="mm/dd/yy"
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
                <label for="has_password" class="checkbox-label">Password protect this link</label>
              </div>
            </div>

            <div v-if="linkForm.has_password" class="form-group">
              <label for="password" class="form-label">Password</label>
              <Password
                  id="password"
                  v-model="linkForm.password"
                  placeholder="Enter password"
                  class="form-input"
                  :feedback="false"
              />
            </div>

            <div class="form-actions">
              <Button
                  label="Cancel"
                  severity="secondary"
                  @click="closeDialog"
                  type="button"
                  class="cancel-btn"
              />
              <Button
                  :label="editingLink ? 'Update Link' : 'Create Link'"
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
            header="Confirm Delete"
            :modal="true"
            :closable="true"
            :draggable="false"
            class="delete-dialog"
        >
          <div class="delete-content">
            <i class="pi pi-exclamation-triangle warning-icon"></i>
            <div class="delete-message">
              <h3>Delete Share Link</h3>
              <p>Are you sure you want to delete "{{ linkToDelete?.name || 'this link' }}"?</p>
              <p class="warning-text">This action cannot be undone and the link will no longer be accessible.</p>
            </div>
          </div>
          <div class="delete-actions">
            <Button
                label="Cancel"
                severity="secondary"
                @click="showDeleteDialog = false"
                class="cancel-btn"
            />
            <Button
                label="Delete"
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
import {ref, reactive, onMounted, computed} from 'vue'
import {useToast} from 'primevue/usetoast'
import {useShareLinksStore} from '@/stores/shareLinks'
import { useTimezone } from '@/composables/useTimezone'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import TimelineShareDialog from '@/components/sharing/TimelineShareDialog.vue'
import Menu from 'primevue/menu'
import { copyToClipboard as copyTextToClipboard } from '@/utils/clipboardUtils'

const timezone = useTimezone()

const toast = useToast()
const shareLinksStore = useShareLinksStore()

// Create menu items for dropdown
const createMenuItems = ref([
  {
    label: 'Live Location Share',
    icon: 'pi pi-map-marker',
    command: () => {
      showCreateDialog.value = true
    }
  },
  {
    label: 'Timeline Share',
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

// Form state
const linkForm = reactive({
  name: '',
  expires_at: null,
  show_history: false,
  history_hours: 24,
  has_password: false,
  password: ''
})

// Computed
const minDate = computed(() => timezone.now().toDate())

// Methods
const resetForm = () => {
  linkForm.name = ''
  linkForm.expires_at = null
  linkForm.show_history = false
  linkForm.history_hours = 24
  linkForm.has_password = false
  linkForm.password = ''
}

const closeDialog = () => {
  showCreateDialog.value = false
  editingLink.value = null
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
    linkForm.has_password = link.has_password
    linkForm.password = '' // Don't pre-fill password for security
    showCreateDialog.value = true
  }
}

const submitLinkForm = async () => {
  try {
    console.log('submitLinkForm', linkForm);
    const formData = {
      name: linkForm.name || 'Untitled Link',
      expires_at: linkForm.expires_at ? linkForm.expires_at.toISOString() : null,
      show_history: linkForm.show_history,
      history_hours: linkForm.show_history ? linkForm.history_hours : null,
      password: linkForm.has_password ? linkForm.password : null
    }

    console.log('formData', formData);

    if (editingLink.value) {
      await shareLinksStore.updateShareLink(editingLink.value.id, formData)
      toast.add({
        severity: 'success',
        summary: 'Success',
        detail: 'Share link updated successfully',
        life: 3000
      })
    } else {
      await shareLinksStore.createShareLink(formData)
      toast.add({
        severity: 'success',
        summary: 'Success',
        detail: 'Share link created successfully',
        life: 3000
      })
    }

    closeDialog()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.message || 'Failed to save share link',
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
      summary: 'Success',
      detail: 'Share link deleted successfully',
      life: 3000
    })
    showDeleteDialog.value = false
    linkToDelete.value = null
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.message || 'Failed to delete share link',
      life: 5000
    })
  }
}

const getShareUrl = (link) => {
  const baseUrl = shareLinksStore.baseUrl || window.location.origin;
  // Make sure that the base URL does not have a trailing slash
  const sanitizedBaseUrl = baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl;
  const path = link.share_type === 'TIMELINE' ? 'shared-timeline' : 'shared';
  return `${sanitizedBaseUrl}/${path}/${link.id}`;
}

const copyToClipboard = async (text) => {
  const success = await copyTextToClipboard(text)

  if (success) {
    toast.add({
      severity: 'success',
      summary: 'Copied',
      detail: 'Link copied to clipboard',
      life: 2000
    })
  } else {
    toast.add({
      severity: 'warn',
      summary: 'Copy Failed',
      detail: 'Could not copy to clipboard. Please copy the URL manually.',
      life: 3000
    })
  }
}

const formatDate = (dateString) => {
  return timezone.format(dateString, 'MMM D, YYYY h:mm A')
}

const formatShowHistory = (link) => {
  if (!link.show_history) {
    return 'Current Location Only';
  }
  if (link.history_hours) {
    return `Yes (${link.history_hours}h)`;
  }
  return 'Yes';
}

function handleTimelineCreated(share) {
  console.log('Timeline share created:', share)
  showTimelineDialog.value = false
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
    summary: 'Success',
    detail: 'Timeline share updated successfully',
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

// Lifecycle
onMounted(async () => {
  try {
    await shareLinksStore.fetchShareLinks()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load share links',
      life: 5000
    })
  }
})
</script>

<style scoped>
.share-links-page {
  padding: 0;
}


.page-header {
  margin-bottom: 2rem;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}

.header-text {
  flex: 1;
}

.page-title {
  font-size: 2rem;
  font-weight: 600;
  margin: 0 0 0.5rem 0;
  color: var(--text-color);
}

.page-description {
  font-size: 1rem;
  color: var(--text-color-secondary);
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  flex-wrap: wrap;
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
  color: var(--text-color-secondary);
}

.share-type-section {
  margin-bottom: 3rem;
  padding: 1.5rem;
  background: var(--surface-50);
  border-radius: 12px;
  border: 1px solid var(--surface-border);
}

.type-title {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0 0 1.5rem 0;
  color: var(--text-color);
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding-bottom: 1rem;
  border-bottom: 2px solid var(--surface-border);
}

.type-title i {
  color: var(--primary-color);
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
  color: var(--text-color);
}

.section-subtitle {
  font-size: 1.1rem;
  font-weight: 600;
  margin-bottom: 1rem;
  color: var(--text-color-secondary);
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
  border-left: 4px solid var(--blue-500);
}

.live-location-card {
  border-left: 4px solid var(--green-500);
}

/* Dark mode adjustments for share type sections */
.p-dark .share-type-section {
  background: var(--surface-100);
  border-color: var(--surface-border);
}

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
  color: var(--text-color);
}

.link-description {
  font-size: 0.9rem;
  color: var(--text-color-secondary);
  margin: 0 0 0.5rem 0;
}

.link-meta {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: var(--text-color-secondary);
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
  color: var(--text-color);
}

.url-input-group {
  display: flex;
  gap: 0.5rem;
}

.share-url-input {
  flex: 1;
  font-family: monospace;
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
  background: var(--surface-50);
  border-radius: 6px;
}

.setting-item {
  display: flex;
  justify-content: space-between;
  font-size: 0.9rem;
}

.setting-label {
  color: var(--text-color-secondary);
}

.setting-value {
  font-weight: 500;
  color: var(--text-color);
}

.timeline-info {
  margin-top: 0.75rem;
  padding: 0.75rem;
  background: var(--surface-100);
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
  color: var(--text-color-secondary);
  margin-bottom: 1rem;
}

.empty-state h3 {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0 0 0.5rem 0;
  color: var(--text-color);
}

.empty-state p {
  color: var(--text-color-secondary);
  margin: 0 0 1.5rem 0;
}

.share-link-dialog {
  width: 500px;
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
  color: var(--text-color);
}

.form-input {
  width: 100%;
}

.checkbox-wrapper {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.checkbox-label {
  font-weight: 500;
  color: var(--text-color);
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
  border: 1px solid var(--surface-border);
  border-radius: 6px;
  transition: all 0.2s ease;
}

.scope-option:hover {
  background: var(--surface-50);
  border-color: var(--primary-color);
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
  color: var(--text-color);
}

.scope-description {
  font-size: 0.9rem;
  color: var(--text-color-secondary);
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
  color: var(--orange-500);
  flex-shrink: 0;
}

.delete-message h3 {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0 0 0.5rem 0;
  color: var(--text-color);
}

.delete-message p {
  margin: 0 0 0.5rem 0;
  color: var(--text-color-secondary);
}

.warning-text {
  font-weight: 500;
  color: var(--orange-600);
}

.delete-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .header-content {
    flex-direction: column;
    align-items: stretch;
  }

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
</style>