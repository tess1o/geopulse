<template>
  <PreferencesTabLayout
    :title="t('timeline.preferences.tripClassification.title')"
    :description="t('timeline.preferences.tripClassification.description')"
  >
    <!-- Classification Priority Info Banner -->
    <Card class="priority-info-banner">
      <template #content>
        <div class="priority-content">
          <div class="priority-icon">
            <i class="pi pi-sort-amount-down"></i>
          </div>
          <div class="priority-text">
            <h3 class="priority-title">{{ t('timeline.preferences.tripClassification.priorityBanner.title') }}</h3>
            <div class="priority-flow">
              <span class="priority-step">✈️ {{ t('timeline.preferences.tripClassification.priorityBanner.steps.flight') }}</span>
              <i class="pi pi-arrow-right"></i>
              <span class="priority-step">⛵ {{ t('timeline.preferences.tripClassification.priorityBanner.steps.boat') }}</span>
              <i class="pi pi-arrow-right"></i>
              <span class="priority-step">🚊 {{ t('timeline.preferences.tripClassification.priorityBanner.steps.train') }}</span>
              <i class="pi pi-arrow-right"></i>
              <span class="priority-step">🚴 {{ t('timeline.preferences.tripClassification.priorityBanner.steps.bicycle') }}</span>
              <i class="pi pi-arrow-right"></i>
              <span class="priority-step">🏃 {{ t('timeline.preferences.tripClassification.priorityBanner.steps.running') }}</span>
              <i class="pi pi-arrow-right"></i>
              <span class="priority-step">🚗/🏍️ {{ t('timeline.preferences.tripClassification.priorityBanner.steps.motorVehicle') }}</span>
              <i class="pi pi-arrow-right"></i>
              <span class="priority-step">🚶 {{ t('timeline.preferences.tripClassification.priorityBanner.steps.walk') }}</span>
              <i class="pi pi-arrow-right"></i>
              <span class="priority-step priority-unknown">❓ {{ t('timeline.preferences.tripClassification.priorityBanner.steps.unknown') }}</span>
            </div>
            <p class="priority-description">
              {{ t('timeline.preferences.tripClassification.priorityBanner.description') }}
            </p>
          </div>
        </div>
      </template>
    </Card>

    <div class="settings-panel">
    <!-- Trip Detection Algorithm -->
    <SettingCard
      :title="t('timeline.preferences.tripClassification.algorithm.title')"
      :description="t('timeline.preferences.tripClassification.algorithm.description')"
      :details="algorithmDetails"
      setting-id="tripDetectionAlgorithm"
    >
      <template #control>
        <Select
          :model-value="modelValue.tripDetectionAlgorithm"
          @update:model-value="updatePref('tripDetectionAlgorithm', $event)"
          :options="tripsAlgorithmOptions"
          optionLabel="label"
          optionValue="value"
          :placeholder="t('timeline.preferences.tripClassification.algorithm.placeholder')"
          class="w-full"
        />
      </template>
    </SettingCard>
    </div>

    <!-- Walking Classification -->
    <TransportTypeCard
      type="walk"
      :title="t('timeline.preferences.tripClassification.walk.title')"
      :subtitle="t('timeline.preferences.tripClassification.walk.subtitle')"
      icon="pi pi-user"
      :description="t('timeline.preferences.tripClassification.walk.description')"
      :mandatory="true"
      :validation-messages="getWarningMessagesForType('walk').value"
      setting-id="walkingMaxAvgSpeed"
    >
      <template #parameters>
        <div class="parameter-group" data-setting-id="walkingMaxAvgSpeed">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.walk.maxAvgSpeed.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.walk.maxAvgSpeed.description') }}
          </p>
          <SliderControl
            v-if="modelValue.walkingMaxAvgSpeed !== undefined"
            :model-value="modelValue.walkingMaxAvgSpeed"
            @update:model-value="updatePref('walkingMaxAvgSpeed', $event)"
            :min="3.0" :max="10.0" :step="0.5"
            :labels="threeLabels('walk.maxAvgSpeed')"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="walkingMaxMaxSpeed">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.walk.maxMaxSpeed.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.walk.maxMaxSpeed.description') }}
          </p>
          <SliderControl
            v-if="modelValue.walkingMaxMaxSpeed !== undefined"
            :model-value="modelValue.walkingMaxMaxSpeed"
            @update:model-value="updatePref('walkingMaxMaxSpeed', $event)"
            :min="5.0" :max="15.0" :step="0.5"
            :labels="threeLabels('walk.maxMaxSpeed')"
            suffix=" km/h" :decimal-places="1"
          />
        </div>
      </template>
    </TransportTypeCard>

    <!-- Bicycle Classification -->
    <TransportTypeCard
      type="bicycle"
      :title="t('timeline.preferences.tripClassification.bicycle.title')"
      :subtitle="t('timeline.preferences.tripClassification.bicycle.subtitle')"
      icon="pi pi-circle"
      :description="t('timeline.preferences.tripClassification.bicycle.description')"
      :enabled="modelValue.bicycleEnabled"
      @update:enabled="updatePref('bicycleEnabled', $event)"
      :collapsible="true"
      :validation-messages="getWarningMessagesForType('bicycle').value"
      setting-id="bicycleEnabled"
    >
      <template #parameters>
        <div class="parameter-group" data-setting-id="bicycleMinAvgSpeed">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.bicycle.minAvgSpeed.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.bicycle.minAvgSpeed.description') }}
          </p>
          <SliderControl
            v-if="modelValue.bicycleMinAvgSpeed !== undefined"
            :model-value="modelValue.bicycleMinAvgSpeed"
            @update:model-value="updatePref('bicycleMinAvgSpeed', $event)"
            :min="5.0" :max="15.0" :step="0.5"
            :labels="threeLabels('bicycle.minAvgSpeed')"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="bicycleMaxAvgSpeed">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.bicycle.maxAvgSpeed.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.bicycle.maxAvgSpeed.description') }}
          </p>
          <SliderControl
            v-if="modelValue.bicycleMaxAvgSpeed !== undefined"
            :model-value="modelValue.bicycleMaxAvgSpeed"
            @update:model-value="updatePref('bicycleMaxAvgSpeed', $event)"
            :min="15.0" :max="35.0" :step="1.0"
            :labels="threeLabels('bicycle.maxAvgSpeed')"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="bicycleMaxMaxSpeed">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.bicycle.maxMaxSpeed.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.bicycle.maxMaxSpeed.description') }}
          </p>
          <SliderControl
            v-if="modelValue.bicycleMaxMaxSpeed !== undefined"
            :model-value="modelValue.bicycleMaxMaxSpeed"
            @update:model-value="updatePref('bicycleMaxMaxSpeed', $event)"
            :min="20.0" :max="80.0" :step="5.0"
            :labels="threeLabels('bicycle.maxMaxSpeed')"
            suffix=" km/h" :decimal-places="1"
          />
        </div>
      </template>
    </TransportTypeCard>

    <!-- Running Classification -->
    <TransportTypeCard
      type="running"
      :title="t('timeline.preferences.tripClassification.running.title')"
      :subtitle="t('timeline.preferences.tripClassification.running.subtitle')"
      icon="pi pi-bolt"
      :description="t('timeline.preferences.tripClassification.running.description')"
      :enabled="modelValue.runningEnabled"
      @update:enabled="updatePref('runningEnabled', $event)"
      :collapsible="true"
      :validation-messages="getWarningMessagesForType('running').value"
      setting-id="runningEnabled"
    >
      <template #parameters>
        <div class="parameter-group" data-setting-id="runningMinAvgSpeed">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.running.minAvgSpeed.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.running.minAvgSpeed.description') }}
          </p>
          <SliderControl
            v-if="modelValue.runningMinAvgSpeed !== undefined"
            :model-value="modelValue.runningMinAvgSpeed"
            @update:model-value="updatePref('runningMinAvgSpeed', $event)"
            :min="5.0" :max="10.0" :step="0.5"
            :labels="threeLabels('running.minAvgSpeed')"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="runningMaxAvgSpeed">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.running.maxAvgSpeed.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.running.maxAvgSpeed.description') }}
          </p>
          <SliderControl
            v-if="modelValue.runningMaxAvgSpeed !== undefined"
            :model-value="modelValue.runningMaxAvgSpeed"
            @update:model-value="updatePref('runningMaxAvgSpeed', $event)"
            :min="10.0" :max="18.0" :step="0.5"
            :labels="threeLabels('running.maxAvgSpeed')"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="runningMaxMaxSpeed">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.running.maxMaxSpeed.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.running.maxMaxSpeed.description') }}
          </p>
          <SliderControl
            v-if="modelValue.runningMaxMaxSpeed !== undefined"
            :model-value="modelValue.runningMaxMaxSpeed"
            @update:model-value="updatePref('runningMaxMaxSpeed', $event)"
            :min="12.0" :max="25.0" :step="1.0"
            :labels="threeLabels('running.maxMaxSpeed')"
            suffix=" km/h" :decimal-places="1"
          />
        </div>
      </template>
    </TransportTypeCard>

    <!-- Motor Vehicle Classification -->
    <TransportTypeCard
      type="car"
      :title="t('timeline.preferences.tripClassification.car.title')"
      :subtitle="t('timeline.preferences.tripClassification.car.subtitle')"
      icon="pi pi-car"
      :description="t('timeline.preferences.tripClassification.car.description')"
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
              <label class="parameter-label">{{ t('timeline.preferences.tripClassification.car.carLabel.title') }}</label>
              <p class="parameter-description">{{ t('timeline.preferences.tripClassification.car.carLabel.description') }}</p>
            </div>
            <ToggleSwitch
              :model-value="modelValue.carEnabled !== false"
              @update:model-value="updatePref('carEnabled', $event)"
              :aria-label="t('timeline.preferences.tripClassification.car.carLabel.ariaEnable')"
            />
          </div>

          <div class="label-toggle" data-setting-id="motorcycleEnabled">
            <div>
              <label class="parameter-label">{{ t('timeline.preferences.tripClassification.car.motorcycleLabel.title') }}</label>
              <p class="parameter-description">{{ t('timeline.preferences.tripClassification.car.motorcycleLabel.description') }}</p>
            </div>
            <ToggleSwitch
              :model-value="modelValue.motorcycleEnabled === true"
              @update:model-value="updatePref('motorcycleEnabled', $event)"
              :aria-label="t('timeline.preferences.tripClassification.car.motorcycleLabel.ariaEnable')"
            />
          </div>

          <div class="label-toggle" data-setting-id="publicTransportationEnabled">
            <div>
              <label class="parameter-label">{{ t('timeline.preferences.tripClassification.car.publicTransportLabel.title') }}</label>
              <p class="parameter-description">{{ t('timeline.preferences.tripClassification.car.publicTransportLabel.description') }}</p>
            </div>
            <ToggleSwitch
              :model-value="modelValue.publicTransportationEnabled === true"
              @update:model-value="updatePref('publicTransportationEnabled', $event)"
              :aria-label="t('timeline.preferences.tripClassification.car.publicTransportLabel.ariaEnable')"
            />
          </div>
        </div>

        <div
          v-if="enabledMotorizedLabels.length > 1"
          class="parameter-group"
          data-setting-id="preferredMotorizedType"
        >
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.car.preferredLabel.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.car.preferredLabel.description') }}
          </p>
          <Select
            :model-value="modelValue.preferredMotorizedType || 'CAR'"
            @update:model-value="updatePref('preferredMotorizedType', $event)"
            :options="enabledMotorizedLabels"
            optionLabel="label"
            optionValue="value"
            :placeholder="t('timeline.preferences.tripClassification.car.preferredLabel.placeholder')"
            class="w-full"
          />
        </div>

        <div class="parameter-group" data-setting-id="carMinAvgSpeed">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.car.minAvgSpeed.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.car.minAvgSpeed.description') }}
          </p>
          <SliderControl
            v-if="modelValue.carMinAvgSpeed !== undefined"
            :model-value="modelValue.carMinAvgSpeed"
            @update:model-value="updatePref('carMinAvgSpeed', $event)"
            :min="5.0" :max="25.0" :step="0.5"
            :labels="threeLabels('car.minAvgSpeed')"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="carMinMaxSpeed">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.car.minMaxSpeed.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.car.minMaxSpeed.description') }}
          </p>
          <SliderControl
            v-if="modelValue.carMinMaxSpeed !== undefined"
            :model-value="modelValue.carMinMaxSpeed"
            @update:model-value="updatePref('carMinMaxSpeed', $event)"
            :min="10.0" :max="50.0" :step="5.0"
            :labels="threeLabels('car.minMaxSpeed')"
            suffix=" km/h" :decimal-places="1"
          />
        </div>
      </template>
    </TransportTypeCard>

    <!-- Train Classification -->
    <TransportTypeCard
      type="train"
      :title="t('timeline.preferences.tripClassification.train.title')"
      :subtitle="t('timeline.preferences.tripClassification.train.subtitle')"
      icon="pi pi-building"
      :description="t('timeline.preferences.tripClassification.train.description')"
      :enabled="modelValue.trainEnabled"
      @update:enabled="updatePref('trainEnabled', $event)"
      :collapsible="true"
      :validation-messages="getWarningMessagesForType('train').value"
      setting-id="trainEnabled"
    >
      <template #parameters>
        <div class="parameter-group" data-setting-id="trainMinAvgSpeed">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.train.minAvgSpeed.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.train.minAvgSpeed.description') }}
          </p>
          <SliderControl
            v-if="modelValue.trainMinAvgSpeed !== undefined"
            :model-value="modelValue.trainMinAvgSpeed"
            @update:model-value="updatePref('trainMinAvgSpeed', $event)"
            :min="20.0" :max="50.0" :step="5.0"
            :labels="threeLabels('train.minAvgSpeed')"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="trainMaxAvgSpeed">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.train.maxAvgSpeed.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.train.maxAvgSpeed.description') }}
          </p>
          <SliderControl
            v-if="modelValue.trainMaxAvgSpeed !== undefined"
            :model-value="modelValue.trainMaxAvgSpeed"
            @update:model-value="updatePref('trainMaxAvgSpeed', $event)"
            :min="80.0" :max="400.0" :step="10.0"
            :labels="threeLabels('train.maxAvgSpeed')"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="trainMinMaxSpeed">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.train.minMaxSpeed.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.train.minMaxSpeed.description') }}
          </p>
          <SliderControl
            v-if="modelValue.trainMinMaxSpeed !== undefined"
            :model-value="modelValue.trainMinMaxSpeed"
            @update:model-value="updatePref('trainMinMaxSpeed', $event)"
            :min="60.0" :max="120.0" :step="10.0"
            :labels="threeLabels('train.minMaxSpeed')"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="trainMaxMaxSpeed">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.train.maxMaxSpeed.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.train.maxMaxSpeed.description') }}
          </p>
          <SliderControl
            v-if="modelValue.trainMaxMaxSpeed !== undefined"
            :model-value="modelValue.trainMaxMaxSpeed"
            @update:model-value="updatePref('trainMaxMaxSpeed', $event)"
            :min="100.0" :max="500.0" :step="10.0"
            :labels="threeLabels('train.maxMaxSpeed')"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="trainMaxSpeedVariance">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.train.maxSpeedVariance.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.train.maxSpeedVariance.description') }}
          </p>
          <SliderControl
            v-if="modelValue.trainMaxSpeedVariance !== undefined"
            :model-value="modelValue.trainMaxSpeedVariance"
            @update:model-value="updatePref('trainMaxSpeedVariance', $event)"
            :min="5.0" :max="30.0" :step="1.0"
            :labels="threeLabels('train.maxSpeedVariance')"
            :decimal-places="1"
          />
        </div>
      </template>
    </TransportTypeCard>

    <!-- Flight Classification -->
    <TransportTypeCard
      type="flight"
      :title="t('timeline.preferences.tripClassification.flight.title')"
      :subtitle="t('timeline.preferences.tripClassification.flight.subtitle')"
      icon="pi pi-send"
      :description="t('timeline.preferences.tripClassification.flight.description')"
      :enabled="modelValue.flightEnabled"
      @update:enabled="updatePref('flightEnabled', $event)"
      :collapsible="true"
      :validation-messages="getWarningMessagesForType('flight').value"
      setting-id="flightEnabled"
    >
      <template #parameters>
        <div class="parameter-group" data-setting-id="flightMinAvgSpeed">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.flight.minAvgSpeed.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.flight.minAvgSpeed.description') }}
          </p>
          <SliderControl
            v-if="modelValue.flightMinAvgSpeed !== undefined"
            :model-value="modelValue.flightMinAvgSpeed"
            @update:model-value="updatePref('flightMinAvgSpeed', $event)"
            :min="250.0" :max="600.0" :step="50.0"
            :labels="threeLabels('flight.minAvgSpeed')"
            suffix=" km/h" :decimal-places="1"
          />
        </div>

        <div class="parameter-group" data-setting-id="flightMinMaxSpeed">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.flight.minMaxSpeed.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.flight.minMaxSpeed.description') }}
          </p>
          <SliderControl
            v-if="modelValue.flightMinMaxSpeed !== undefined"
            :model-value="modelValue.flightMinMaxSpeed"
            @update:model-value="updatePref('flightMinMaxSpeed', $event)"
            :min="400.0" :max="900.0" :step="50.0"
            :labels="threeLabels('flight.minMaxSpeed')"
            suffix=" km/h" :decimal-places="1"
          />
        </div>
      </template>
    </TransportTypeCard>

    <!-- Boat Classification -->
    <TransportTypeCard
      type="boat"
      :title="t('timeline.preferences.tripClassification.boat.title')"
      :subtitle="t('timeline.preferences.tripClassification.boat.subtitle')"
      icon="pi pi-compass"
      :description="t('timeline.preferences.tripClassification.boat.description')"
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
                v-if="boatSetupStatus.error && boatSetupStatus.docsUrl"
                :href="boatSetupStatus.docsUrl"
                target="_blank"
                rel="noopener noreferrer"
                class="boat-setup-docs-link"
              >
                {{ t('timeline.preferences.tripClassification.boat.setup.docsLink') }} <i class="pi pi-external-link"></i>
              </a>
            </div>
            <div v-if="boatSetupStatus.progressPercentage !== undefined" class="boat-setup-progress">
              {{ boatSetupStatus.progressPercentage }}%
            </div>
            <Button
              v-if="boatSetupCanStart"
              :label="t('timeline.preferences.tripClassification.boat.setup.retry')"
              icon="pi pi-refresh"
              size="small"
              @click="$emit('retry-boat-setup')"
            />
          </div>
        </Message>

        <div class="parameter-group" data-setting-id="boatMinWaterRatio">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.boat.minWaterRatio.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.boat.minWaterRatio.description') }}
          </p>
          <SliderControl
            v-if="modelValue.boatMinWaterRatio !== undefined"
            :model-value="modelValue.boatMinWaterRatio"
            @update:model-value="updatePref('boatMinWaterRatio', $event)"
            :min="0.1" :max="1.0" :step="0.05"
            :labels="threeLabels('boat.minWaterRatio')"
            :decimal-places="2"
          />
        </div>

        <div class="parameter-group" data-setting-id="boatMinWaterDistanceMeters">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.boat.minWaterDistance.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.boat.minWaterDistance.description') }}
          </p>
          <SliderControl
            v-if="modelValue.boatMinWaterDistanceMeters !== undefined"
            :model-value="modelValue.boatMinWaterDistanceMeters"
            @update:model-value="updatePref('boatMinWaterDistanceMeters', $event)"
            :min="100" :max="10000" :step="100"
            :labels="threeLabels('boat.minWaterDistance')"
            suffix=" m" :decimal-places="0"
          />
        </div>

        <div class="parameter-group" data-setting-id="boatMinContinuousWaterDistanceMeters">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.boat.minContinuousWaterDistance.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.boat.minContinuousWaterDistance.description') }}
          </p>
          <SliderControl
            v-if="modelValue.boatMinContinuousWaterDistanceMeters !== undefined"
            :model-value="modelValue.boatMinContinuousWaterDistanceMeters"
            @update:model-value="updatePref('boatMinContinuousWaterDistanceMeters', $event)"
            :min="100" :max="10000" :step="100"
            :labels="threeLabels('boat.minContinuousWaterDistance')"
            suffix=" m" :decimal-places="0"
          />
        </div>

        <div class="parameter-group" data-setting-id="boatMaxPlausibleSpeed">
          <label class="parameter-label">{{ t('timeline.preferences.tripClassification.boat.maxPlausibleSpeed.label') }}</label>
          <p class="parameter-description">
            {{ t('timeline.preferences.tripClassification.boat.maxPlausibleSpeed.description') }}
          </p>
          <SliderControl
            v-if="modelValue.boatMaxPlausibleSpeed !== undefined"
            :model-value="modelValue.boatMaxPlausibleSpeed"
            @update:model-value="updatePref('boatMaxPlausibleSpeed', $event)"
            :min="20" :max="300" :step="10"
            :labels="threeLabels('boat.maxPlausibleSpeed')"
            suffix=" km/h" :decimal-places="0"
          />
        </div>
      </template>
    </TransportTypeCard>

    <!-- Short Distance Threshold -->
    <div class="settings-panel">
    <SettingCard
      :title="t('timeline.preferences.tripClassification.shortDistance.title')"
      :description="t('timeline.preferences.tripClassification.shortDistance.description')"
      :details="t('timeline.preferences.tripClassification.shortDistance.details')"
      setting-id="shortDistanceKm"
    >
      <template #control>
        <SliderControl
          v-if="modelValue.shortDistanceKm !== undefined"
          :model-value="modelValue.shortDistanceKm"
          @update:model-value="updatePref('shortDistanceKm', $event)"
          :min="0.1" :max="3.0" :step="0.1"
          :labels="threeLabels('shortDistance')"
          suffix=" km" :decimal-places="1"
        />
      </template>
    </SettingCard>

    <!-- Trip Arrival Detection Duration -->
    <SettingCard
      :title="t('timeline.preferences.tripClassification.arrivalDetection.title')"
      :description="t('timeline.preferences.tripClassification.arrivalDetection.description')"
      :details="lowerHigherDetails('arrivalDetection')"
      setting-id="tripArrivalDetectionMinDurationSeconds"
    >
      <template #control>
        <SliderControl
          v-if="modelValue.tripArrivalDetectionMinDurationSeconds !== undefined"
          :model-value="modelValue.tripArrivalDetectionMinDurationSeconds"
          @update:model-value="updatePref('tripArrivalDetectionMinDurationSeconds', $event)"
          :min="10" :max="300" :step="10"
          :labels="threeLabels('arrivalDetection')"
          suffix=" s" :decimal-places="0"
        />
      </template>
    </SettingCard>

    <!-- Trip Sustained Stop Duration -->
    <SettingCard
      :title="t('timeline.preferences.tripClassification.sustainedStop.title')"
      :description="t('timeline.preferences.tripClassification.sustainedStop.description')"
      :details="lowerHigherDetails('sustainedStop')"
      setting-id="tripSustainedStopMinDurationSeconds"
    >
      <template #control>
        <SliderControl
          v-if="modelValue.tripSustainedStopMinDurationSeconds !== undefined"
          :model-value="modelValue.tripSustainedStopMinDurationSeconds"
          @update:model-value="updatePref('tripSustainedStopMinDurationSeconds', $event)"
          :min="10" :max="600" :step="10"
          :labels="threeLabels('sustainedStop')"
          suffix=" s" :decimal-places="0"
        />
      </template>
    </SettingCard>

    <!-- Trip Arrival Minimum Points -->
    <SettingCard
      :title="t('timeline.preferences.tripClassification.arrivalMinPoints.title')"
      :description="t('timeline.preferences.tripClassification.arrivalMinPoints.description')"
      :details="lowerHigherDetails('arrivalMinPoints')"
      setting-id="tripArrivalMinPoints"
    >
      <template #control>
        <SliderControl
          v-if="modelValue.tripArrivalMinPoints !== undefined"
          :model-value="modelValue.tripArrivalMinPoints"
          @update:model-value="updatePref('tripArrivalMinPoints', $event)"
          :min="2" :max="5" :step="1"
          :labels="threeLabels('arrivalMinPoints')"
          suffix=" points" :decimal-places="0"
        />
      </template>
    </SettingCard>
    </div>
  </PreferencesTabLayout>
