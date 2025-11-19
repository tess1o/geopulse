<template>
  <AppLayout>
    <PageContainer>
      <div class="timeline-preferences-page">
        <!-- Page Header -->
        <div class="page-header">
          <div class="header-content">
            <div class="header-text">
              <h1 class="page-title">Timeline Preferences</h1>
              <p class="page-description">
                Fine-tune how your location timeline is generated from GPS data
              </p>
            </div>
            <div class="header-actions">
              <Button
                label="View Active Job"
                icon="pi pi-eye"
                severity="info"
                outlined
                @click="goToActiveJob"
                title="Check if timeline generation is currently running"
              />
              <Button
                label="Reset to Defaults"
                icon="pi pi-refresh"
                severity="secondary"
                outlined
                @click="confirmResetDefaults"
                :disabled="timelineRegenerationVisible"
              />
              <Button
                label="Regenerate Timeline"
                icon="pi pi-replay"
                severity="danger"
                outlined
                @click="confirmRegenerateTimeline"
                :disabled="timelineRegenerationVisible"
              />
              <Button
                label="Save Changes"
                icon="pi pi-save"
                @click="confirmSavePreferences"
                :disabled="!hasUnsavedChanges || !isFormValid || timelineRegenerationVisible"
              />
            </div>
          </div>
        </div>

        <!-- Info Banner -->
        <Card class="info-banner">
          <template #content>
            <div class="banner-content">
              <div class="banner-icon">
                <i class="pi pi-info-circle"></i>
              </div>
              <div class="banner-text">
                <h3 class="banner-title">How Timeline Processing Works</h3>
                <p class="banner-description">
                  Your GPS data is processed to identify meaningful stays and trips.
                  These settings control the sensitivity of this detection and apply only to your account.
                  Some changes (like speed thresholds) will quickly update trip classifications, while others may require full timeline re-generation depending on your GPS data volume.
                </p>
              </div>
            </div>
          </template>
        </Card>

        <!-- Unsaved Changes Warning -->
        <Message v-if="hasUnsavedChanges" severity="warn" class="unsaved-warning">
          <div class="warning-content">
            <div class="warning-text">
              <i class="pi pi-exclamation-triangle mr-2"></i>
              You have unsaved changes
            </div>
            <div class="warning-actions">
              <Button 
                label="Discard" 
                size="small" 
                severity="secondary" 
                outlined
                @click="discardChanges" 
              />
              <Button 
                label="Save Now" 
                size="small" 
                @click="confirmSavePreferences"
                :disabled="timelineRegenerationVisible"
              />
            </div>
          </div>
        </Message>

        <!-- Preferences Tabs -->
        <TabContainer
          :tabs="tabItems"
          :activeIndex="activeTabIndex"
          @tab-change="handleTabChange"
          class="preferences-tabs"
        >
          <!-- Stay Point Detection Tab -->
          <div v-if="activeTab === 'staypoints'">
              <div class="preferences-section">
                <div class="section-header">
                  <h2 class="section-title">Stay Point Detection Settings</h2>
                  <p class="section-description">
                    Configure how GPS data is analyzed to identify places where you've stayed
                  </p>
                </div>

                <div class="settings-grid">

                  <!-- Stay Detection Radius -->
                  <SettingCard
                    title="Stay Detection Radius"
                    description="Distance threshold for grouping GPS points into stay locations. Also defines minimum distance between stays to create a trip"
                    :details="{
                      'Lower values': 'More sensitive stay detection, separate nearby locations',
                      'Higher values': 'Less sensitive, merge nearby locations into single stays'
                    }"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.staypointRadiusMeters }}m</div>
                      <SliderControl
                        v-if="prefs.staypointRadiusMeters !== undefined"
                        v-model="prefs.staypointRadiusMeters"
                        :min="10"
                        :max="500"
                        :step="10"
                        :labels="['10m (Sensitive)', '50m (Balanced)', '500m (Conservative)']"
                        suffix=" m"
                        :input-min="1"
                        :input-max="2000"
                        :decimal-places="0"
                      />
                    </template>
                  </SettingCard>

                  <!-- Min Trip Duration -->
                  <SettingCard
                      title="Minimum Stay Duration"
                      description="The minimum duration (in minutes) for a stay point to be confirmed"
                      :details="{
                      'Lower values': 'Too short stays can be false positives',
                      'Higher values': 'Longer stays are more likely to be real'
                    }"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.staypointMinDurationMinutes }} minutes</div>
                      <SliderControl
                          v-if="prefs.staypointMinDurationMinutes !== undefined"
                          v-model="prefs.staypointMinDurationMinutes"
                          :min="1"
                          :max="60"
                          :step="1"
                          :labels="['1 min (Sensitive)', '7 min (Balanced)', '60 min (Conservative)']"
                          suffix=" min"
                          :input-min="1"
                          :input-max="300"
                          :decimal-places="0"
                      />
                    </template>
                  </SettingCard>

                  <!-- Enhanced Filtering -->
                  <SettingCard
                      title="Enhanced Filtering"
                      description="Use velocity and accuracy data for better stay point detection"
                      details="Filters out poor quality GPS points and improves timeline accuracy"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.useVelocityAccuracy ? 'Enabled' : 'Disabled' }}</div>
                      <ToggleSwitch
                          v-model="prefs.useVelocityAccuracy"
                          class="toggle-control"
                      />
                    </template>
                  </SettingCard>

                  <!-- Velocity Threshold -->
                  <SettingCard
                    v-if="prefs.useVelocityAccuracy"
                    title="Velocity Threshold"
                    description="Maximum velocity to consider a point as stationary"
                    :details="{
                      'Lower values': 'More strict filtering',
                      'Higher values': 'Allow more movement within stays'
                    }"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.staypointVelocityThreshold }} km/h</div>
                      <SliderControl
                        v-if="prefs.staypointVelocityThreshold !== undefined"
                        v-model="prefs.staypointVelocityThreshold"
                        :min="1"
                        :max="20"
                        :step="0.5"
                        :labels="['1 km/h (Strict)', '8 km/h (Balanced)', '20 km/h (Lenient)']"
                        suffix=" km/h"
                        :input-min="0.5"
                        :input-max="50"
                        :decimal-places="1"
                      />
                    </template>
                  </SettingCard>

                  <!-- Accuracy Threshold -->
                  <SettingCard
                    v-if="prefs.useVelocityAccuracy"
                    title="GPS Accuracy Threshold"
                    description="Minimum GPS accuracy required to use a location point"
                    :details="{
                      'Lower values': 'Require more accurate GPS',
                      'Higher values': 'Accept less accurate GPS points'
                    }"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.staypointMaxAccuracyThreshold }}m</div>
                      <SliderControl
                        v-if="prefs.staypointMaxAccuracyThreshold !== undefined"
                        v-model="prefs.staypointMaxAccuracyThreshold"
                        :min="5"
                        :max="200"
                        :step="5"
                        :labels="['5m (High accuracy)', '60m (Balanced)', '200m (Low accuracy)']"
                        suffix=" m"
                        :input-min="1"
                        :input-max="500"
                        :decimal-places="1"
                      />
                    </template>
                  </SettingCard>

                  <!-- Min Accuracy Ratio -->
                  <SettingCard
                    v-if="prefs.useVelocityAccuracy"
                    title="Minimum Accuracy Ratio"
                    description="Minimum ratio of accurate GPS points required in a stay point cluster"
                    details="Higher values ensure more reliable stay point detection by requiring a higher percentage of accurate GPS points"
                  >
                    <template #control>
                      <div class="control-value">{{ Math.round(prefs.staypointMinAccuracyRatio * 100) }}%</div>
                      <SliderControl
                        v-if="prefs.staypointMinAccuracyRatio !== undefined"
                        v-model="prefs.staypointMinAccuracyRatio"
                        :min="0.1"
                        :max="1.0"
                        :step="0.05"
                        :labels="['10% (Lenient)', '50% (Balanced)', '100% (Strict)']"
                        :input-min="0.1"
                        :input-max="1.0"
                        :decimal-places="2"
                      />
                    </template>
                  </SettingCard>

                </div>
              </div>
          </div>

          <!-- Trip Classification Tab -->
          <div v-if="activeTab === 'trips'">
              <div class="preferences-section">
                <div class="section-header">
                  <h2 class="section-title">Trip Classification Settings</h2>
                  <p class="section-description">
                    Configure how trips are detected and classified by movement type
                  </p>
                </div>

                <!-- Classification Priority Info Banner -->
                <Card class="priority-info-banner">
                  <template #content>
                    <div class="priority-content">
                      <div class="priority-icon">
                        <i class="pi pi-sort-amount-down"></i>
                      </div>
                      <div class="priority-text">
                        <h3 class="priority-title">Classification Priority Order</h3>
                        <div class="priority-flow">
                          <span class="priority-step">✈️ FLIGHT</span>
                          <i class="pi pi-arrow-right"></i>
                          <span class="priority-step">🚊 TRAIN</span>
                          <i class="pi pi-arrow-right"></i>
                          <span class="priority-step">🚴 BICYCLE</span>
                          <i class="pi pi-arrow-right"></i>
                          <span class="priority-step">🚗 CAR</span>
                          <i class="pi pi-arrow-right"></i>
                          <span class="priority-step">🚶 WALK</span>
                          <i class="pi pi-arrow-right"></i>
                          <span class="priority-step priority-unknown">❓ UNKNOWN</span>
                        </div>
                        <p class="priority-description">
                          Trips are classified in priority order from top to bottom. Once a match is found, classification stops.
                          This order handles overlapping speed ranges correctly - e.g., a 20 km/h trip matches BICYCLE before reaching CAR.
                        </p>
                      </div>
                    </div>
                  </template>
                </Card>

                <div class="settings-grid">
                  <!-- Trip Detection Algorithm -->
                  <SettingCard
                    title="Trip Detection Algorithm"
                    description="The algorithm used to identify trips between stay points"
                    :details="{
                      'Single': 'Always one trip between stay points',
                      'Multiple': 'Based on velocity one or more trips between stay points (like CAR → WALK, WALK → CAR, etc)'
                    }"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.tripDetectionAlgorithm }}</div>
                      <Select
                        v-model="prefs.tripDetectionAlgorithm"
                        :options="tripsAlgorithmOptions"
                        optionLabel="label"
                        optionValue="value"
                        placeholder="Select algorithm"
                        class="w-full"
                      />
                    </template>
                  </SettingCard>
                  
                  <!-- Walking Classification -->
                  <TransportTypeCard
                    type="walk"
                    title="Walking"
                    subtitle="Mandatory transport type"
                    icon="pi pi-user"
                    description="Detects slow-speed movement on foot (0-8 km/h typical)"
                    :mandatory="true"
                    :validation-messages="getWarningMessagesForType('walk').value"
                  >
                    <template #parameters>
                      <div class="parameter-group">
                        <label class="parameter-label">Maximum Average Speed</label>
                        <p class="parameter-description">
                          Trips with average speeds above this will be classified as non-walking
                        </p>
                        <div class="control-value">{{ prefs.walkingMaxAvgSpeed }} km/h</div>
                        <SliderControl
                          v-if="prefs.walkingMaxAvgSpeed !== undefined"
                          v-model="prefs.walkingMaxAvgSpeed"
                          :min="3.0" :max="10.0" :step="0.5"
                          :labels="['3.0 km/h (Slow)', '5.5 km/h (Normal)', '10.0 km/h (Fast)']"
                          suffix=" km/h" :decimal-places="1"
                        />
                      </div>

                      <div class="parameter-group">
                        <label class="parameter-label">Maximum Peak Speed</label>
                        <p class="parameter-description">
                          Brief speed bursts above this will reclassify the trip
                        </p>
                        <div class="control-value">{{ prefs.walkingMaxMaxSpeed }} km/h</div>
                        <SliderControl
                          v-if="prefs.walkingMaxMaxSpeed !== undefined"
                          v-model="prefs.walkingMaxMaxSpeed"
                          :min="5.0" :max="15.0" :step="0.5"
                          :labels="['5.0 km/h (Conservative)', '8.0 km/h (Normal)', '15.0 km/h (Generous)']"
                          suffix=" km/h" :decimal-places="1"
                        />
                      </div>
                    </template>
                  </TransportTypeCard>

                  <!-- Bicycle Classification -->
                  <TransportTypeCard
                    type="bicycle"
                    title="Bicycle"
                    subtitle="Optional transport type"
                    icon="pi pi-circle"
                    description="Detects cycling trips (8-25 km/h typical range). Also captures running/jogging. Priority order ensures correct classification even with speed overlap with cars."
                    v-model:enabled="prefs.bicycleEnabled"
                    :collapsible="true"
                    :validation-messages="getWarningMessagesForType('bicycle').value"
                  >
                    <template #parameters>
                      <div class="parameter-group">
                        <label class="parameter-label">Minimum Average Speed</label>
                        <p class="parameter-description">
                          Trips slower than this will be classified as walking
                        </p>
                        <div class="control-value">{{ prefs.bicycleMinAvgSpeed }} km/h</div>
                        <SliderControl
                          v-if="prefs.bicycleMinAvgSpeed !== undefined"
                          v-model="prefs.bicycleMinAvgSpeed"
                          :min="5.0" :max="15.0" :step="0.5"
                          :labels="['5.0 km/h (Slow)', '8.0 km/h (Default)', '15.0 km/h (Fast)']"
                          suffix=" km/h" :decimal-places="1"
                        />
                      </div>

                      <div class="parameter-group">
                        <label class="parameter-label">Maximum Average Speed</label>
                        <p class="parameter-description">
                          Trips faster than this will be classified as motorized transport
                        </p>
                        <div class="control-value">{{ prefs.bicycleMaxAvgSpeed }} km/h</div>
                        <SliderControl
                          v-if="prefs.bicycleMaxAvgSpeed !== undefined"
                          v-model="prefs.bicycleMaxAvgSpeed"
                          :min="15.0" :max="35.0" :step="1.0"
                          :labels="['15.0 km/h (Slow)', '25.0 km/h (Default)', '35.0 km/h (E-bike)']"
                          suffix=" km/h" :decimal-places="1"
                        />
                      </div>

                      <div class="parameter-group">
                        <label class="parameter-label">Maximum Peak Speed</label>
                        <p class="parameter-description">
                          Allows for downhill segments or e-bikes, but below car speeds
                        </p>
                        <div class="control-value">{{ prefs.bicycleMaxMaxSpeed }} km/h</div>
                        <SliderControl
                          v-if="prefs.bicycleMaxMaxSpeed !== undefined"
                          v-model="prefs.bicycleMaxMaxSpeed"
                          :min="20.0" :max="50.0" :step="5.0"
                          :labels="['20.0 km/h (City)', '35.0 km/h (Default)', '50.0 km/h (E-bike)']"
                          suffix=" km/h" :decimal-places="1"
                        />
                      </div>
                    </template>
                  </TransportTypeCard>

                  <!-- Car Classification -->
                  <TransportTypeCard
                    type="car"
                    title="Car"
                    subtitle="Mandatory transport type"
                    icon="pi pi-car"
                    description="Detects motorized vehicle transport (10+ km/h). High speed variance indicates stop-and-go traffic."
                    :mandatory="true"
                    :validation-messages="getWarningMessagesForType('car').value"
                  >
                    <template #parameters>
                      <div class="parameter-group">
                        <label class="parameter-label">Minimum Average Speed</label>
                        <p class="parameter-description">
                          Trips with average speeds below this will be classified as walking or bicycle (if enabled)
                        </p>
                        <div class="control-value">{{ prefs.carMinAvgSpeed }} km/h</div>
                        <SliderControl
                          v-if="prefs.carMinAvgSpeed !== undefined"
                          v-model="prefs.carMinAvgSpeed"
                          :min="5.0" :max="25.0" :step="0.5"
                          :labels="['5.0 km/h (Sensitive)', '10.0 km/h (Default)', '25.0 km/h (Conservative)']"
                          suffix=" km/h" :decimal-places="1"
                        />
                      </div>

                      <div class="parameter-group">
                        <label class="parameter-label">Minimum Peak Speed</label>
                        <p class="parameter-description">
                          Trips that never reach this speed will not be classified as driving
                        </p>
                        <div class="control-value">{{ prefs.carMinMaxSpeed }} km/h</div>
                        <SliderControl
                          v-if="prefs.carMinMaxSpeed !== undefined"
                          v-model="prefs.carMinMaxSpeed"
                          :min="10.0" :max="50.0" :step="5.0"
                          :labels="['10.0 km/h (City)', '15.0 km/h (Default)', '50.0 km/h (Highway)']"
                          suffix=" km/h" :decimal-places="1"
                        />
                      </div>
                    </template>
                  </TransportTypeCard>

                  <!-- Train Classification -->
                  <TransportTypeCard
                    type="train"
                    title="Train"
                    subtitle="Optional transport type"
                    icon="pi pi-building"
                    description="Detects train travel by high speed with low variance (30-150 km/h, steady movement). Uses speed variance to distinguish from cars."
                    v-model:enabled="prefs.trainEnabled"
                    :collapsible="true"
                    :validation-messages="getWarningMessagesForType('train').value"
                  >
                    <template #parameters>
                      <div class="parameter-group">
                        <label class="parameter-label">Minimum Average Speed</label>
                        <p class="parameter-description">
                          Separates from cars in heavy traffic
                        </p>
                        <div class="control-value">{{ prefs.trainMinAvgSpeed }} km/h</div>
                        <SliderControl
                          v-if="prefs.trainMinAvgSpeed !== undefined"
                          v-model="prefs.trainMinAvgSpeed"
                          :min="20.0" :max="50.0" :step="5.0"
                          :labels="['20.0 km/h (City)', '30.0 km/h (Default)', '50.0 km/h (Fast)']"
                          suffix=" km/h" :decimal-places="1"
                        />
                      </div>

                      <div class="parameter-group">
                        <label class="parameter-label">Maximum Average Speed</label>
                        <p class="parameter-description">
                          Covers regional and intercity trains
                        </p>
                        <div class="control-value">{{ prefs.trainMaxAvgSpeed }} km/h</div>
                        <SliderControl
                          v-if="prefs.trainMaxAvgSpeed !== undefined"
                          v-model="prefs.trainMaxAvgSpeed"
                          :min="80.0" :max="200.0" :step="10.0"
                          :labels="['80.0 km/h (Regional)', '150.0 km/h (Default)', '200.0 km/h (High-speed)']"
                          suffix=" km/h" :decimal-places="1"
                        />
                      </div>

                      <div class="parameter-group">
                        <label class="parameter-label">Minimum Peak Speed (Station Filter)</label>
                        <p class="parameter-description">
                          Filters out trips with only station waiting time (critical!)
                        </p>
                        <div class="control-value">{{ prefs.trainMinMaxSpeed }} km/h</div>
                        <SliderControl
                          v-if="prefs.trainMinMaxSpeed !== undefined"
                          v-model="prefs.trainMinMaxSpeed"
                          :min="60.0" :max="120.0" :step="10.0"
                          :labels="['60.0 km/h (Lenient)', '80.0 km/h (Default)', '120.0 km/h (Strict)']"
                          suffix=" km/h" :decimal-places="1"
                        />
                      </div>

                      <div class="parameter-group">
                        <label class="parameter-label">Maximum Peak Speed</label>
                        <p class="parameter-description">
                          Upper limit for train speeds
                        </p>
                        <div class="control-value">{{ prefs.trainMaxMaxSpeed }} km/h</div>
                        <SliderControl
                          v-if="prefs.trainMaxMaxSpeed !== undefined"
                          v-model="prefs.trainMaxMaxSpeed"
                          :min="100.0" :max="250.0" :step="10.0"
                          :labels="['100.0 km/h (Regional)', '180.0 km/h (Default)', '250.0 km/h (High-speed)']"
                          suffix=" km/h" :decimal-places="1"
                        />
                      </div>

                      <div class="parameter-group">
                        <label class="parameter-label">Maximum Speed Variance (Key Discriminator)</label>
                        <p class="parameter-description">
                          Trains have low variance (&lt; 15), cars have high variance (&gt; 25). This is the key to distinguishing trains from cars!
                        </p>
                        <div class="control-value">{{ prefs.trainMaxSpeedVariance }}</div>
                        <SliderControl
                          v-if="prefs.trainMaxSpeedVariance !== undefined"
                          v-model="prefs.trainMaxSpeedVariance"
                          :min="5.0" :max="30.0" :step="1.0"
                          :labels="['5.0 (Strict)', '15.0 (Default)', '30.0 (Lenient)']"
                          :decimal-places="1"
                        />
                      </div>
                    </template>
                  </TransportTypeCard>

                  <!-- Flight Classification -->
                  <TransportTypeCard
                    type="flight"
                    title="Flight"
                    subtitle="Optional transport type"
                    icon="pi pi-send"
                    description="Detects air travel by very high speeds (400+ km/h avg OR 500+ km/h peak). OR logic handles extended taxi/ground time. GPS noise above 1200 km/h is automatically rejected."
                    v-model:enabled="prefs.flightEnabled"
                    :collapsible="true"
                    :validation-messages="getWarningMessagesForType('flight').value"
                  >
                    <template #parameters>
                      <div class="parameter-group">
                        <label class="parameter-label">Minimum Average Speed</label>
                        <p class="parameter-description">
                          Conservative default for typical flights (including taxi/takeoff/landing time)
                        </p>
                        <div class="control-value">{{ prefs.flightMinAvgSpeed }} km/h</div>
                        <SliderControl
                          v-if="prefs.flightMinAvgSpeed !== undefined"
                          v-model="prefs.flightMinAvgSpeed"
                          :min="250.0" :max="600.0" :step="50.0"
                          :labels="['250.0 km/h (Regional)', '400.0 km/h (Default)', '600.0 km/h (Long-haul)']"
                          suffix=" km/h" :decimal-places="1"
                        />
                      </div>

                      <div class="parameter-group">
                        <label class="parameter-label">Minimum Peak Speed</label>
                        <p class="parameter-description">
                          Catches flights with long taxi/wait time (OR logic with avg speed)
                        </p>
                        <div class="control-value">{{ prefs.flightMinMaxSpeed }} km/h</div>
                        <SliderControl
                          v-if="prefs.flightMinMaxSpeed !== undefined"
                          v-model="prefs.flightMinMaxSpeed"
                          :min="400.0" :max="900.0" :step="50.0"
                          :labels="['400.0 km/h (Turboprop)', '500.0 km/h (Default)', '900.0 km/h (Jet)']"
                          suffix=" km/h" :decimal-places="1"
                        />
                      </div>
                    </template>
                  </TransportTypeCard>

                  <!-- Short Distance Threshold -->
                  <SettingCard
                    title="Short Trip Distance Threshold"
                    description="Distance threshold for applying relaxed walking speed detection"
                    details="Trips shorter than this distance get slightly more lenient walking speed classification to account for GPS inaccuracies"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.shortDistanceKm }} km</div>
                      <SliderControl
                        v-if="prefs.shortDistanceKm !== undefined"
                        v-model="prefs.shortDistanceKm"
                        :min="0.1" :max="3.0" :step="0.1"
                        :labels="['0.1 km (Strict)', '1.0 km (Normal)', '3.0 km (Lenient)']"
                        suffix=" km" :decimal-places="1"
                      />
                    </template>
                  </SettingCard>

                  <!-- Trip Arrival Detection Duration -->
                  <SettingCard
                    title="Arrival Detection Duration"
                    description="Minimum time GPS points must be clustered and slow to detect arrival at destination"
                    :details="{
                      'Lower values': 'More sensitive arrival detection, may catch brief stops',
                      'Higher values': 'More conservative, only detect sustained arrivals'
                    }"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.tripArrivalDetectionMinDurationSeconds }} seconds</div>
                      <SliderControl
                        v-if="prefs.tripArrivalDetectionMinDurationSeconds !== undefined"
                        v-model="prefs.tripArrivalDetectionMinDurationSeconds"
                        :min="10" :max="300" :step="10"
                        :labels="['10s (Sensitive)', '90s (Normal)', '300s (Conservative)']"
                        suffix=" s" :decimal-places="0"
                      />
                    </template>
                  </SettingCard>

                  <!-- Trip Sustained Stop Duration -->
                  <SettingCard
                    title="Sustained Stop Duration"
                    description="Minimum time for consistent slow movement to detect a real stop (filters traffic lights)"
                    :details="{
                      'Lower values': 'Detect shorter stops, may include traffic delays',
                      'Higher values': 'Only detect longer stops, better traffic filtering'
                    }"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.tripSustainedStopMinDurationSeconds }} seconds</div>
                      <SliderControl
                        v-if="prefs.tripSustainedStopMinDurationSeconds !== undefined"
                        v-model="prefs.tripSustainedStopMinDurationSeconds"
                        :min="10" :max="600" :step="10"
                        :labels="['10s (Sensitive)', '60s (Normal)', '600s (Conservative)']"
                        suffix=" s" :decimal-places="0"
                      />
                    </template>
                  </SettingCard>
                </div>
              </div>
          </div>

          <!-- GPS Gaps Detection Tab -->
          <div v-if="activeTab === 'gpsgaps'">
              <div class="preferences-section">
                <div class="section-header">
                  <h2 class="section-title">GPS Gaps Detection Settings</h2>
                  <p class="section-description">
                    Configure how GPS data gaps are detected and recorded in your timeline
                  </p>
                </div>

                <div class="settings-grid">
                  <!-- Data Gap Threshold -->
                  <SettingCard
                    title="Data Gap Threshold"
                    description="Maximum time gap in seconds allowed between GPS points before considering it a GPS data gap"
                    details="When the time difference between two consecutive GPS points exceeds this threshold, a GPS Data Gap entity will be created instead of extending the current stay or trip. This prevents artificial extension of activities during periods of missing GPS data."
                  >
                    <template #control>
                      <div class="control-value">{{ Math.floor(prefs.dataGapThresholdSeconds / 60) }} minutes ({{ prefs.dataGapThresholdSeconds }}s)</div>
                      <SliderControl
                        v-if="prefs.dataGapThresholdSeconds !== undefined"
                        v-model="prefs.dataGapThresholdSeconds"
                        :min="300"
                        :max="86400"
                        :step="300"
                        :labels="['5 min (Sensitive)', '3 hours (Balanced)', '6 hours (Lenient)']"
                        :suffix="' seconds'"
                        :input-min="60"
                        :input-max="604800"
                        :decimal-places="0"
                      />
                    </template>
                  </SettingCard>

                  <!-- Min Duration for Gap Recording -->
                  <SettingCard
                    title="Minimum Gap Duration"
                    description="Minimum duration in seconds for a gap to be recorded as a GPS Data Gap"
                    details="Gaps shorter than this threshold will be ignored to reduce noise. This prevents very short connectivity issues from creating unnecessary gap records."
                  >
                    <template #control>
                      <div class="control-value">{{ Math.floor(prefs.dataGapMinDurationSeconds / 60) }} minutes ({{ prefs.dataGapMinDurationSeconds }}s)</div>
                      <SliderControl
                        v-if="prefs.dataGapMinDurationSeconds !== undefined"
                        v-model="prefs.dataGapMinDurationSeconds"
                        :min="300"
                        :max="7200"
                        :step="300"
                        :labels="['5 min (Strict)', '30 min (Balanced)', '2 hours (Lenient)']"
                        :suffix="' seconds'"
                        :input-min="60"
                        :input-max="14400"
                        :decimal-places="0"
                      />
                    </template>
                  </SettingCard>

                  <!-- Gap Stay Inference -->
                  <SettingCard
                    title="Gap Stay Inference"
                    description="Infer stays when GPS data gaps occur but locations before and after are the same"
                    :details="{
                      'When enabled': 'If you were at a location and GPS data stops, then resumes at the same location, the system infers you stayed there',
                      'Use case': 'Overnight gaps at home will show as a stay instead of a data gap'
                    }"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.gapStayInferenceEnabled ? 'Enabled' : 'Disabled' }}</div>
                      <ToggleSwitch
                        v-model="prefs.gapStayInferenceEnabled"
                        class="toggle-control"
                      />
                    </template>
                  </SettingCard>

                  <!-- Gap Stay Inference Max Gap Hours -->
                  <SettingCard
                    v-if="prefs.gapStayInferenceEnabled"
                    title="Maximum Gap Duration for Inference"
                    description="Maximum duration of GPS data gap to infer a stay"
                    :details="{
                      'Lower values': 'Only infer stays for shorter gaps (e.g., brief phone downtime)',
                      'Higher values': 'Infer stays for longer gaps (e.g., overnight, full day)'
                    }"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.gapStayInferenceMaxGapHours }} hours</div>
                      <SliderControl
                        v-if="prefs.gapStayInferenceMaxGapHours !== undefined"
                        v-model="prefs.gapStayInferenceMaxGapHours"
                        :min="1"
                        :max="72"
                        :step="1"
                        :labels="['1 hour (Strict)', '24 hours (Default)', '72 hours (Lenient)']"
                        suffix=" hours"
                        :input-min="1"
                        :input-max="168"
                        :decimal-places="0"
                      />
                    </template>
                  </SettingCard>
                </div>
              </div>
          </div>

          <!-- Stay Point Merging Tab -->
          <div v-if="activeTab === 'merging'">
              <div class="preferences-section">
                <div class="section-header">
                  <h2 class="section-title">Stay Point Merging Settings</h2>
                  <p class="section-description">
                    Configure how nearby stay points are consolidated into single locations
                  </p>
                </div>

                <div class="settings-grid">
                  <!-- Enable Merging -->
                  <SettingCard
                    title="Enable Stay Point Merging"
                    description="Whether to merge nearby stay points that are close in time and distance"
                    details="Helps consolidate multiple GPS clusters at the same general location into single stay points"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.isMergeEnabled ? 'Enabled' : 'Disabled' }}</div>
                      <ToggleSwitch
                        v-model="prefs.isMergeEnabled"
                        class="toggle-control"
                      />
                    </template>
                  </SettingCard>

                  <!-- Max Merge Distance -->
                  <SettingCard
                    v-if="prefs.isMergeEnabled"
                    title="Maximum Merge Distance"
                    description="Maximum distance between stay points to consider them for merging"
                    :details="{
                      'Lower values': 'Only merge very close points',
                      'Higher values': 'Merge points further apart'
                    }"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.mergeMaxDistanceMeters }}m</div>
                      <SliderControl
                        v-if="prefs.mergeMaxDistanceMeters !== undefined"
                        v-model="prefs.mergeMaxDistanceMeters"
                        :min="20"
                        :max="500"
                        :step="10"
                        :labels="['20m (Precise)', '150m (Balanced)', '500m (Generous)']"
                        suffix=" m"
                        :input-min="10"
                        :input-max="1000"
                        :decimal-places="0"
                      />
                    </template>
                  </SettingCard>

                  <!-- Max Time Gap -->
                  <SettingCard
                    v-if="prefs.isMergeEnabled"
                    title="Maximum Time Gap"
                    description="Maximum time gap between stay points to consider them for merging"
                    :details="{
                      'Lower values': 'Only merge consecutive stays',
                      'Higher values': 'Merge stays separated by longer gaps'
                    }"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.mergeMaxTimeGapMinutes }} minutes</div>
                      <SliderControl
                        v-if="prefs.mergeMaxTimeGapMinutes !== undefined"
                        v-model="prefs.mergeMaxTimeGapMinutes"
                        :min="1"
                        :max="60"
                        :step="1"
                        :labels="['1 min (Strict)', '10 min (Balanced)', '60 min (Generous)']"
                        suffix=" min"
                        :input-min="1"
                        :input-max="300"
                        :decimal-places="0"
                      />
                    </template>
                  </SettingCard>
                </div>
              </div>
          </div>

          <!-- GPS Path Simplification Tab -->
          <div v-if="activeTab === 'pathsimplification'">
              <div class="preferences-section">
                <div class="section-header">
                  <h2 class="section-title">GPS Path Simplification Settings</h2>
                  <p class="section-description">
                    Configure how GPS paths are simplified to reduce data while preserving route accuracy
                  </p>
                </div>

                <div class="settings-grid">
                  <!-- Enable Path Simplification -->
                  <SettingCard
                    title="Enable Path Simplification"
                    description="Whether GPS path simplification is enabled for timeline trips"
                    details="When enabled, trip paths will be simplified using the Douglas-Peucker algorithm to reduce the number of GPS points while preserving route accuracy"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.pathSimplificationEnabled ? 'Enabled' : 'Disabled' }}</div>
                      <ToggleSwitch
                        v-model="prefs.pathSimplificationEnabled"
                        class="toggle-control"
                      />
                    </template>
                  </SettingCard>

                  <!-- Simplification Tolerance -->
                  <SettingCard
                    v-if="prefs.pathSimplificationEnabled"
                    title="Simplification Tolerance"
                    description="Base tolerance in meters for GPS path simplification"
                    :details="{
                      'Lower values': 'Preserve more detail, less compression',
                      'Higher values': 'More compression, less detail'
                    }"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.pathSimplificationTolerance }}m</div>
                      <SliderControl
                        v-if="prefs.pathSimplificationTolerance !== undefined"
                        v-model="prefs.pathSimplificationTolerance"
                        :min="1"
                        :max="50"
                        :step="1"
                        :labels="['1m (High detail)', '15m (Balanced)', '50m (High compression)']"
                        suffix=" m"
                        :input-min="1"
                        :input-max="100"
                        :decimal-places="0"
                      />
                    </template>
                  </SettingCard>

                  <!-- Maximum Points -->
                  <SettingCard
                    v-if="prefs.pathSimplificationEnabled"
                    title="Maximum Points"
                    description="Maximum number of GPS points to retain in simplified paths"
                    details="If a simplified path still exceeds this limit, tolerance will be automatically increased until the limit is met. Set to 0 for no limit"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.pathMaxPoints === 0 ? 'No limit' : prefs.pathMaxPoints + ' points' }}</div>
                      <SliderControl
                        v-if="prefs.pathMaxPoints !== undefined"
                        v-model="prefs.pathMaxPoints"
                        :min="0"
                        :max="500"
                        :step="10"
                        :labels="['0 (No limit)', '100 (Balanced)', '500 (High limit)']"
                        :suffix="prefs.pathMaxPoints === 0 ? '' : ' points'"
                        :input-min="0"
                        :input-max="1000"
                        :decimal-places="0"
                      />
                    </template>
                  </SettingCard>

                  <!-- Adaptive Simplification -->
                  <SettingCard
                    v-if="prefs.pathSimplificationEnabled"
                    title="Adaptive Simplification"
                    description="Enables adaptive simplification that adjusts tolerance based on trip characteristics"
                    details="When enabled, longer trips use higher tolerance values for better compression while shorter trips maintain higher accuracy with lower tolerance"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.pathAdaptiveSimplification ? 'Enabled' : 'Disabled' }}</div>
                      <ToggleSwitch
                        v-model="prefs.pathAdaptiveSimplification"
                        class="toggle-control"
                      />
                    </template>
                  </SettingCard>
                </div>
              </div>
          </div>
        </TabContainer>

        <!-- Confirm Dialog -->
        <ConfirmDialog />
        <Toast />
        
        <!-- Timeline Regeneration Modal -->
        <TimelineRegenerationModal
          v-model:visible="timelineRegenerationVisible"
          :type="timelineRegenerationType"
          :job-id="currentJobId"
          :job-progress="jobProgress"
        />
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'

