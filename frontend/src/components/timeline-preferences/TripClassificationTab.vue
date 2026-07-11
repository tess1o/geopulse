<template>
  <PreferencesTabLayout
    title="Trip Classification Settings"
    description="Configure how trips are detected and classified by movement type"
  >
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
              <span class="priority-step">⛵ BOAT</span>
              <i class="pi pi-arrow-right"></i>
              <span class="priority-step">🚊 TRAIN</span>
              <i class="pi pi-arrow-right"></i>
              <span class="priority-step">🚴 BICYCLE</span>
              <i class="pi pi-arrow-right"></i>
              <span class="priority-step">🏃 RUNNING</span>
              <i class="pi pi-arrow-right"></i>
              <span class="priority-step">🚗/🏍️ MOTOR VEHICLE</span>
              <i class="pi pi-arrow-right"></i>
              <span class="priority-step">🚶 WALK</span>
              <i class="pi pi-arrow-right"></i>
              <span class="priority-step priority-unknown">❓ UNKNOWN</span>
            </div>
            <p class="priority-description">
              Trips are classified in priority order from top to bottom. Once a match is found, classification stops.
              This order handles water and speed evidence conservatively - e.g., over-water flights remain FLIGHT, and overlapping land speeds match BICYCLE before RUNNING and motor vehicles.
            </p>
          </div>
        </div>
      </template>
    </Card>

    <!-- Trip Detection Algorithm -->
    <SettingCard
      title="Trip Detection Algorithm"
      description="The algorithm used to identify trips between stay points"
      :details="{
        'Single': 'Always one trip between stay points',
        'Multiple': 'Based on velocity one or more trips between stay points (like CAR → WALK, WALK → CAR, etc)'
      }"
      setting-id="tripDetectionAlgorithm"
    >
      <template #control>
        <div class="control-value">{{ modelValue.tripDetectionAlgorithm }}</div>
        <Select
          :model-value="modelValue.tripDetectionAlgorithm"
          @update:model-value="updatePref('tripDetectionAlgorithm', $event)"
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
      setting-id="walkingMaxAvgSpeed"
    >
      <template #parameters>
        <div class="parameter-group" data-setting-id="walkingMaxAvgSpeed">
          <label class="parameter-label">Maximum Average Speed</label>
          <p class="parameter-description">
            Trips with average speeds above this will be classified as non-walking
          </p>
          <div class="control-value">{{ modelValue.walkingMaxAvgSpeed }} km/h</div>
          <SliderControl
            v-if="modelValue.walkingMaxAvgSpeed !== undefined"
            :model-value="modelValue.walkingMaxAvgSpeed"
            @update:model-value="updatePref('walkingMaxAvgSpeed', $event)"
            :min="3.0" :max="10.0" :step="0.5"
            :labels="['3.0 km/h (Slow)', '5.5 km/h (Normal)', '10.0 km/h (Fast)']"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="walkingMaxMaxSpeed">
          <label class="parameter-label">Maximum Peak Speed</label>
          <p class="parameter-description">
            Brief speed bursts above this will reclassify the trip
          </p>
          <div class="control-value">{{ modelValue.walkingMaxMaxSpeed }} km/h</div>
          <SliderControl
            v-if="modelValue.walkingMaxMaxSpeed !== undefined"
            :model-value="modelValue.walkingMaxMaxSpeed"
            @update:model-value="updatePref('walkingMaxMaxSpeed', $event)"
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
      description="Detects cycling trips (8-25 km/h typical range). Priority order ensures correct classification even with speed overlap with running and cars."
      :enabled="modelValue.bicycleEnabled"
      @update:enabled="updatePref('bicycleEnabled', $event)"
      :collapsible="true"
      :validation-messages="getWarningMessagesForType('bicycle').value"
      setting-id="bicycleEnabled"
    >
      <template #parameters>
        <div class="parameter-group" data-setting-id="bicycleMinAvgSpeed">
          <label class="parameter-label">Minimum Average Speed</label>
          <p class="parameter-description">
            Trips slower than this will be classified as running or walking
          </p>
          <div class="control-value">{{ modelValue.bicycleMinAvgSpeed }} km/h</div>
          <SliderControl
            v-if="modelValue.bicycleMinAvgSpeed !== undefined"
            :model-value="modelValue.bicycleMinAvgSpeed"
            @update:model-value="updatePref('bicycleMinAvgSpeed', $event)"
            :min="5.0" :max="15.0" :step="0.5"
            :labels="['5.0 km/h (Slow)', '8.0 km/h (Default)', '15.0 km/h (Fast)']"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="bicycleMaxAvgSpeed">
          <label class="parameter-label">Maximum Average Speed</label>
          <p class="parameter-description">
            Trips faster than this will be classified as motorized transport
          </p>
          <div class="control-value">{{ modelValue.bicycleMaxAvgSpeed }} km/h</div>
          <SliderControl
            v-if="modelValue.bicycleMaxAvgSpeed !== undefined"
            :model-value="modelValue.bicycleMaxAvgSpeed"
            @update:model-value="updatePref('bicycleMaxAvgSpeed', $event)"
            :min="15.0" :max="35.0" :step="1.0"
            :labels="['15.0 km/h (Slow)', '25.0 km/h (Default)', '35.0 km/h (E-bike)']"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="bicycleMaxMaxSpeed">
          <label class="parameter-label">Maximum Peak Speed</label>
          <p class="parameter-description">
            Allows for downhill segments or e-bikes, but below car speeds
          </p>
          <div class="control-value">{{ modelValue.bicycleMaxMaxSpeed }} km/h</div>
          <SliderControl
            v-if="modelValue.bicycleMaxMaxSpeed !== undefined"
            :model-value="modelValue.bicycleMaxMaxSpeed"
            @update:model-value="updatePref('bicycleMaxMaxSpeed', $event)"
            :min="20.0" :max="50.0" :step="5.0"
            :labels="['20.0 km/h (City)', '35.0 km/h (Default)', '50.0 km/h (E-bike)']"
            suffix=" km/h" :decimal-places="1"
          />
        </div>
      </template>
    </TransportTypeCard>

    <!-- Running Classification -->
    <TransportTypeCard
      type="running"
      title="Running"
      subtitle="Optional transport type"
      icon="pi pi-bolt"
      description="Detects running/jogging trips (7-14 km/h typical range). Conservative thresholds separate running from fast walking and slow cycling. When disabled, running speeds are captured by BICYCLE (if enabled) or CAR/UNKNOWN."
      :enabled="modelValue.runningEnabled"
      @update:enabled="updatePref('runningEnabled', $event)"
      :collapsible="true"
      :validation-messages="getWarningMessagesForType('running').value"
      setting-id="runningEnabled"
    >
      <template #parameters>
        <div class="parameter-group" data-setting-id="runningMinAvgSpeed">
          <label class="parameter-label">Minimum Average Speed</label>
          <p class="parameter-description">
            Trips slower than this will be classified as walking
          </p>
          <div class="control-value">{{ modelValue.runningMinAvgSpeed }} km/h</div>
          <SliderControl
            v-if="modelValue.runningMinAvgSpeed !== undefined"
            :model-value="modelValue.runningMinAvgSpeed"
            @update:model-value="updatePref('runningMinAvgSpeed', $event)"
            :min="5.0" :max="10.0" :step="0.5"
            :labels="['5.0 km/h (Slow)', '7.0 km/h (Default)', '10.0 km/h (Fast)']"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="runningMaxAvgSpeed">
          <label class="parameter-label">Maximum Average Speed</label>
          <p class="parameter-description">
            Trips faster than this will be classified as cycling or motorized transport
          </p>
          <div class="control-value">{{ modelValue.runningMaxAvgSpeed }} km/h</div>
          <SliderControl
            v-if="modelValue.runningMaxAvgSpeed !== undefined"
            :model-value="modelValue.runningMaxAvgSpeed"
            @update:model-value="updatePref('runningMaxAvgSpeed', $event)"
            :min="10.0" :max="18.0" :step="0.5"
            :labels="['10.0 km/h (Slow)', '14.0 km/h (Default)', '18.0 km/h (Fast)']"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="runningMaxMaxSpeed">
          <label class="parameter-label">Maximum Peak Speed</label>
          <p class="parameter-description">
            Allows for sprint segments while staying below cycling speeds
          </p>
          <div class="control-value">{{ modelValue.runningMaxMaxSpeed }} km/h</div>
          <SliderControl
            v-if="modelValue.runningMaxMaxSpeed !== undefined"
            :model-value="modelValue.runningMaxMaxSpeed"
            @update:model-value="updatePref('runningMaxMaxSpeed', $event)"
            :min="12.0" :max="25.0" :step="1.0"
            :labels="['12.0 km/h (Slow)', '18.0 km/h (Default)', '25.0 km/h (Sprint)']"
            suffix=" km/h" :decimal-places="1"
          />
        </div>
      </template>
    </TransportTypeCard>

    <!-- Motor Vehicle Classification -->
    <TransportTypeCard
      type="car"
      title="Motor Vehicle"
      subtitle="Car or motorcycle label"
      icon="pi pi-car"
      description="Detects car or motorcycle-like trips using shared motor vehicle speed thresholds. The selected label controls how matching trips are classified."
      :enabled="isMotorVehicleEnabled"
      @update:enabled="updateMotorVehicleEnabled"
      :collapsible="true"
      :validation-messages="getWarningMessagesForType('car').value"
      setting-id="carEnabled"
    >
      <template #parameters>
        <div class="motorized-label-controls">
          <div class="label-toggle" data-setting-id="carEnabled">
            <div>
              <label class="parameter-label">Car Label</label>
              <p class="parameter-description">Allow detected motor vehicle trips to be labeled as car.</p>
            </div>
            <ToggleSwitch
              :model-value="modelValue.carEnabled !== false"
              @update:model-value="updatePref('carEnabled', $event)"
              aria-label="Enable car label"
            />
          </div>

          <div class="label-toggle" data-setting-id="motorcycleEnabled">
            <div>
              <label class="parameter-label">Motorcycle Label</label>
              <p class="parameter-description">Allow detected motor vehicle trips to be labeled as motorcycle.</p>
            </div>
            <ToggleSwitch
              :model-value="modelValue.motorcycleEnabled === true"
              @update:model-value="updatePref('motorcycleEnabled', $event)"
              aria-label="Enable motorcycle label"
            />
          </div>
        </div>

        <div
          v-if="modelValue.carEnabled !== false && modelValue.motorcycleEnabled === true"
          class="parameter-group"
          data-setting-id="preferredMotorizedType"
        >
          <label class="parameter-label">Preferred Motor Vehicle Label</label>
          <p class="parameter-description">
            When both labels are enabled, detected motor vehicle trips use this label by default.
          </p>
          <div class="control-value">{{ formatPreferredMotorizedType(modelValue.preferredMotorizedType) }}</div>
          <Select
            :model-value="modelValue.preferredMotorizedType || 'CAR'"
            @update:model-value="updatePref('preferredMotorizedType', $event)"
            :options="preferredMotorizedTypeOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="Select preferred label"
            class="w-full"
          />
        </div>

        <div class="parameter-group" data-setting-id="carMinAvgSpeed">
          <label class="parameter-label">Minimum Average Speed</label>
          <p class="parameter-description">
            Trips with average speeds below this will be classified as walking, running, or bicycle if those match first
          </p>
          <div class="control-value">{{ modelValue.carMinAvgSpeed }} km/h</div>
          <SliderControl
            v-if="modelValue.carMinAvgSpeed !== undefined"
            :model-value="modelValue.carMinAvgSpeed"
            @update:model-value="updatePref('carMinAvgSpeed', $event)"
            :min="5.0" :max="25.0" :step="0.5"
            :labels="['5.0 km/h (Sensitive)', '10.0 km/h (Default)', '25.0 km/h (Conservative)']"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="carMinMaxSpeed">
          <label class="parameter-label">Minimum Peak Speed</label>
          <p class="parameter-description">
            Trips that never reach this speed will not be classified as a motor vehicle unless average speed matches
          </p>
          <div class="control-value">{{ modelValue.carMinMaxSpeed }} km/h</div>
          <SliderControl
            v-if="modelValue.carMinMaxSpeed !== undefined"
            :model-value="modelValue.carMinMaxSpeed"
            @update:model-value="updatePref('carMinMaxSpeed', $event)"
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
      :enabled="modelValue.trainEnabled"
      @update:enabled="updatePref('trainEnabled', $event)"
      :collapsible="true"
      :validation-messages="getWarningMessagesForType('train').value"
      setting-id="trainEnabled"
    >
      <template #parameters>
        <div class="parameter-group" data-setting-id="trainMinAvgSpeed">
          <label class="parameter-label">Minimum Average Speed</label>
          <p class="parameter-description">
            Separates from cars in heavy traffic
          </p>
          <div class="control-value">{{ modelValue.trainMinAvgSpeed }} km/h</div>
          <SliderControl
            v-if="modelValue.trainMinAvgSpeed !== undefined"
            :model-value="modelValue.trainMinAvgSpeed"
            @update:model-value="updatePref('trainMinAvgSpeed', $event)"
            :min="20.0" :max="50.0" :step="5.0"
            :labels="['20.0 km/h (City)', '30.0 km/h (Default)', '50.0 km/h (Fast)']"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="trainMaxAvgSpeed">
          <label class="parameter-label">Maximum Average Speed</label>
          <p class="parameter-description">
            Covers regional and intercity trains
          </p>
          <div class="control-value">{{ modelValue.trainMaxAvgSpeed }} km/h</div>
          <SliderControl
            v-if="modelValue.trainMaxAvgSpeed !== undefined"
            :model-value="modelValue.trainMaxAvgSpeed"
            @update:model-value="updatePref('trainMaxAvgSpeed', $event)"
            :min="80.0" :max="400.0" :step="10.0"
            :labels="['80.0 km/h (Regional)', '150.0 km/h (Default)', '400.0 km/h (High-speed)']"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="trainMinMaxSpeed">
          <label class="parameter-label">Minimum Peak Speed (Station Filter)</label>
          <p class="parameter-description">
            Filters out trips with only station waiting time (critical!)
          </p>
          <div class="control-value">{{ modelValue.trainMinMaxSpeed }} km/h</div>
          <SliderControl
            v-if="modelValue.trainMinMaxSpeed !== undefined"
            :model-value="modelValue.trainMinMaxSpeed"
            @update:model-value="updatePref('trainMinMaxSpeed', $event)"
            :min="60.0" :max="120.0" :step="10.0"
            :labels="['60.0 km/h (Lenient)', '80.0 km/h (Default)', '120.0 km/h (Strict)']"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="trainMaxMaxSpeed">
          <label class="parameter-label">Maximum Peak Speed</label>
          <p class="parameter-description">
            Upper limit for train speeds
          </p>
          <div class="control-value">{{ modelValue.trainMaxMaxSpeed }} km/h</div>
          <SliderControl
            v-if="modelValue.trainMaxMaxSpeed !== undefined"
            :model-value="modelValue.trainMaxMaxSpeed"
            @update:model-value="updatePref('trainMaxMaxSpeed', $event)"
            :min="100.0" :max="500.0" :step="10.0"
            :labels="['100.0 km/h (Regional)', '180.0 km/h (Default)', '500.0 km/h (High-speed)']"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="trainMaxSpeedVariance">
          <label class="parameter-label">Maximum Speed Variance (Key Discriminator)</label>
          <p class="parameter-description">
            Trains have low variance (&lt; 15), cars have high variance (&gt; 25). This is the key to distinguishing trains from cars!
          </p>
          <div class="control-value">{{ modelValue.trainMaxSpeedVariance }}</div>
          <SliderControl
            v-if="modelValue.trainMaxSpeedVariance !== undefined"
            :model-value="modelValue.trainMaxSpeedVariance"
            @update:model-value="updatePref('trainMaxSpeedVariance', $event)"
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
      :enabled="modelValue.flightEnabled"
      @update:enabled="updatePref('flightEnabled', $event)"
      :collapsible="true"
      :validation-messages="getWarningMessagesForType('flight').value"
      setting-id="flightEnabled"
    >
      <template #parameters>
        <div class="parameter-group" data-setting-id="flightMinAvgSpeed">
          <label class="parameter-label">Minimum Average Speed</label>
          <p class="parameter-description">
            Conservative default for typical flights (including taxi/takeoff/landing time)
          </p>
          <div class="control-value">{{ modelValue.flightMinAvgSpeed }} km/h</div>
          <SliderControl
            v-if="modelValue.flightMinAvgSpeed !== undefined"
            :model-value="modelValue.flightMinAvgSpeed"
            @update:model-value="updatePref('flightMinAvgSpeed', $event)"
            :min="250.0" :max="600.0" :step="50.0"
            :labels="['250.0 km/h (Regional)', '400.0 km/h (Default)', '600.0 km/h (Long-haul)']"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="flightMinMaxSpeed">
          <label class="parameter-label">Minimum Peak Speed</label>
          <p class="parameter-description">
            Catches flights with long taxi/wait time (OR logic with avg speed)
          </p>
          <div class="control-value">{{ modelValue.flightMinMaxSpeed }} km/h</div>
          <SliderControl
            v-if="modelValue.flightMinMaxSpeed !== undefined"
            :model-value="modelValue.flightMinMaxSpeed"
            @update:model-value="updatePref('flightMinMaxSpeed', $event)"
            :min="400.0" :max="900.0" :step="50.0"
            :labels="['400.0 km/h (Turboprop)', '500.0 km/h (Default)', '900.0 km/h (Jet)']"
            suffix=" km/h" :decimal-places="1"
          />
        </div>
      </template>
    </TransportTypeCard>

    <!-- Boat Classification -->
    <TransportTypeCard
      type="boat"
      title="Boat"
      subtitle="Optional transport type"
      icon="pi pi-compass"
      description="Detects boat trips from sustained water evidence. Speed is only used as a sanity check, not as proof of boat travel."
      :enabled="modelValue.boatEnabled"
      @update:enabled="updatePref('boatEnabled', $event)"
      :collapsible="true"
      :validation-messages="getWarningMessagesForType('boat').value"
      setting-id="boatEnabled"
    >
      <template #parameters>
        <Message
          v-if="modelValue.boatEnabled && boatSetupStatus"
          :severity="boatSetupSeverity"
          class="boat-setup-message"
        >
          <div class="boat-setup-content">
            <div>
              <strong>{{ boatSetupTitle }}</strong>
              <div v-if="boatSetupPhaseText" class="boat-setup-phase">{{ boatSetupPhaseText }}</div>
              <a
                v-if="boatSetupStatus.errorMessage && boatSetupStatus.docsUrl"
                :href="boatSetupStatus.docsUrl"
                target="_blank"
                rel="noopener noreferrer"
                class="boat-setup-docs-link"
              >
                Offline setup documentation <i class="pi pi-external-link"></i>
              </a>
            </div>
            <div v-if="boatSetupStatus.progressPercentage !== undefined" class="boat-setup-progress">
              {{ boatSetupStatus.progressPercentage }}%
            </div>
            <Button
              v-if="boatSetupCanStart"
              label="Retry"
              icon="pi pi-refresh"
              size="small"
              @click="$emit('retry-boat-setup')"
            />
          </div>
        </Message>

        <div class="parameter-group" data-setting-id="boatMinWaterRatio">
          <label class="parameter-label">Minimum Water Ratio</label>
          <p class="parameter-description">
            Share of usable trip distance that must be over water
          </p>
          <div class="control-value">{{ Math.round((modelValue.boatMinWaterRatio || 0) * 100) }}%</div>
          <SliderControl
            v-if="modelValue.boatMinWaterRatio !== undefined"
            :model-value="modelValue.boatMinWaterRatio"
            @update:model-value="updatePref('boatMinWaterRatio', $event)"
            :min="0.1" :max="1.0" :step="0.05"
            :labels="['10% (Lenient)', '60% (Default)', '100% (Strict)']"
            :decimal-places="2"
          />
        </div>

        <div class="parameter-group" data-setting-id="boatMinWaterDistanceMeters">
          <label class="parameter-label">Minimum Water Distance</label>
          <p class="parameter-description">
            Total water distance required before a trip can be classified as boat
          </p>
          <div class="control-value">{{ modelValue.boatMinWaterDistanceMeters }} m</div>
          <SliderControl
            v-if="modelValue.boatMinWaterDistanceMeters !== undefined"
            :model-value="modelValue.boatMinWaterDistanceMeters"
            @update:model-value="updatePref('boatMinWaterDistanceMeters', $event)"
            :min="100" :max="10000" :step="100"
            :labels="['100 m (Lenient)', '2000 m (Default)', '10000 m (Strict)']"
            suffix=" m" :decimal-places="0"
          />
        </div>

        <div class="parameter-group" data-setting-id="boatMinContinuousWaterDistanceMeters">
          <label class="parameter-label">Minimum Continuous Water Distance</label>
          <p class="parameter-description">
            Filters out short bridge, tunnel, and shoreline crossings
          </p>
          <div class="control-value">{{ modelValue.boatMinContinuousWaterDistanceMeters }} m</div>
          <SliderControl
            v-if="modelValue.boatMinContinuousWaterDistanceMeters !== undefined"
            :model-value="modelValue.boatMinContinuousWaterDistanceMeters"
            @update:model-value="updatePref('boatMinContinuousWaterDistanceMeters', $event)"
            :min="100" :max="10000" :step="100"
            :labels="['100 m (Lenient)', '1000 m (Default)', '10000 m (Strict)']"
            suffix=" m" :decimal-places="0"
          />
        </div>

        <div class="parameter-group" data-setting-id="boatMaxPlausibleSpeed">
          <label class="parameter-label">Maximum Plausible Speed</label>
          <p class="parameter-description">
            Sanity ceiling only; slow movement can still be boat travel
          </p>
          <div class="control-value">{{ modelValue.boatMaxPlausibleSpeed }} km/h</div>
          <SliderControl
            v-if="modelValue.boatMaxPlausibleSpeed !== undefined"
            :model-value="modelValue.boatMaxPlausibleSpeed"
            @update:model-value="updatePref('boatMaxPlausibleSpeed', $event)"
            :min="20" :max="300" :step="10"
            :labels="['20 km/h (Strict)', '120 km/h (Default)', '300 km/h (Lenient)']"
            suffix=" km/h" :decimal-places="0"
          />
        </div>
      </template>
    </TransportTypeCard>

    <!-- Short Distance Threshold -->
    <SettingCard
      title="Short Trip Distance Threshold"
      description="Distance threshold for applying relaxed walking speed detection"
      details="Trips shorter than this distance get slightly more lenient walking speed classification to account for GPS inaccuracies"
      setting-id="shortDistanceKm"
    >
      <template #control>
        <div class="control-value">{{ modelValue.shortDistanceKm }} km</div>
        <SliderControl
          v-if="modelValue.shortDistanceKm !== undefined"
          :model-value="modelValue.shortDistanceKm"
          @update:model-value="updatePref('shortDistanceKm', $event)"
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
      setting-id="tripArrivalDetectionMinDurationSeconds"
    >
      <template #control>
        <div class="control-value">{{ modelValue.tripArrivalDetectionMinDurationSeconds }} seconds</div>
        <SliderControl
          v-if="modelValue.tripArrivalDetectionMinDurationSeconds !== undefined"
          :model-value="modelValue.tripArrivalDetectionMinDurationSeconds"
          @update:model-value="updatePref('tripArrivalDetectionMinDurationSeconds', $event)"
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
      setting-id="tripSustainedStopMinDurationSeconds"
    >
      <template #control>
        <div class="control-value">{{ modelValue.tripSustainedStopMinDurationSeconds }} seconds</div>
        <SliderControl
          v-if="modelValue.tripSustainedStopMinDurationSeconds !== undefined"
          :model-value="modelValue.tripSustainedStopMinDurationSeconds"
          @update:model-value="updatePref('tripSustainedStopMinDurationSeconds', $event)"
          :min="10" :max="600" :step="10"
          :labels="['10s (Sensitive)', '60s (Normal)', '600s (Conservative)']"
          suffix=" s" :decimal-places="0"
        />
      </template>
    </SettingCard>

    <!-- Trip Arrival Minimum Points -->
    <SettingCard
      title="Minimum Stop Points for Arrival Detection"
      description="Minimum number of GPS points required to detect arrival at destination"
      :details="{
        'Lower values (2)': 'Faster detection, ideal for infrequent GPS (10-15 min intervals)',
        'Higher values (3-4)': 'More reliable, ideal for frequent GPS (30-60 sec intervals)'
      }"
      setting-id="tripArrivalMinPoints"
    >
      <template #control>
        <div class="control-value">{{ modelValue.tripArrivalMinPoints }} points</div>
        <SliderControl
          v-if="modelValue.tripArrivalMinPoints !== undefined"
          :model-value="modelValue.tripArrivalMinPoints"
          @update:model-value="updatePref('tripArrivalMinPoints', $event)"
          :min="2" :max="5" :step="1"
          :labels="['2 points (Fast)', '3 points (Balanced)', '5 points (Conservative)']"
          suffix=" points" :decimal-places="0"
        />
      </template>
    </SettingCard>
  </PreferencesTabLayout>
