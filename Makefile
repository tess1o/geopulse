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
	@echo "Frontend primevue image built successfully"

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
	@echo "Frontend primevue image pushed successfully"

# Show version
.PHONY: version
version:
	@echo "Current version: $(VERSION)"

# Show version
.PHONY: run-tests
run-tests:
	@echo "Running tests"
	./mvnw -pl backend clean test

# Help
.PHONY: help
help:
	@echo "GeoPulse Docker Image Build System"
	@echo ""
	@echo "Usage:"
	@echo "  make [target]"
	@echo ""
	@echo "Targets:"
	@echo "  all                Build and push all images (default)"
	@echo "  build-all          Build both backend and frontend images for multiple architectures"
	@echo "  build-all-local    Build both backend and frontend images for local architecture only"
	@echo "  push-all           Push both backend and frontend images"
	@echo "  build-backend      Build backend image for multiple architectures"
	@echo "  build-backend-local Build backend image for local architecture only"
	@echo "  build-frontend     Build frontend image for multiple architectures"
	@echo "  build-frontend-local Build frontend image for local architecture only"
	@echo "  push-backend       Push backend image"
	@echo "  push-frontend      Push frontend image"
	@echo "  version            Show current version"
	@echo "  help               Show this help message"