// Layout components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'

// Custom components
import SettingCard from '@/components/ui/forms/SettingCard.vue'
import SliderControl from '@/components/ui/forms/SliderControl.vue'
import TransportTypeCard from '@/components/ui/forms/TransportTypeCard.vue'
import TimelineRegenerationModal from '@/components/dialogs/TimelineRegenerationModal.vue'

import { useTimelinePreferencesStore } from '@/stores/timelinePreferences'
import { useTimelineStore } from '@/stores/timeline'
import { useTimelineRegeneration } from '@/composables/useTimelineRegeneration'
import { useClassificationValidation } from '@/composables/useClassificationValidation'

// Store
const router = useRouter()
const toast = useToast()
const confirm = useConfirm()
const timelinePreferencesStore = useTimelinePreferencesStore()
const timelineStore = useTimelineStore()

// Composables
const {
  timelineRegenerationVisible,
  timelineRegenerationType,
  currentJobId,
  jobProgress,
  withTimelineRegeneration
} = useTimelineRegeneration()

// Validation
const {
  validationWarnings,
  hasWarnings,
  hasErrors,
  getWarningMessagesForType
} = useClassificationValidation(computed(() => prefs.value))

// Store refs
const { timelinePreferences: originalPrefs } = storeToRefs(timelinePreferencesStore)

