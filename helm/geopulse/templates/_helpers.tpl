{{/*
Expand the name of the chart.
*/}}
{{- define "geopulse.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "geopulse.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "geopulse.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "geopulse.labels" -}}
helm.sh/chart: {{ include "geopulse.chart" . }}
{{ include "geopulse.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "geopulse.selectorLabels" -}}
app.kubernetes.io/name: {{ include "geopulse.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Backend labels
*/}}
{{- define "geopulse.backend.labels" -}}
{{ include "geopulse.labels" . }}
app.kubernetes.io/component: backend
{{- end }}

{{/*
Backend selector labels
*/}}
{{- define "geopulse.backend.selectorLabels" -}}
{{ include "geopulse.selectorLabels" . }}
app.kubernetes.io/component: backend
{{- end }}

{{/*
Frontend labels
*/}}
{{- define "geopulse.frontend.labels" -}}
{{ include "geopulse.labels" . }}
app.kubernetes.io/component: frontend
{{- end }}

{{/*
Frontend selector labels
*/}}
{{- define "geopulse.frontend.selectorLabels" -}}
{{ include "geopulse.selectorLabels" . }}
app.kubernetes.io/component: frontend
{{- end }}

{{/*
PostgreSQL labels
*/}}
{{- define "geopulse.postgres.labels" -}}
{{ include "geopulse.labels" . }}
app.kubernetes.io/component: database
{{- end }}

{{/*
PostgreSQL selector labels
*/}}
{{- define "geopulse.postgres.selectorLabels" -}}
{{ include "geopulse.selectorLabels" . }}
app.kubernetes.io/component: database
{{- end }}

{{/*
Mosquitto labels
*/}}
{{- define "geopulse.mosquitto.labels" -}}
{{ include "geopulse.labels" . }}
app.kubernetes.io/component: mqtt
{{- end }}

{{/*
Mosquitto selector labels
*/}}
{{- define "geopulse.mosquitto.selectorLabels" -}}
{{ include "geopulse.selectorLabels" . }}
app.kubernetes.io/component: mqtt
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "geopulse.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "geopulse.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
PostgreSQL host
*/}}
{{- define "geopulse.postgres.host" -}}
{{- if .Values.postgres.enabled }}
{{- printf "%s-postgres" (include "geopulse.fullname" .) }}
{{- else }}
{{- .Values.externalPostgres.host }}
{{- end }}
{{- end }}

{{/*
PostgreSQL port
*/}}
{{- define "geopulse.postgres.port" -}}
{{- if .Values.postgres.enabled }}
{{- .Values.postgres.service.port }}
{{- else }}
{{- .Values.externalPostgres.port }}
{{- end }}
{{- end }}

{{/*
PostgreSQL database
*/}}
{{- define "geopulse.postgres.database" -}}
{{- if .Values.postgres.enabled }}
{{- .Values.postgres.database }}
{{- else }}
{{- .Values.externalPostgres.database }}
{{- end }}
{{- end }}

{{/*
PostgreSQL username
*/}}
{{- define "geopulse.postgres.username" -}}
{{- if .Values.postgres.enabled }}
{{- .Values.postgres.username }}
{{- else }}
{{- .Values.externalPostgres.username }}
{{- end }}
{{- end }}

{{/*
PostgreSQL JDBC URL
*/}}
{{- define "geopulse.postgres.jdbcUrl" -}}
{{- printf "jdbc:postgresql://%s:%v/%s" (include "geopulse.postgres.host" .) (include "geopulse.postgres.port" .) (include "geopulse.postgres.database" .) }}
{{- end }}

{{/*
Backend service URL
*/}}
{{- define "geopulse.backend.url" -}}
{{- printf "http://%s-backend:%v" (include "geopulse.fullname" .) (.Values.backend.service.port | int) }}
{{- end }}

{{/*
Mosquitto service host
*/}}
{{- define "geopulse.mosquitto.host" -}}
{{- printf "%s-mosquitto" (include "geopulse.fullname" .) }}
{{- end }}

{{/*
Secret name
*/}}
{{- define "geopulse.secretName" -}}
{{- if .Values.secrets.useExistingSecret }}
{{- .Values.secrets.existingSecretName }}
{{- else }}
{{- printf "%s-secrets" (include "geopulse.fullname" .) }}
{{- end }}
{{- end }}

{{/*
Generate random password
*/}}
{{- define "geopulse.generatePassword" -}}
{{- randAlphaNum 32 }}
{{- end }}

{{/*
Image pull policy
*/}}
{{- define "geopulse.imagePullPolicy" -}}
{{- .Values.global.imagePullPolicy | default "IfNotPresent" }}
{{- end }}

{{/*
Return the proper image name for backend
*/}}
{{- define "geopulse.backend.image" -}}
{{- printf "%s:%s" .Values.backend.image.repository (.Values.backend.image.tag | default .Chart.AppVersion) }}
{{- end }}

{{/*
Return the proper image name for frontend
*/}}
{{- define "geopulse.frontend.image" -}}
{{- printf "%s:%s" .Values.frontend.image.repository (.Values.frontend.image.tag | default .Chart.AppVersion) }}
{{- end }}

{{/*
Return the proper image name for postgres
*/}}
{{- define "geopulse.postgres.image" -}}
{{- printf "%s:%s" .Values.postgres.image.repository .Values.postgres.image.tag }}
{{- end }}

{{/*
Return the proper image name for mosquitto
*/}}
{{- define "geopulse.mosquitto.image" -}}
{{- printf "%s:%s" .Values.mosquitto.image.repository .Values.mosquitto.image.tag }}
{{- end }}

{{/*
Return the proper image name for keygen
*/}}
{{- define "geopulse.keygen.image" -}}
{{- printf "%s:%s" .Values.keygen.image.repository .Values.keygen.image.tag }}
{{- end }}