</template>

<script setup>
import './shared-styles.css'
import { computed } from 'vue'
import PreferencesTabLayout from './PreferencesTabLayout.vue'
import SettingCard from '@/components/ui/forms/SettingCard.vue'
import SliderControl from '@/components/ui/forms/SliderControl.vue'
import TransportTypeCard from '@/components/ui/forms/TransportTypeCard.vue'
import Card from 'primevue/card'
import Select from 'primevue/select'
import Message from 'primevue/message'
import Button from 'primevue/button'
import ToggleSwitch from 'primevue/toggleswitch'

const props = defineProps({
  modelValue: {
    type: Object,
    required: true
  },
  getWarningMessagesForType: {
    type: Function,
    required: true
  },
  boatSetupStatus: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'retry-boat-setup'])

const tripsAlgorithmOptions = [
  { label: 'Single trip', value: 'single' },
  { label: 'Multiple trips', value: 'multiple' }
]

const preferredMotorizedTypeOptions = [
  { label: 'Car', value: 'CAR' },
  { label: 'Motorcycle', value: 'MOTORCYCLE' }
]

const isMotorVehicleEnabled = computed(() => {
  return props.modelValue.carEnabled !== false || props.modelValue.motorcycleEnabled === true
})

const updatePref = (key, value) => {
  emit('update:modelValue', {
    ...props.modelValue,
    [key]: value
  })
}