// State
const activeTab = ref('staypoints')

// Tab configuration
const tabItems = ref([
  {
    label: 'Stay Point Detection',
    icon: 'pi pi-map-marker',
    key: 'staypoints'
  },
  {
    label: 'Trip Classification',
    icon: 'pi pi-route',
    key: 'trips'
  },
  {
    label: 'GPS Gaps Detection',
    icon: 'pi pi-exclamation-circle',
    key: 'gpsgaps'
  },
  {
    label: 'Stay Point Merging',
    icon: 'pi pi-sitemap',
    key: 'merging'
  },
  {
    label: 'GPS Path Simplification',
    icon: 'pi pi-share-alt',
    key: 'pathsimplification'
  }
])

const activeTabIndex = computed(() => {
  return tabItems.value.findIndex(tab => tab.key === activeTab.value)
})
const loading = ref(false)
const regenerateLoading = ref(false)

const prefs = ref({})

// Algorithm options
const algorithmOptions = [
  { label: 'Simple Algorithm', value: 'simple' },
  { label: 'Enhanced Algorithm', value: 'enhanced' }
]

const tripsAlgorithmOptions = [
  { label: 'Single trip', value: 'single' },
  { label: 'Multiple trips', value: 'multiple' }
]

// Computed
const hasUnsavedChanges = computed(() => {
  return JSON.stringify(prefs.value) !== JSON.stringify(originalPrefs.value)
})

