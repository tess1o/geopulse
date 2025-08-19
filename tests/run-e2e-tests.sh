#!/bin/bash

# GeoPulse E2E Test Runner
# This script provides an easy way to run E2E tests with proper setup and cleanup

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to cleanup on exit
cleanup() {
    print_status "Cleaning up test environment..."
    docker-compose -f docker-compose.e2e.yml down -v --remove-orphans || true
}

# Set trap to cleanup on exit
trap cleanup EXIT

# Function to check if required tools are installed
check_requirements() {
    print_status "Checking requirements..."
    
    if ! command -v docker &> /dev/null; then
        print_error "Docker is required but not installed."
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        print_error "Docker Compose is required but not installed."
        exit 1
    fi
    
    print_success "All requirements met"
}

# Function to check if ports are available
check_ports() {
    print_status "Checking if required ports are available..."
    
    local ports=(5433 5556 8081)
    local busy_ports=()
    
    for port in "${ports[@]}"; do
        if lsof -i:$port &> /dev/null; then
            busy_ports+=($port)
        fi
    done
    
    if [ ${#busy_ports[@]} -gt 0 ]; then
        print_warning "The following ports are in use: ${busy_ports[*]}"
        print_warning "You may need to stop other services or the tests may fail"
        read -p "Continue anyway? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    else
        print_success "All required ports are available"
    fi
}

# Function to wait for services to be healthy
wait_for_services() {
    print_status "Waiting for services to be healthy..."
    
    local max_attempts=30
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        if docker-compose -f docker-compose.e2e.yml ps | grep -q "healthy"; then
            local healthy_count=$(docker-compose -f docker-compose.e2e.yml ps | grep -c "healthy" || echo "0")
            if [ "$healthy_count" -ge 3 ]; then
                print_success "All services are healthy"
                return 0
            fi
        fi
        
        print_status "Waiting for services... (attempt $attempt/$max_attempts)"
        sleep 5
        ((attempt++))
    done
    
    print_error "Services failed to become healthy within the timeout period"
    print_status "Showing service status:"
    docker-compose -f docker-compose.e2e.yml ps
    print_status "Showing service logs:"
    docker-compose -f docker-compose.e2e.yml logs --tail=50
    return 1
}

# Function to run the E2E tests
run_tests() {
    print_status "Starting E2E test environment..."
    
    # Pull latest images
    print_status "Pulling latest images..."
    docker-compose -f docker-compose.e2e.yml pull
    
    # Start services
    print_status "Starting services..."
    docker-compose -f docker-compose.e2e.yml up -d geopulse-postgres-e2e geopulse-backend-e2e geopulse-ui-e2e
    
    # Wait for services to be healthy
    if ! wait_for_services; then
        print_error "Failed to start services"
        return 1
    fi
    
    # Run tests
    print_status "Running E2E tests..."
    if docker-compose -f docker-compose.e2e.yml up --exit-code-from playwright-tests playwright-tests; then
        print_success "All E2E tests passed!"
        return 0
    else
        print_error "Some E2E tests failed"
        return 1
    fi
}

# Function to show test results
show_results() {
    print_status "Test artifacts:"
    
    if [ -d "test-results" ]; then
        echo "  📁 Test results: ./test-results/"
    fi
    
    if [ -d "playwright-report" ]; then
        echo "  📄 HTML report: ./playwright-report/index.html"
        echo "  🌐 View report: npm run test:e2e:report"
    fi
    
    print_status "Logs available with: npm run test:e2e:logs"
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -h, --help     Show this help message"
    echo "  -c, --clean    Clean up test environment and exit"
    echo "  -q, --quiet    Suppress non-essential output"
    echo "  -v, --verbose  Show verbose output"
    echo ""
    echo "Examples:"
    echo "  $0                 # Run E2E tests"
    echo "  $0 --clean         # Clean up test environment"
    echo "  $0 --verbose       # Run with verbose output"
}

# Parse command line arguments
CLEAN_ONLY=false
QUIET=false
VERBOSE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_usage
            exit 0
            ;;
        -c|--clean)
            CLEAN_ONLY=true
            shift
            ;;
        -q|--quiet)
            QUIET=true
            shift
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        *)
            print_error "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Set verbosity
if [ "$VERBOSE" = true ]; then
    set -x
fi

if [ "$QUIET" = false ]; then
    echo ""
    echo "🧪 GeoPulse E2E Test Runner"
    echo "=================================="
    echo ""
fi

# If clean only, just cleanup and exit
if [ "$CLEAN_ONLY" = true ]; then
    print_status "Cleaning up test environment..."
    docker-compose -f docker-compose.e2e.yml down -v --remove-orphans
    docker system prune -f
    print_success "Cleanup completed"
    exit 0
fi

# Main execution
main() {
    check_requirements
    check_ports
    
    if run_tests; then
        print_success "E2E tests completed successfully! 🎉"
        show_results
        exit 0
    else
        print_error "E2E tests failed! 😞"
        show_results
        exit 1
    fi
}

# Run main function
main "$@"