</template>

<script setup>
import './shared-styles.css'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import PreferencesTabLayout from './PreferencesTabLayout.vue'
import SettingCard from '@/components/ui/forms/SettingCard.vue'
import SliderControl from '@/components/ui/forms/SliderControl.vue'
import TransportTypeCard from '@/components/ui/forms/TransportTypeCard.vue'
import Card from 'primevue/card'
import Select from 'primevue/select'
import Message from 'primevue/message'
import Button from 'primevue/button'
import ToggleSwitch from 'primevue/toggleswitch'
import { formatBoatSetupPhase } from '@/utils/boatSetupDisplay'

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

const { t } = useI18n()

const NS = 'timeline.preferences.tripClassification'

const threeLabels = (field) => [
  t(`${NS}.${field}.labelLow`),
  t(`${NS}.${field}.labelMid`),
  t(`${NS}.${field}.labelHigh`)
]

const lowerHigherDetails = (field) => ({
  [t(`${NS}.${field}.detailsLowerLabel`)]: t(`${NS}.${field}.detailsLowerValue`),
  [t(`${NS}.${field}.detailsHigherLabel`)]: t(`${NS}.${field}.detailsHigherValue`)
})

const algorithmDetails = computed(() => ({
  [t(`${NS}.algorithm.detailsSingleLabel`)]: t(`${NS}.algorithm.detailsSingleValue`),
  [t(`${NS}.algorithm.detailsMultipleLabel`)]: t(`${NS}.algorithm.detailsMultipleValue`)
}))