const isFormValid = computed(() => {
  // Basic validation - can be extended
  return Object.values(prefs.value).every(val => val !== null && val !== undefined)
})

// Methods
const handleTabChange = (event) => {
  const selectedTab = tabItems.value[event.index]
  if (selectedTab) {
    activeTab.value = selectedTab.key
  }
}

const getChangedPrefs = () => {
  const changed = {}
  
  for (const key in prefs.value) {
    const currentValue = prefs.value[key]
    const originalValue = originalPrefs.value?.[key]
    
    // Skip null values (they indicate no change requested)
    if (currentValue === null) {
      continue
    }
    
    // Only include fields that have actually changed from their original values
    if (currentValue !== originalValue) {
      changed[key] = currentValue
    }
  }
  return changed
}

const loadPreferences = async () => {
  try {
    await timelinePreferencesStore.fetchTimelinePreferences()
  } catch (error) {
    console.error('Error loading preferences:', error)
    toast.add({
      severity: 'error',
      summary: 'Loading Failed',
      detail: 'Failed to load timeline preferences',
      life: 5000
    })
  }
}

const confirmSavePreferences = () => {
  if (!isFormValid.value) {
    return;
  }

  const changes = getChangedPrefs()
  if (Object.keys(changes).length === 0) {
    toast.add({
      severity: 'info',
      summary: 'No Changes',
      detail: 'No preferences were modified',
      life: 3000
    })
    return
  }

  // Categorize changes
  const hasClassificationChanges = hasClassificationParameters(changes)
  const hasStructuralChanges = hasStructuralParameters(changes)
  
  if (hasClassificationChanges && !hasStructuralChanges) {
    // Fast path - classification only
    confirm.require({
      message: 'These changes will recalculate movement types for your existing trips. Do you want to proceed?',
      header: 'Update Trip Classifications',
      icon: 'pi pi-refresh',
      rejectProps: {
        label: 'Cancel',
        severity: 'secondary',
        outlined: true
      },
      acceptProps: {
        label: 'Update Classifications',
        severity: 'success'
      },
      accept: () => savePreferences('classification')
    })
  } else {
    // Full regeneration path (current behavior)
    confirm.require({
      message: 'Changing these timeline preferences will trigger a complete re-generation of all your timeline data according to the new settings. This process may take some time depending on the volume of your GPS data. Do you want to proceed?',
      header: 'Save Timeline Preferences', 
      icon: 'pi pi-exclamation-triangle',
      rejectProps: {
        label: 'Cancel',
        severity: 'secondary',
        outlined: true
      },
      acceptProps: {
        label: 'Save & Regenerate',
        severity: 'primary'
      },
      accept: () => savePreferences('full')
    })
  }
}

