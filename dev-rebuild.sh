#!/bin/bash

# GeoPulse Development Container Rebuild Script
# Usage: ./dev-rebuild.sh [backend|frontend|both] [--env=dev|e2e]

set -e

COMPONENT=${1:-both}
ENVIRONMENT="dev"

# Parse environment argument
for arg in "$@"; do
    case $arg in
        --env=*)
        ENVIRONMENT="${arg#*=}"
        shift
        ;;
    esac
done

# Set environment-specific variables
if [[ "$ENVIRONMENT" == "e2e" ]]; then
    COMPOSE_FILE="tests/docker-compose.e2e.yml"
    BACKEND_CONTAINER="geopulse-backend-e2e"
    FRONTEND_CONTAINER="geopulse-ui-e2e"
    BACKEND_PORT="8081"
    FRONTEND_PORT="5556"
    echo "🧪 Using E2E testing environment"
else
    COMPOSE_FILE="docker-compose.yml"
    BACKEND_CONTAINER="geopulse-backend"
    FRONTEND_CONTAINER="geopulse-ui"
    BACKEND_PORT="8080"
    FRONTEND_PORT="5555"
    echo "🚀 Using development environment"
fi

rebuild_backend() {
    echo "🔧 Rebuilding backend container ($BACKEND_CONTAINER)..."
    
    if [[ "$ENVIRONMENT" == "e2e" ]]; then
        # E2E uses build context, rebuild directly
        echo "📦 Building backend for E2E..."
        docker-compose -f $COMPOSE_FILE build geopulse-backend-e2e
    else
        # Dev uses pre-built images
        echo "📦 Building backend image..."
        make build-backend-local
    fi
    
    # Restart backend container
    echo "🔄 Restarting backend container..."
    docker-compose -f $COMPOSE_FILE stop $BACKEND_CONTAINER
    docker-compose -f $COMPOSE_FILE rm -f $BACKEND_CONTAINER
    docker-compose -f $COMPOSE_FILE up -d $BACKEND_CONTAINER
    
    echo "✅ Backend rebuilt and restarted!"
}

rebuild_frontend() {
    echo "🔧 Rebuilding frontend container ($FRONTEND_CONTAINER)..."
    
    if [[ "$ENVIRONMENT" == "e2e" ]]; then
        # E2E uses build context, rebuild directly
        echo "📦 Building frontend for E2E..."
        docker-compose -f $COMPOSE_FILE build geopulse-ui-e2e
    else
        # Dev uses pre-built images
        echo "📦 Building frontend image..."
        make build-frontend-local
    fi
    
    # Restart frontend container
    echo "🔄 Restarting frontend container..."
    docker-compose -f $COMPOSE_FILE stop $FRONTEND_CONTAINER
    docker-compose -f $COMPOSE_FILE rm -f $FRONTEND_CONTAINER
    docker-compose -f $COMPOSE_FILE up -d $FRONTEND_CONTAINER
    
    echo "✅ Frontend rebuilt and restarted!"
}

case $COMPONENT in
    "backend")
        rebuild_backend
        ;;
    "frontend")
        rebuild_frontend
        ;;
    "both")
        rebuild_backend
        rebuild_frontend
        ;;
    *)
        echo "❌ Usage: ./dev-rebuild.sh [backend|frontend|both] [--env=dev|e2e]"
        echo ""
        echo "Components:"
        echo "   backend  - Rebuild only backend container"
        echo "   frontend - Rebuild only frontend container" 
        echo "   both     - Rebuild both containers (default)"
        echo ""
        echo "Environments:"
        echo "   --env=dev - Development environment (default)"
        echo "               Containers: geopulse-backend, geopulse-ui"
        echo "               Ports: :8080, :5555"
        echo ""
        echo "   --env=e2e - E2E testing environment"
        echo "               Containers: geopulse-backend-e2e, geopulse-ui-e2e"
        echo "               Ports: :8081, :5556"
        echo ""
        echo "Examples:"
        echo "   ./dev-rebuild.sh backend          # Rebuild dev backend"
        echo "   ./dev-rebuild.sh both --env=e2e   # Rebuild E2E containers"
        exit 1
        ;;
esac

echo ""
echo "🎉 Container rebuild complete!"
echo "📊 Backend: http://localhost:$BACKEND_PORT"
echo "🌐 Frontend: http://localhost:$FRONTEND_PORT"
echo ""

if [[ "$ENVIRONMENT" == "e2e" ]]; then
    echo "💡 To run E2E tests:"
    echo "   cd tests && npm run test:e2e"
else
    echo "💡 To run E2E tests against dev environment:"
    echo "   cd tests && BASE_URL=http://localhost:5555 API_BASE_URL=http://localhost:8080 npm run test:e2e"
fi