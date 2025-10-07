# Makefile for GeoPulse Docker image building and publishing

# Variables
VERSION := $(shell grep -m1 "<version>" pom.xml | sed -E 's/.*<version>(.*)<\/version>.*/\1/')
BACKEND_IMAGE := tess1o/geopulse-backend
FRONTEND_IMAGE := tess1o/geopulse-ui
PLATFORMS := linux/amd64,linux/arm64

# Default target
.PHONY: all
all: build-all push-all

# Build both backend and frontend images for multiple architectures
.PHONY: build-all
build-all: build-backend build-frontend

# Build both backend and frontend images for local architecture only
.PHONY: build-all-local
build-all-local: build-backend-local build-frontend-local

# Push both backend and frontend images
.PHONY: push-all
push-all: push-backend push-frontend

.PHONY: build-backend-native
build-backend-native:
	@echo "Building native backend Docker image for multiple architectures..."
	@docker buildx create --name geopulse-builder --use --bootstrap || true
	docker buildx build --platform $(PLATFORMS) \
		-t $(BACKEND_IMAGE):$(VERSION)-native \
		-t $(BACKEND_IMAGE):native-latest \
		--build-arg VERSION=$(VERSION) \
		-f backend/Dockerfile.native \
		--push \
		.
	@echo "✅ Native multi-arch backend image built and pushed successfully."

# ==========================
# Build for ARM64 only
# ==========================
.PHONY: build-backend-native-arm64
build-backend-native-arm64:
	@echo "Building native backend Docker image for ARM64..."
	@docker buildx create --name geopulse-builder --use --bootstrap || true
	docker buildx build --platform linux/arm64 \
		-t $(BACKEND_IMAGE):$(VERSION)-native-arm64 \
		-t $(BACKEND_IMAGE):native-arm64-latest \
		--build-arg VERSION=$(VERSION) \
		-f backend/Dockerfile.native \
		--push \
		.
	@echo "✅ ARM64 native backend image built and pushed successfully."

# ==========================
# Build for AMD64 only
# ==========================
.PHONY: build-backend-native-amd64
build-backend-native-amd64:
	@echo "Building native backend Docker image for AMD64..."
	@docker buildx create --name geopulse-builder --use --bootstrap || true
	docker buildx build --platform linux/amd64 \
		-t $(BACKEND_IMAGE):$(VERSION)-native-amd64 \
		-t $(BACKEND_IMAGE):native-amd64-latest \
		--build-arg VERSION=$(VERSION) \
		-f backend/Dockerfile.native \
		--push \
		.
	@echo "✅ AMD64 native backend image built and pushed successfully."

# ------------------------------
# Build both sequentially + multi-arch manifest
# ------------------------------
.PHONY: build-backend-native
build-backend-native: build-backend-native-arm64 build-backend-native-amd64
	@echo "Creating multi-arch manifest..."
	docker manifest create $(BACKEND_IMAGE):$(VERSION)-native \
		$(BACKEND_IMAGE):$(VERSION)-native-amd64 \
		$(BACKEND_IMAGE):$(VERSION)-native-arm64

	docker manifest create $(BACKEND_IMAGE):native-latest \
		$(BACKEND_IMAGE):native-amd64-latest \
		$(BACKEND_IMAGE):native-arm64-latest

	docker manifest push $(BACKEND_IMAGE):$(VERSION)-native
	docker manifest push $(BACKEND_IMAGE):native-latest
	@echo "✅ Multi-arch native images built and pushed successfully."

# Build multi-architecture backend image
.PHONY: build-backend
build-backend:
	@echo "Building backend Docker image for multiple architectures..."
	docker buildx create --name geopulse-builder --use --bootstrap || true
	docker buildx build --platform $(PLATFORMS) \
		-t $(BACKEND_IMAGE):$(VERSION) \
		-t $(BACKEND_IMAGE):latest \
		--build-arg VERSION=$(VERSION) \
		-f backend/Dockerfile \
		--push \
		.
	@echo "Backend image built successfully"

# Build backend image for local architecture only
.PHONY: build-backend-local
build-backend-local:
	@echo "Building backend Docker image for local architecture..."
	docker build \
		-t $(BACKEND_IMAGE):$(VERSION) \
		-t $(BACKEND_IMAGE):latest \
		--build-arg VERSION=$(VERSION) \
		-f backend/Dockerfile \
		.
	@echo "Backend image built successfully"

# Build multi-architecture frontend image
.PHONY: build-frontend
build-frontend:
	@echo "Building frontend Docker image for multiple architectures..."
	docker buildx create --name geopulse-builder --use --bootstrap || true
	docker buildx build --platform $(PLATFORMS) \
		-t $(FRONTEND_IMAGE):$(VERSION) \
		-t $(FRONTEND_IMAGE):latest \
		--build-arg VERSION=$(VERSION) \
		-f frontend/Dockerfile \
		--push \
		.
	@echo "Frontend image built successfully"

.PHONY: build-frontend-local
build-frontend-local:
	@echo "Building frontend Docker image for local architecture..."
	docker build \
		-t $(FRONTEND_IMAGE):$(VERSION) \
		-t $(FRONTEND_IMAGE):latest \
		--build-arg VERSION=$(VERSION) \
		-f frontend/Dockerfile \
		.
	@echo "Frontend image built successfully"

# Push backend image to Docker Hub
.PHONY: push-backend
push-backend:
	@echo "Pushing backend Docker image to Docker Hub..."
	docker buildx build --platform $(PLATFORMS) \
		-t $(BACKEND_IMAGE):$(VERSION) \
		-t $(BACKEND_IMAGE):latest \
		--build-arg VERSION=$(VERSION) \
		-f backend/Dockerfile \
		--push \
		.
	@echo "Backend image pushed successfully"

.PHONY: push-frontend
push-frontend:
	@echo "Pushing frontend Docker image to Docker Hub..."
	docker buildx build --platform $(PLATFORMS) \
		-t $(FRONTEND_IMAGE):$(VERSION) \
		-t $(FRONTEND_IMAGE):latest \
		--build-arg VERSION=$(VERSION) \
		-f frontend/Dockerfile \
		--push \
		.
	@echo "Frontend image pushed successfully"

# Show version
.PHONY: version
version:
	@echo "Current version: $(VERSION)"

# Backend unit tests
.PHONY: test-unit
test-unit:
	@echo "Running backend unit tests"
	./mvnw -pl backend clean test

# Legacy alias for backwards compatibility
.PHONY: run-tests
run-tests: test-unit

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
	@echo "  test-unit              Run backend unit tests"
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