// Parameter categorization functions
const hasClassificationParameters = (changes) => {
  const classificationFields = [
    'walkingMaxAvgSpeed', 'walkingMaxMaxSpeed',
    'carMinAvgSpeed', 'carMinMaxSpeed', 'shortDistanceKm',
    // Bicycle
    'bicycleEnabled', 'bicycleMinAvgSpeed', 'bicycleMaxAvgSpeed', 'bicycleMaxMaxSpeed',
    // Train
    'trainEnabled', 'trainMinAvgSpeed', 'trainMaxAvgSpeed', 'trainMinMaxSpeed',
    'trainMaxMaxSpeed', 'trainMaxSpeedVariance',
    // Flight
    'flightEnabled', 'flightMinAvgSpeed', 'flightMinMaxSpeed'
  ]
  return classificationFields.some(field => field in changes)
}

const hasStructuralParameters = (changes) => {
  const structuralFields = [
    'staypointVelocityThreshold', 'staypointRadiusMeters',
    'staypointMinDurationMinutes', 'tripDetectionAlgorithm',
    'useVelocityAccuracy', 'staypointMaxAccuracyThreshold', 'staypointMinAccuracyRatio',
    'isMergeEnabled', 'mergeMaxDistanceMeters', 'mergeMaxTimeGapMinutes',
    'pathSimplificationEnabled', 'pathSimplificationTolerance',
    'pathMaxPoints', 'pathAdaptiveSimplification',
    'dataGapThresholdSeconds', 'dataGapMinDurationSeconds',
    'gapStayInferenceEnabled', 'gapStayInferenceMaxGapHours',
    'tripArrivalDetectionMinDurationSeconds', 'tripSustainedStopMinDurationSeconds'
  ]
  return structuralFields.some(field => field in changes)
}

