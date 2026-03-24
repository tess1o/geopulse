# Makefile for GeoPulse Docker image building and publishing

# Variables
VERSION := $(shell grep -m1 "<version>" pom.xml | sed -E 's/.*<version>(.*)<\/version>.*/\1/')
VERSION_NATIVE := $(VERSION)-native
VERSION_JVM := $(VERSION)-jvm
BACKEND_IMAGE := tess1o/geopulse-backend
FRONTEND_IMAGE := tess1o/geopulse-ui
GHCR_NAMESPACE := ghcr.io/tess1o
GHCR_BACKEND_IMAGE := $(GHCR_NAMESPACE)/geopulse-backend
GHCR_FRONTEND_IMAGE := $(GHCR_NAMESPACE)/geopulse-ui
GHCR_NAMESPACE := ghcr.io/tess1o
GHCR_BACKEND_IMAGE := $(GHCR_NAMESPACE)/geopulse-backend
PLATFORMS := linux/amd64,linux/arm64

# Build both backend and frontend images for multiple architectures
.PHONY: all
all: build-backend-jvm build-backend-native build-frontend openapi publish-helm
.PHONY: build-all
build-all: build-backend-jvm build-backend-native build-frontend

# ==========================
# Create or reuse builder
# ==========================
.PHONY: ensure-builder
ensure-builder:
	@docker buildx inspect geopulse-builder >/dev/null 2>&1 || \
	(docker buildx create --name geopulse-builder --use --bootstrap && echo "✅ Buildx builder created")
	@docker buildx use geopulse-builder

.PHONY: build-backend-native-arm64
build-backend-native-arm64: ensure-builder
	@echo "🏗️  Building native backend Docker image for ARM64..."
	docker buildx build --platform linux/arm64 \
		-t $(BACKEND_IMAGE):$(VERSION_NATIVE)-arm64 \
		-t $(BACKEND_IMAGE):native-latest-arm64 \
		-t $(GHCR_BACKEND_IMAGE):$(VERSION_NATIVE)-arm64 \
		-t $(GHCR_BACKEND_IMAGE):native-latest-arm64 \
		--build-arg VERSION=$(VERSION_NATIVE) \
		--build-arg QUARKUS_NATIVE_BUILD_ARGS="" \
		-f backend/Dockerfile.native \
		--push \
		.
	@echo "✅ ARM64 native backend image pushed successfully."

.PHONY: build-backend-native-amd64
build-backend-native-amd64: ensure-builder
	@echo "🏗️  Building native backend Docker image for AMD64..."
	docker buildx build --platform linux/amd64 \
		-t $(BACKEND_IMAGE):$(VERSION_NATIVE)-amd64 \
		-t $(BACKEND_IMAGE):native-latest-amd64 \
		-t $(GHCR_BACKEND_IMAGE):$(VERSION_NATIVE)-amd64 \
		-t $(GHCR_BACKEND_IMAGE):native-latest-amd64 \
		--build-arg VERSION=$(VERSION_NATIVE) \
		--build-arg QUARKUS_NATIVE_BUILD_ARGS="" \
		-f backend/Dockerfile.native \
		--push \
		.
	@echo "✅ AMD64 native backend image pushed successfully."