const tripsAlgorithmOptions = computed(() => [
  { label: t(`${NS}.algorithm.optionSingle`), value: 'single' },
  { label: t(`${NS}.algorithm.optionMultiple`), value: 'multiple' }
])

const motorizedTypeOptions = computed(() => [
  { label: t(`${NS}.car.preferredLabel.optionCar`), value: 'CAR' },
  { label: t(`${NS}.car.preferredLabel.optionMotorcycle`), value: 'MOTORCYCLE' },
  { label: t(`${NS}.car.preferredLabel.optionPublicTransport`), value: 'PUBLIC_TRANSPORT' }
])

const isMotorVehicleEnabled = computed(() => {
  return props.modelValue.carEnabled !== false || props.modelValue.motorcycleEnabled === true || props.modelValue.publicTransportationEnabled === true
})

const enabledMotorizedLabels = computed(() => motorizedTypeOptions.value.filter(({ value }) => (
  (value === 'CAR' && props.modelValue.carEnabled !== false) ||
  (value === 'MOTORCYCLE' && props.modelValue.motorcycleEnabled === true) ||
  (value === 'PUBLIC_TRANSPORT' && props.modelValue.publicTransportationEnabled === true)
)))

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
    motorcycleEnabled: false,
    publicTransportationEnabled: false
  })
}