const updateMotorVehicleEnabled = (enabled) => {
  if (enabled) {
    emit('update:modelValue', {
      ...props.modelValue,
      carEnabled: true
    })
    return
  }

  emit('update:modelValue', {
    ...props.modelValue,
    carEnabled: false,
    motorcycleEnabled: false
  })
}

const formatPreferredMotorizedType = (type) => {
  return type === 'MOTORCYCLE' ? 'Motorcycle' : 'Car'
}

const boatSetupSeverity = computed(() => {
  if (!props.boatSetupStatus) return 'warn'
  if (props.boatSetupStatus.status === 'READY') return 'success'
  if (props.boatSetupStatus.status === 'FAILED') return 'error'
  if (['QUEUED', 'RUNNING'].includes(props.boatSetupStatus.status)) return 'info'
  return 'warn'
})

const boatSetupTitle = computed(() => {
  if (!props.boatSetupStatus) return 'Boat setup required'
  if (props.boatSetupStatus.status === 'READY') return 'Boat setup ready'
  if (props.boatSetupStatus.status === 'FAILED') return 'Boat setup failed'
  if (['QUEUED', 'RUNNING'].includes(props.boatSetupStatus.status)) return 'Boat setup in progress'
  return 'Boat setup required'
})

const boatSetupPhaseText = computed(() => {
  if (!props.boatSetupStatus) return null
  if (props.boatSetupStatus.status === 'READY') return 'Cached water evidence is available.'
  return props.boatSetupStatus.phase
})

const boatSetupCanStart = computed(() => {
  return props.boatSetupStatus?.status === 'FAILED'
})
</script>

<style scoped>
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

.boat-setup-message {
  margin-bottom: 1rem;
}

.boat-setup-content {
  align-items: center;
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  width: 100%;
}

.boat-setup-phase {
  font-size: 0.9rem;
  margin-top: 0.25rem;
}

.boat-setup-progress {
  font-weight: 700;
  white-space: nowrap;
}

.boat-setup-docs-link {
  display: inline-block;
  margin-top: 0.35rem;
}

.motorized-label-controls {
  display: grid;
  gap: 1rem;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.label-toggle {
  align-items: center;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  display: flex;
  gap: 1rem;
  justify-content: space-between;
  padding: 1rem;
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