.PHONY: build-backend-native
build-backend-native: build-backend-native-arm64 build-backend-native-amd64
	@echo "🧩 Creating multi-arch manifest for Docker Hub..."
	docker buildx imagetools create \
		-t $(BACKEND_IMAGE):$(VERSION_NATIVE) \
		$(BACKEND_IMAGE):$(VERSION_NATIVE)-amd64 \
		$(BACKEND_IMAGE):$(VERSION_NATIVE)-arm64
	docker buildx imagetools create \
		-t $(BACKEND_IMAGE):native-latest \
		-t $(BACKEND_IMAGE):latest \
		$(BACKEND_IMAGE):native-latest-amd64 \
		$(BACKEND_IMAGE):native-latest-arm64

	@echo "🧩 Creating multi-arch manifest for GHCR..."
	docker buildx imagetools create \
		-t $(GHCR_BACKEND_IMAGE):$(VERSION_NATIVE) \
		$(GHCR_BACKEND_IMAGE):$(VERSION_NATIVE)-amd64 \
		$(GHCR_BACKEND_IMAGE):$(VERSION_NATIVE)-arm64
	docker buildx imagetools create \
		-t $(GHCR_BACKEND_IMAGE):native-latest \
		-t $(GHCR_BACKEND_IMAGE):latest \
		$(GHCR_BACKEND_IMAGE):native-latest-amd64 \
		$(GHCR_BACKEND_IMAGE):native-latest-arm64
	@echo "✅ Multi-arch native images built and pushed successfully (Docker Hub + GHCR)."

# Build Compatible Native Images (x86-64-v2 for old CPUs, armv8-a+nolse for Raspberry Pi)
.PHONY: build-backend-native-amd64-compat
build-backend-native-amd64-compat: ensure-builder
	@echo "🏗️  Building compatible native backend Docker image for AMD64 (x86-64-v2)..."
	docker buildx build --platform linux/amd64 \
		-t $(BACKEND_IMAGE):$(VERSION_NATIVE)-amd64-compat \
		-t $(BACKEND_IMAGE):native-compat-amd64 \
		-t $(GHCR_BACKEND_IMAGE):$(VERSION_NATIVE)-amd64-compat \
		-t $(GHCR_BACKEND_IMAGE):native-compat-amd64 \
		--build-arg VERSION=$(VERSION_NATIVE) \
		--build-arg QUARKUS_NATIVE_BUILD_ARGS=",-march=x86-64-v2" \
		-f backend/Dockerfile.native \
		--push \
		.
	@echo "✅ AMD64 compatible native backend image pushed successfully."

.PHONY: build-backend-native-arm64-compat
build-backend-native-arm64-compat: ensure-builder
	@echo "🏗️  Building compatible native backend Docker image for ARM64 (Raspberry Pi)..."
	docker buildx build --platform linux/arm64 \
		-t $(BACKEND_IMAGE):$(VERSION_NATIVE)-arm64-compat \
		-t $(BACKEND_IMAGE):native-compat-arm64 \
		-t $(GHCR_BACKEND_IMAGE):$(VERSION_NATIVE)-arm64-compat \
		-t $(GHCR_BACKEND_IMAGE):native-compat-arm64 \
		--build-arg VERSION=$(VERSION_NATIVE) \
		--build-arg QUARKUS_NATIVE_BUILD_ARGS=",-march=armv8-a+nolse" \
		-f backend/Dockerfile.native \
		--push \
		.
	@echo "✅ ARM64 compatible native backend image pushed successfully."

.PHONY: build-backend-native-compat
build-backend-native-compat: build-backend-native-amd64-compat build-backend-native-arm64-compat
	@echo "🧩 Creating multi-arch compatible manifest for Docker Hub..."
	docker buildx imagetools create \
		-t $(BACKEND_IMAGE):$(VERSION_NATIVE)-compat \
		$(BACKEND_IMAGE):$(VERSION_NATIVE)-amd64-compat \
		$(BACKEND_IMAGE):$(VERSION_NATIVE)-arm64-compat
	docker buildx imagetools create \
		-t $(BACKEND_IMAGE):native-compat-latest \
		-t $(BACKEND_IMAGE):compat-latest \
		$(BACKEND_IMAGE):native-compat-amd64 \
		$(BACKEND_IMAGE):native-compat-arm64

	@echo "🧩 Creating multi-arch compatible manifest for GHCR..."
	docker buildx imagetools create \
		-t $(GHCR_BACKEND_IMAGE):$(VERSION_NATIVE)-compat \
		$(GHCR_BACKEND_IMAGE):$(VERSION_NATIVE)-amd64-compat \
		$(GHCR_BACKEND_IMAGE):$(VERSION_NATIVE)-arm64-compat
	docker buildx imagetools create \
		-t $(GHCR_BACKEND_IMAGE):native-compat-latest \
		-t $(GHCR_BACKEND_IMAGE):compat-latest \
		$(GHCR_BACKEND_IMAGE):native-compat-amd64 \
		$(GHCR_BACKEND_IMAGE):native-compat-arm64
	@echo "✅ Multi-arch compatible native images built and pushed successfully (Docker Hub + GHCR)."