const boatSetupSeverity = computed(() => {
  if (!props.boatSetupStatus) return 'warn'
  if (props.boatSetupStatus.status === 'READY') return 'success'
  if (props.boatSetupStatus.status === 'FAILED') return 'error'
  if (['QUEUED', 'RUNNING'].includes(props.boatSetupStatus.status)) return 'info'
  return 'warn'
})

const boatSetupTitle = computed(() => {
  if (!props.boatSetupStatus) return t(`${NS}.boat.setup.requiredTitle`)
  if (props.boatSetupStatus.status === 'READY') return t(`${NS}.boat.setup.readyTitle`)
  if (props.boatSetupStatus.status === 'FAILED') return t(`${NS}.boat.setup.failedTitle`)
  if (['QUEUED', 'RUNNING'].includes(props.boatSetupStatus.status)) return t(`${NS}.boat.setup.inProgressTitle`)
  return t(`${NS}.boat.setup.requiredTitle`)
})

const boatSetupPhaseText = computed(() => {
  if (!props.boatSetupStatus) return null
  if (props.boatSetupStatus.status === 'READY') return t(`${NS}.boat.setup.readyPhaseText`)
  return formatBoatSetupPhase(props.boatSetupStatus.phase)
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
  border: 1px solid var(--gp-border);
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