const savePreferences = async (saveType = 'full') => {
  if (!isFormValid.value) return

  // Capture changes immediately to avoid closure issues
  const changes = getChangedPrefs()

  if (saveType === 'classification') {
    // Fast path: classification-only updates don't need job tracking
    try {
      await timelinePreferencesStore.updateTimelinePreferences(changes)
      toast.add({
        severity: 'success',
        summary: 'Success',
        detail: 'Trip classifications updated successfully.',
        life: 3000
      })
      await loadPreferences()
    } catch (error) {
      console.error('Failed to save preferences:', error)
      toast.add({
        severity: 'error',
        summary: 'Error',
        detail: error.message || 'Failed to save preferences.',
        life: 5000
      })
    }
  } else {
    // Full regeneration path: requires job tracking
    const action = () => {
      return timelinePreferencesStore.updateTimelinePreferences(changes)
    }

    withTimelineRegeneration(
      action,
      {
        modalType: 'preferences',
        successMessage: 'Preferences saved and timeline regeneration started.',
        errorMessage: 'Failed to save preferences.',
        onSuccess: loadPreferences
      }
    )
  }
}

const confirmResetDefaults = () => {
  confirm.require({
    message: 'This will reset all settings to their default values. Are you sure?',
    header: 'Reset to Defaults',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: {
      label: 'Cancel',
      severity: 'secondary',
      outlined: true
    },
    acceptProps: {
      label: 'Reset',
      severity: 'danger'
    },
    accept: resetDefaults
  })
}