# Build multi-architecture backend image
.PHONY: build-backend-jvm
build-backend-jvm: ensure-builder
	@echo "Building backend JVM Docker image for multiple architectures..."
	docker buildx build --platform $(PLATFORMS) \
		-t $(BACKEND_IMAGE):$(VERSION_JVM) \
		-t $(BACKEND_IMAGE):jvm-latest \
		-t $(GHCR_BACKEND_IMAGE):$(VERSION_JVM) \
		-t $(GHCR_BACKEND_IMAGE):jvm-latest \
		--build-arg VERSION=$(VERSION_JVM) \
		-f backend/Dockerfile \
		--push \
		.
	@echo "✅ Backend JVM image built and pushed to Docker Hub and GHCR successfully."

# Build multi-architecture frontend image
.PHONY: build-frontend
build-frontend: ensure-builder
	@echo "Building frontend Docker image for multiple architectures..."
	docker buildx build --platform $(PLATFORMS) \
		-t $(FRONTEND_IMAGE):$(VERSION) \
		-t $(FRONTEND_IMAGE):latest \
		-t $(GHCR_FRONTEND_IMAGE):$(VERSION) \
		-t $(GHCR_FRONTEND_IMAGE):latest \
		--build-arg VERSION=$(VERSION) \
		-f frontend/Dockerfile \
		--push \
		.
	@echo "Frontend image built successfully"

