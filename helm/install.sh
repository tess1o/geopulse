#!/bin/bash

# GeoPulse Helm Chart Installation Script
# This script helps you install GeoPulse to your Kubernetes cluster

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
RELEASE_NAME="${RELEASE_NAME:-geopulse}"
NAMESPACE="${NAMESPACE:-default}"
CHART_PATH="$(dirname "$0")/geopulse"

echo -e "${BLUE}"
echo "╔════════════════════════════════════════╗"
echo "║   GeoPulse Kubernetes Installation    ║"
echo "╔════════════════════════════════════════╗"
echo -e "${NC}"

# Check prerequisites
echo -e "${YELLOW}Checking prerequisites...${NC}"

if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}✗ kubectl not found. Please install kubectl first.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ kubectl found${NC}"

if ! command -v helm &> /dev/null; then
    echo -e "${RED}✗ helm not found. Please install Helm 3.x first.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ helm found ($(helm version --short))${NC}"

if ! kubectl cluster-info &> /dev/null; then
    echo -e "${RED}✗ Cannot connect to Kubernetes cluster${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Kubernetes cluster accessible${NC}"

echo ""

# Installation options
echo -e "${BLUE}Installation Options:${NC}"
echo "1. Minimal (no persistence, for testing)"
echo "2. Standard (with persistence)"
echo "3. Production (with ingress, TLS, HA)"
echo "4. With MQTT support"
echo "5. Custom (use your values file)"
echo ""
read -p "Select option (1-5): " OPTION

VALUES_ARGS=""

case $OPTION in
    1)
        echo -e "${YELLOW}Installing minimal configuration...${NC}"
        VALUES_ARGS="--set postgres.persistence.enabled=false --set keygen.persistence.enabled=false"
        ;;
    2)
        echo -e "${YELLOW}Installing standard configuration...${NC}"
        # Use default values
        ;;
    3)
        echo -e "${YELLOW}Production installation requires configuration...${NC}"
        read -p "Enter your domain name (e.g., geopulse.example.com): " DOMAIN
        if [ -z "$DOMAIN" ]; then
            echo -e "${RED}Domain name is required for production installation${NC}"
            exit 1
        fi
        VALUES_ARGS="--set ingress.enabled=true \
                     --set ingress.hostname=$DOMAIN \
                     --set ingress.tls.enabled=true \
                     --set config.uiUrl=https://$DOMAIN \
                     --set config.authSecureCookies=true \
                     --set backend.replicaCount=2 \
                     --set frontend.replicaCount=2"
        echo -e "${YELLOW}Note: Make sure cert-manager is installed for TLS certificates${NC}"
        ;;
    4)
        echo -e "${YELLOW}Installing with MQTT support...${NC}"
        VALUES_ARGS="--set mosquitto.enabled=true"
        ;;
    5)
        read -p "Enter path to your values file: " VALUES_FILE
        if [ ! -f "$VALUES_FILE" ]; then
            echo -e "${RED}Values file not found: $VALUES_FILE${NC}"
            exit 1
        fi
        VALUES_ARGS="-f $VALUES_FILE"
        ;;
    *)
        echo -e "${RED}Invalid option${NC}"
        exit 1
        ;;
esac

echo ""
read -p "Install to namespace '$NAMESPACE' with release name '$RELEASE_NAME'? (y/n): " CONFIRM
if [ "$CONFIRM" != "y" ]; then
    echo -e "${YELLOW}Installation cancelled${NC}"
    exit 0
fi

# Create namespace if it doesn't exist
if ! kubectl get namespace "$NAMESPACE" &> /dev/null; then
    echo -e "${YELLOW}Creating namespace: $NAMESPACE${NC}"
    kubectl create namespace "$NAMESPACE"
fi

echo ""
echo -e "${BLUE}Installing GeoPulse...${NC}"
echo ""

# Run Helm install
if helm install "$RELEASE_NAME" "$CHART_PATH" \
    --namespace "$NAMESPACE" \
    $VALUES_ARGS; then

    echo ""
    echo -e "${GREEN}╔════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║  Installation completed successfully! ║${NC}"
    echo -e "${GREEN}╔════════════════════════════════════════╗${NC}"
    echo ""

    echo -e "${BLUE}Next steps:${NC}"
    echo ""
    echo "1. Check deployment status:"
    echo -e "   ${YELLOW}kubectl get pods -n $NAMESPACE -l app.kubernetes.io/instance=$RELEASE_NAME${NC}"
    echo ""
    echo "2. Wait for all pods to be ready:"
    echo -e "   ${YELLOW}kubectl wait --for=condition=ready pod -n $NAMESPACE -l app.kubernetes.io/instance=$RELEASE_NAME --timeout=300s${NC}"
    echo ""
    echo "3. Access GeoPulse:"

    if [ "$OPTION" == "3" ]; then
        echo -e "   ${YELLOW}https://$DOMAIN${NC}"
    else
        echo -e "   ${YELLOW}kubectl port-forward -n $NAMESPACE svc/$RELEASE_NAME-frontend 5555:80${NC}"
        echo -e "   Then visit: ${YELLOW}http://localhost:5555${NC}"
    fi

    echo ""
    echo "4. View release notes:"
    echo -e "   ${YELLOW}helm status $RELEASE_NAME -n $NAMESPACE${NC}"
    echo ""
    echo "5. Run tests:"
    echo -e "   ${YELLOW}helm test $RELEASE_NAME -n $NAMESPACE${NC}"
    echo ""

else
    echo ""
    echo -e "${RED}Installation failed!${NC}"
    echo ""
    echo "Check the error messages above and try again."
    echo "For help, visit: https://github.com/tess1o/geopulse/issues"
    exit 1
fi