const resetDefaults = () => {
  withTimelineRegeneration(
    () => timelinePreferencesStore.resetTimelinePreferencesToDefaults(),
    {
      modalType: 'preferences',
      successMessage: 'Preferences reset and timeline regeneration started.',
      errorMessage: 'Failed to reset preferences.',
      onSuccess: loadPreferences
    }
  )
}

const discardChanges = () => {
  if (originalPrefs.value) {
    prefs.value = { ...originalPrefs.value }
    toast.add({
      severity: 'info',
      summary: 'Changes Discarded',
      detail: 'All unsaved changes have been discarded',
      life: 3000
    })
  }
}

const confirmRegenerateTimeline = () => {
  confirm.require({
    message: 'This will completely delete your current timeline data and regenerate it from scratch.\n\nThis operation may take several minutes depending on your GPS data volume.\n\nDo you want to proceed?',
    header: 'Regenerate Complete Timeline',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: {
      label: 'Cancel',
      severity: 'secondary',
      outlined: true
    },
    acceptProps: {
      label: 'Regenerate Timeline',
      severity: 'danger'
    },
    accept: regenerateTimeline
  })
}

const regenerateTimeline = () => {
  withTimelineRegeneration(
    () => timelineStore.regenerateAllTimeline(),
    {
      modalType: 'general',
      successMessage: 'Timeline regeneration started.',
      errorMessage: 'Failed to start timeline regeneration.'
    }
  )
}

const goToActiveJob = () => {
  router.push('/app/timeline/jobs')
}

watch(originalPrefs, (newVal) => {
  if (newVal) {
    prefs.value = { ...newVal }
  }
}, { immediate: true })

// Lifecycle
onMounted(() => {
  loadPreferences()
})
</script>

<style scoped>
.timeline-preferences-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1rem;
  width: 100%;
  box-sizing: border-box;
}

@media (max-width: 430px) {
  .timeline-preferences-page {
    padding: 0 0.75rem;
    max-width: calc(100vw - 1.5rem);
    box-sizing: border-box;
  }
}

/* Page Header */
.page-header {
  margin-bottom: 2rem;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 2rem;
}

.header-text {
  flex: 1;
}

.page-title {
  font-size: 2rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.page-description {
  font-size: 1.1rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.5;
}

.header-actions {
  display: flex;
  gap: 1rem;
  flex-shrink: 0;
}

/* Info Banner */
.info-banner {
  margin-bottom: 2rem;
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-medium);
  border-left: 4px solid var(--gp-primary);
  border-radius: var(--gp-radius-large);
}

.p-dark .info-banner {
  margin-bottom: 2rem;
  background: var(--gp-surface-dark) !important;
  border: 1px solid var(--gp-border-dark) !important;
  border-left: 1px solid var(--gp-border-dark) !important;
  border-radius: var(--gp-radius-large) !important;
}

.banner-content {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
}

.banner-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  background: var(--gp-primary);
  color: white;
  border-radius: 50%;
  font-size: 1.25rem;
  flex-shrink: 0;
}

.banner-text {
  flex: 1;
}

.banner-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.banner-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.4;
}

/* Unsaved Changes Warning */
.unsaved-warning {
  margin-bottom: 2rem;
}

.warning-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}

.warning-text {
  display: flex;
  align-items: center;
  font-weight: 500;
}

.warning-actions {
  display: flex;
  gap: 0.5rem;
  flex-shrink: 0;
}

/* Preferences Tabs */
.preferences-tabs {
  margin-bottom: 2rem;
}

/* Preferences Section */
.preferences-section {
  padding: 2rem 0;
}

.section-header {
  margin-bottom: 2rem;
  text-align: center;
}

.section-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.section-description {
  font-size: 1rem;
  color: var(--gp-text-secondary);
  line-height: 1.5;
  max-width: 600px;
  margin: 0 auto;
  word-wrap: break-word;
  overflow-wrap: break-word;
  hyphens: auto;
}

/* Settings Grid */
.settings-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.5rem;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

/* Control Value Display */
.control-value {
  display: inline-block;
  padding: 0.5rem 1rem;
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  font-family: var(--font-mono, monospace);
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 1rem;
  min-width: 120px;
  text-align: center;
  width: 100%;
  box-sizing: border-box;
  max-width: 100%;
}

.toggle-control {
  margin-top: 0.5rem;
}

/* Input and Button Styling */
:deep(.p-dropdown) {
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-medium);
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

:deep(.p-dropdown:focus) {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.1);
}

:deep(.p-toggleswitch) {
  width: 3rem;
  height: 1.75rem;
}

:deep(.p-toggleswitch.p-toggleswitch-checked .p-toggleswitch-slider) {
  background: var(--gp-primary);
}

:deep(.p-button) {
  border-radius: var(--gp-radius-medium);
  font-weight: 600;
  transition: all 0.2s ease;
}

:deep(.p-button:not(.p-button-outlined)) {
  background: var(--gp-primary);
  border-color: var(--gp-primary);
}