.PHONY: openapi
openapi:
	@echo "Removing old OpenAPI specification"
	rm -rf backend/target/openapi docs/openapi
	@echo "📘 Generating OpenAPI specification..."
	./mvnw -pl backend -am package -DskipTests=true
	@echo "📦 Copying OpenAPI files to docs/openapi..."
	mkdir -p docs/openapi
	cp -v backend/target/openapi/* docs/openapi/
	@echo "✅ OpenAPI spec copied to docs/openapi/"

.PHONY: publish-helm
publish-helm:
	@echo "📦 Packaging Helm chart..."
	helm package helm/geopulse -d charts

	@echo "🧩 Updating Helm repo index..."
	helm repo index charts --url https://tess1o.github.io/geopulse/charts --merge charts/index.yaml

	@echo "📂 Copying charts to docs-website static directory..."
	mkdir -p docs-website/static/charts
	cp -r charts/* docs-website/static/charts/

	@echo "🚀 Deploying documentation with Helm charts..."
	cd docs-website && GIT_USER=tess1o npm run deploy

	@echo "✅ Helm charts and documentation published successfully!"

# Backend unit tests
.PHONY: backend-test-unit
backend-test-unit:
	@echo "Running backend unit tests"
	./mvnw -pl backend clean test

# Backend integration tests
.PHONY: backend-test-integration
backend-test-integration:
	@echo "Running backend integration tests"
	./mvnw -pl backend -DskipITs=false test-compile failsafe:integration-test failsafe:verify

# Full backend test suite (unit + integration)
.PHONY: backend-test-all
backend-test-all:
	@echo "Running full backend test suite"
	./mvnw -pl backend clean verify -DskipITs=false

# Timezone matrix settings for local verification
# Override like:
#   make backend-test-unit-tz-matrix TZ_MATRIX="UTC Europe/Kyiv America/New_York"
#   make backend-test-integration-tz-matrix IT_TEST=FriendshipRepositoryIntegrationTest
TZ_MATRIX ?= UTC Europe/Kyiv
UNIT_TEST ?=
IT_TEST ?=

# Backend unit tests in timezone matrix
.PHONY: backend-test-unit-tz-matrix
backend-test-unit-tz-matrix:
	@set -e; \
	for tz in $(TZ_MATRIX); do \
		echo "Running backend unit tests with timezone $$tz"; \
		if [ -n "$(UNIT_TEST)" ]; then \
			TZ=$$tz ./mvnw -pl backend -Duser.timezone=$$tz -Dtest="$(UNIT_TEST)" test; \
		else \
			TZ=$$tz ./mvnw -pl backend -Duser.timezone=$$tz clean test; \
		fi; \
	done

# Backend integration tests in timezone matrix
.PHONY: backend-test-integration-tz-matrix
backend-test-integration-tz-matrix:
	@set -e; \
	for tz in $(TZ_MATRIX); do \
		echo "Running backend integration tests with timezone $$tz"; \
		if [ -n "$(IT_TEST)" ]; then \
			TZ=$$tz ./mvnw -pl backend -DskipITs=false -Duser.timezone=$$tz -Dit.test="$(IT_TEST)" test-compile failsafe:integration-test failsafe:verify; \
		else \
			TZ=$$tz ./mvnw -pl backend -DskipITs=false -Duser.timezone=$$tz test-compile failsafe:integration-test failsafe:verify; \
		fi; \
	done

#==============================================================================
# E2E TESTING TARGETS
#==============================================================================

# Variables for E2E testing
E2E_COMPOSE_FILE := tests/docker-compose.e2e.yml

# Start E2E test environment (UI + Backend + DB)
.PHONY: e2e-start
e2e-start:
	@echo "🚀 Starting E2E test environment..."
	@echo "  Backend: http://localhost:8081"
	@echo "  Frontend: http://localhost:5556"
	@echo "  Database: localhost:5433"
	docker-compose -f $(E2E_COMPOSE_FILE) up -d geopulse-postgres-e2e geopulse-backend-e2e geopulse-ui-e2e
	@echo "✅ E2E environment started"

# Stop E2E test environment
.PHONY: e2e-stop
e2e-stop:
	@echo "🛑 Stopping E2E test environment..."
	docker-compose -f $(E2E_COMPOSE_FILE) down
	@echo "✅ E2E environment stopped"

# Restart E2E test environment
.PHONY: e2e-restart
e2e-restart:
	@echo "🔄 Restarting E2E test environment..."
	docker-compose -f $(E2E_COMPOSE_FILE) down
	docker-compose -f $(E2E_COMPOSE_FILE) up -d geopulse-postgres-e2e geopulse-backend-e2e geopulse-ui-e2e
	@echo "✅ E2E environment restarted"

# Show E2E environment status
.PHONY: e2e-status
e2e-status:
	@echo "📊 E2E Environment Status:"
	@docker-compose -f $(E2E_COMPOSE_FILE) ps

# Show E2E environment logs
.PHONY: e2e-logs
e2e-logs:
	@echo "📋 E2E Environment Logs:"
	docker-compose -f $(E2E_COMPOSE_FILE) logs -f

# Run E2E tests (requires environment to be running)
.PHONY: test-e2e
test-e2e:
	@echo "🧪 Running E2E tests..."
	cd tests && npm run test:e2e

# Run E2E tests with UI (interactive)
.PHONY: test-e2e-ui
test-e2e-ui:
	@echo "🧪 Running E2E tests in UI mode..."
	cd tests && npm run test:e2e:ui

# Run E2E tests in headed mode (visible browser)
.PHONY: test-e2e-headed
test-e2e-headed:
	@echo "🧪 Running E2E tests in headed mode..."
	cd tests && npm run test:e2e:headed

# Debug E2E tests
.PHONY: test-e2e-debug
test-e2e-debug:
	@echo "🐛 Running E2E tests in debug mode..."
	cd tests && npm run test:e2e:debug

# Show E2E test report
.PHONY: test-e2e-report
test-e2e-report:
	@echo "📊 Opening E2E test report..."
	cd tests && npm run test:e2e:report

# Full E2E workflow: start environment → run tests → show report
.PHONY: test-e2e-full
test-e2e-full:
	@echo "🚀 Running full E2E test workflow..."
	$(MAKE) e2e-start
	@echo "⏳ Waiting for services to be ready..."
	sleep 10
	$(MAKE) test-e2e
	$(MAKE) test-e2e-report
	@echo "✅ Full E2E test workflow completed"

# Rebuild E2E backend and restart
.PHONY: e2e-rebuild-backend
e2e-rebuild-backend:
	@echo "🔧 Rebuilding E2E backend..."
	docker-compose -f $(E2E_COMPOSE_FILE) build geopulse-backend-e2e
	docker-compose -f $(E2E_COMPOSE_FILE) stop geopulse-backend-e2e
	docker-compose -f $(E2E_COMPOSE_FILE) rm -f geopulse-backend-e2e
	docker-compose -f $(E2E_COMPOSE_FILE) up -d geopulse-backend-e2e
	@echo "✅ E2E backend rebuilt and restarted"

# Rebuild E2E frontend and restart
.PHONY: e2e-rebuild-frontend
e2e-rebuild-frontend:
	@echo "🔧 Rebuilding E2E frontend..."
	docker-compose -f $(E2E_COMPOSE_FILE) build geopulse-ui-e2e
	docker-compose -f $(E2E_COMPOSE_FILE) stop geopulse-ui-e2e
	docker-compose -f $(E2E_COMPOSE_FILE) rm -f geopulse-ui-e2e
	docker-compose -f $(E2E_COMPOSE_FILE) up -d geopulse-ui-e2e
	@echo "✅ E2E frontend rebuilt and restarted"

# Rebuild both E2E containers
.PHONY: e2e-rebuild-all
e2e-rebuild-all:
	@echo "🔧 Rebuilding all E2E containers..."
	$(MAKE) e2e-rebuild-backend
	$(MAKE) e2e-rebuild-frontend
	@echo "✅ All E2E containers rebuilt"

# Clean E2E environment (remove containers and volumes)
.PHONY: e2e-clean
e2e-clean:
	@echo "🧹 Cleaning E2E environment..."
	docker-compose -f $(E2E_COMPOSE_FILE) down -v
	docker-compose -f $(E2E_COMPOSE_FILE) rm -f
	@echo "✅ E2E environment cleaned"

# E2E health check
.PHONY: e2e-health
e2e-health:
	@echo "🏥 Checking E2E environment health..."
	@echo "Backend health:"
	@curl -f http://localhost:8081/health 2>/dev/null && echo "✅ Backend OK" || echo "❌ Backend DOWN"
	@echo "Frontend health:"
	@curl -f http://localhost:5556 2>/dev/null >/dev/null && echo "✅ Frontend OK" || echo "❌ Frontend DOWN"
	@echo "Database health:"
	@docker exec geopulse-postgres-e2e pg_isready -U geopulse_test -d geopulse_test 2>/dev/null && echo "✅ Database OK" || echo "❌ Database DOWN"

#==============================================================================
# DEVELOPMENT ENVIRONMENT TARGETS
#==============================================================================

# Start development environment
.PHONY: dev-start
dev-start:
	@echo "🚀 Starting development environment..."
	@echo "  Backend: http://localhost:8080"
	@echo "  Frontend: http://localhost:5555"
	docker-compose up -d
	@echo "✅ Development environment started"

# Stop development environment
.PHONY: dev-stop
dev-stop:
	@echo "🛑 Stopping development environment..."
	docker-compose down
	@echo "✅ Development environment stopped"

# Show development environment status
.PHONY: dev-status
dev-status:
	@echo "📊 Development Environment Status:"
	@docker-compose ps

# Rebuild development containers using dev-rebuild.sh script
.PHONY: dev-rebuild-backend
dev-rebuild-backend:
	@echo "🔧 Rebuilding development backend..."
	./dev-rebuild.sh backend

.PHONY: dev-rebuild-frontend
dev-rebuild-frontend:
	@echo "🔧 Rebuilding development frontend..."
	./dev-rebuild.sh frontend

.PHONY: dev-rebuild-all
dev-rebuild-all:
	@echo "🔧 Rebuilding all development containers..."
	./dev-rebuild.sh both

# Help
.PHONY: help
help:
	@echo "GeoPulse Development & Testing System"
	@echo ""
	@echo "Usage: make [target]"
	@echo ""
	@echo "📦 BUILD & PUBLISH TARGETS:"
	@echo "  all                     Build and push all images (default)"
	@echo "  build-all              Build both backend and frontend images for multiple architectures"
	@echo "  build-all-local        Build both backend and frontend images for local architecture only"
	@echo "  push-all               Push both backend and frontend images"
	@echo "  build-backend          Build backend image for multiple architectures"
	@echo "  build-backend-local    Build backend image for local architecture only"  
	@echo "  build-frontend         Build frontend image for multiple architectures"
	@echo "  build-frontend-local   Build frontend image for local architecture only"
	@echo "  push-backend           Push backend image"
	@echo "  push-frontend          Push frontend image"
	@echo ""
	@echo "🧪 TESTING TARGETS:"
	@echo "  backend-test-unit      Run backend unit tests"
	@echo "  backend-test-integration Run backend integration tests"
	@echo "  backend-test-all       Run all backend tests (unit + integration)"
	@echo "  test-e2e               Run E2E tests (requires E2E environment)"
	@echo "  test-e2e-ui            Run E2E tests in UI mode (interactive)"
	@echo "  test-e2e-headed        Run E2E tests in headed mode (visible browser)"
	@echo "  test-e2e-debug         Debug E2E tests (step through)"
	@echo "  test-e2e-report        Show E2E test report"
	@echo "  test-e2e-full          Complete E2E workflow: start → test → report"
	@echo ""
	@echo "🧪 E2E ENVIRONMENT MANAGEMENT:"
	@echo "  e2e-start              Start E2E test environment (UI + Backend + DB)"
	@echo "  e2e-stop               Stop E2E test environment"
	@echo "  e2e-restart            Restart E2E test environment"
	@echo "  e2e-status             Show E2E environment status"
	@echo "  e2e-logs               Show E2E environment logs (follow mode)"
	@echo "  e2e-health             Check E2E environment health"
	@echo "  e2e-clean              Clean E2E environment (remove containers & volumes)"
	@echo ""
	@echo "🔧 E2E REBUILD TARGETS:"
	@echo "  e2e-rebuild-backend    Rebuild E2E backend container"
	@echo "  e2e-rebuild-frontend   Rebuild E2E frontend container"
	@echo "  e2e-rebuild-all        Rebuild all E2E containers"
	@echo ""
	@echo "🚀 DEVELOPMENT ENVIRONMENT:"
	@echo "  dev-start              Start development environment"
	@echo "  dev-stop               Stop development environment"
	@echo "  dev-status             Show development environment status"
	@echo "  dev-rebuild-backend    Rebuild development backend container"
	@echo "  dev-rebuild-frontend   Rebuild development frontend container"
	@echo "  dev-rebuild-all        Rebuild all development containers"
	@echo ""
	@echo "ℹ️  UTILITY TARGETS:"
	@echo "  version                Show current version"
	@echo "  help                   Show this help message"
	@echo ""
	@echo "🔗 QUICK WORKFLOWS:"
	@echo "  Development: make dev-start → code changes → make dev-rebuild-backend"
	@echo "  E2E Testing: make test-e2e-full (complete workflow)"
	@echo "  Bug Fixing:  make e2e-start → fix code → make e2e-rebuild-* → make test-e2e"
