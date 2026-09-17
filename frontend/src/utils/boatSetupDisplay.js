import { formatMessageDescriptor } from '@/utils/messageDescriptor'

const phaseLabels = {
  BOAT_SETUP_IS_READY: 'Boat setup is ready',
  GPS_WATER_EVIDENCE_NEEDS_ENRICHMENT: 'GPS water evidence needs enrichment',
  WATER_DATASET_IS_NOT_IMPORTED: 'Water dataset is not imported',
  STARTING_BOAT_SETUP: 'Starting Boat setup',
  BOAT_SETUP_COMPLETED: 'Boat setup completed',
  RECLASSIFYING_EXISTING_TRIPS: 'Reclassifying existing trips',
  WATER_DATASET_READY: 'Water dataset ready',
  PREPARING_WATER_DATASET_ARTIFACT: 'Preparing water dataset artifact',
  IMPORTING_WATER_POLYGONS: 'Importing water polygons',
  USING_LOCAL_WATER_DATASET_FILE: 'Using local water dataset file',
  DOWNLOADING_WATER_DATASET: 'Downloading water dataset',
  GPS_WATER_EVIDENCE_READY: 'GPS water evidence ready',
  CLASSIFYING_GPS_WATER_EVIDENCE: 'Classifying GPS water evidence',
  WAITING_FOR_WATER_DATASET_IMPORT: 'Waiting for water dataset import',
  WAITING_FOR_GPS_WATER_EVIDENCE: 'Waiting for GPS water evidence',
  BOAT_SETUP_WORKER_DID_NOT_START: 'Boat setup worker did not start',
  WATER_DATASET_SETUP_FAILED: 'Water dataset setup failed',
  BOAT_SETUP_FAILED: 'Boat setup failed'
}

export const formatBoatSetupPhase = (phase, fallback = 'Preparing Boat setup...') => (
  phaseLabels[phase] || fallback
)

export const formatBoatSetupError = (error, fallback = 'Water dataset setup failed. Check offline setup instructions.') => (
  formatMessageDescriptor(error) || fallback
)