:deep(.p-button:not(.p-button-outlined):hover) {
  background: var(--gp-primary-hover);
  border-color: var(--gp-primary-hover);
  transform: translateY(-1px);
  box-shadow: var(--gp-shadow-medium);
}

/* Danger button styling */
:deep(.p-button.p-button-danger.p-button-outlined) {
  border-color: #dc3545;
  color: #dc3545;
}

:deep(.p-button.p-button-danger.p-button-outlined:hover) {
  background: #dc3545;
  color: white;
  transform: translateY(-1px);
  box-shadow: var(--gp-shadow-medium);
}

/* Responsive Design */
@media (max-width: 768px) {
  .timeline-preferences-page {
    padding: 0 1rem;
    margin: 0 auto;
    width: 100%;
    max-width: 100vw;
    box-sizing: border-box;
  }
  
  .page-title {
    font-size: 1.5rem;
  }
  
  .header-content {
    flex-direction: column;
    align-items: stretch;
    gap: 1.5rem;
  }
  
  .header-actions {
    justify-content: stretch;
    flex-wrap: wrap;
  }
  
  .header-actions .p-button {
    flex: 1;
    min-height: 44px;
  }
  
  .banner-content {
    flex-direction: column;
    text-align: center;
    gap: 1rem;
  }
  
  .warning-content {
    flex-direction: column;
    align-items: stretch;
    gap: 1rem;
  }
  
  .warning-actions {
    justify-content: center;
  }
  
  .warning-actions .p-button {
    min-height: 44px;
  }
  
  .section-header {
    text-align: left;
    margin-bottom: 1.5rem;
  }
  
  .section-title {
    font-size: 1.3rem;
  }
  
  .section-description {
    font-size: 0.9rem;
    max-width: 100%;
    padding: 0 0.5rem;
    word-wrap: break-word;
    overflow-wrap: break-word;
  }
  
  .preferences-section {
    padding: 1.5rem 0;
  }
  
  .settings-grid {
    gap: 1.25rem;
    padding: 0;
    margin: 0;
  }
  
  .preferences-section {
    padding: 1.5rem 0;
    width: 100%;
    overflow: hidden;
  }
  
  :deep(.p-tabs-tab) {
    padding: 1rem 0.75rem;
    font-size: 0.85rem;
    min-height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  
  :deep(.p-tabs-nav) {
    justify-content: space-around;
  }
  
  :deep(.p-tabs-tab .p-tabs-tab-content) {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.25rem;
  }
}

@media (max-width: 480px) {
  .timeline-preferences-page {
    padding: 0 0.75rem;
    max-width: calc(100vw - 1.5rem);
  }
  
  .page-header {
    margin-bottom: 1.5rem;
  }
  
  .page-title {
    font-size: 1.3rem;
  }
  
  .page-description {
    font-size: 1rem;
  }
  
  .header-actions {
    flex-direction: column;
    gap: 0.75rem;
  }
  
  .header-actions .p-button {
    width: 100%;
    min-height: 48px;
    font-size: 0.95rem;
  }
  
  .banner-icon {
    margin: 0 auto;
    width: 2rem;
    height: 2rem;
    font-size: 1rem;
  }
  
  .banner-title {
    font-size: 1rem;
  }
  
  .banner-description {
    font-size: 0.85rem;
  }
  
  .section-title {
    font-size: 1.2rem;
  }
  
  .section-description {
    font-size: 0.85rem;
    max-width: 100%;
    padding: 0;
    margin: 0;
    word-wrap: break-word;
    overflow-wrap: break-word;
    line-height: 1.4;
  }
  
  .control-value {
    min-width: 0;
    font-size: 0.85rem;
    padding: 0.4rem 0.8rem;
    width: 100%;
    max-width: 100%;
    margin-bottom: 0.75rem;
  }
  
  :deep(.p-dropdown) {
    width: 100%;
    max-width: 100%;
    font-size: 0.9rem;
  }
  
  :deep(.p-dropdown .p-dropdown-label) {
    padding: 0.6rem 0.8rem;
    font-size: 0.9rem;
  }
  
  :deep(.p-toggleswitch) {
    align-self: center;
  }
  
  :deep(.p-tabs-tab) {
    padding: 0.75rem 0.5rem;
    font-size: 0.8rem;
    min-height: 48px;
  }
  
  :deep(.p-tabs-tab .pi) {
    font-size: 0.9rem;
  }
}

/* Speed Setting Groups */
.speed-setting-group {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  width: 100%;
}

.speed-setting {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.speed-setting .control-value {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  font-weight: 500;
  text-align: center;
  padding: 0.25rem 0;
}

@media (max-width: 768px) {
  .speed-setting-group {
    gap: 1.25rem;
  }
  
  .speed-setting .control-value {
    font-size: 0.85rem;
  }
}

@media (max-width: 480px) {
  .speed-setting-group {
    gap: 1rem;
  }

  .speed-setting .control-value {
    font-size: 0.8rem;
  }
}

/* Priority Info Banner */
.priority-info-banner {
  margin-bottom: 2rem;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: var(--gp-radius-large);
  color: white;
}

.priority-content {
  display: flex;
  align-items: flex-start;
  gap: 1.5rem;
}

.priority-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  flex-shrink: 0;
}

.priority-icon i {
  font-size: 1.5rem;
  color: white;
}

.priority-text {
  flex: 1;
}

.priority-title {
  font-size: 1.2rem;
  font-weight: 600;
  color: white;
  margin: 0 0 1rem 0;
}

.priority-flow {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.priority-step {
  padding: 0.5rem 1rem;
  background: rgba(255, 255, 255, 0.2);
  border-radius: var(--gp-radius-medium);
  font-weight: 500;
  font-size: 0.9rem;
  white-space: nowrap;
}

.priority-step.priority-unknown {
  background: rgba(255, 255, 255, 0.1);
  opacity: 0.8;
}

.priority-flow i {
  color: rgba(255, 255, 255, 0.6);
  font-size: 0.8rem;
}

.priority-description {
  font-size: 0.9rem;
  color: rgba(255, 255, 255, 0.9);
  margin: 0;
  line-height: 1.5;
}

/* Parameter Groups (within TransportTypeCard) */
.parameter-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.parameter-label {
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0;
}

.parameter-description {
  font-size: 0.85rem;
  color: var(--gp-text-secondary);
  margin: 0 0 0.5rem 0;
  line-height: 1.4;
}

/* Responsive: Priority Banner */
@media (max-width: 768px) {
  .priority-content {
    flex-direction: column;
    text-align: center;
  }

  .priority-icon {
    margin: 0 auto;
  }

  .priority-flow {
    justify-content: center;
  }
}

@media (max-width: 480px) {
  .priority-info-banner {
    margin-bottom: 1.5rem;
  }

  .priority-title {
    font-size: 1rem;
  }

  .priority-flow {
    gap: 0.5rem;
  }

  .priority-step {
    padding: 0.4rem 0.8rem;
    font-size: 0.8rem;
  }

  .priority-flow i {
    font-size: 0.7rem;
  }

  .priority-description {
    font-size: 0.85rem;
  }

  .parameter-label {
    font-size: 0.9rem;
  }

  .parameter-description {
    font-size: 0.8rem;
  }
}
</style>