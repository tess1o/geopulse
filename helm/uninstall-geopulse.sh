#!/bin/bash
# Uninstall GeoPulse - removes everything including data

echo "⚠️  WARNING: This will DELETE ALL GeoPulse data including:"
echo "  - Helm release"
echo "  - All pods and services"
echo "  - PostgreSQL database (ALL YOUR DATA!)"
echo "  - JWT keys"
echo "  - Mosquitto data (if installed)"
echo "  - All persistent volumes"
echo "  - All secrets"
echo ""
read -p "Are you sure you want to continue? (type 'yes' to confirm): " confirm

if [ "$confirm" != "yes" ]; then
    echo "❌ Uninstall cancelled."
    exit 0
fi

echo ""
echo "🗑️  Uninstalling GeoPulse..."
echo ""

# Check if release exists
if ! helm list -n default | grep -q "geopulse"; then
    echo "⚠️  GeoPulse helm release not found in namespace 'default'"
    echo "Checking for resources anyway..."
else
    echo "📦 Uninstalling helm release..."
    helm uninstall geopulse -n default
    echo "✅ Helm release uninstalled"
fi

echo ""
echo "🔐 Deleting secrets..."
kubectl delete secret geopulse-secrets -n default --ignore-not-found=true
echo "✅ Secrets deleted"

echo ""
echo "💾 Deleting persistent volume claims..."
kubectl delete pvc -l app.kubernetes.io/instance=geopulse -n default --ignore-not-found=true

# Also delete by name in case labels don't match
kubectl delete pvc data-geopulse-postgres-0 -n default --ignore-not-found=true 2>/dev/null
kubectl delete pvc geopulse-keys -n default --ignore-not-found=true 2>/dev/null
kubectl delete pvc data-geopulse-mosquitto-0 -n default --ignore-not-found=true 2>/dev/null
kubectl delete pvc logs-geopulse-mosquitto-0 -n default --ignore-not-found=true 2>/dev/null
kubectl delete pvc config-geopulse-mosquitto-0 -n default --ignore-not-found=true 2>/dev/null

echo "✅ Persistent volume claims deleted"

echo ""
echo "🧹 Cleaning up any remaining resources..."

# Clean up any leftover configmaps
kubectl delete configmap geopulse-config -n default --ignore-not-found=true 2>/dev/null
kubectl delete configmap geopulse-mosquitto-config -n default --ignore-not-found=true 2>/dev/null

# Clean up any leftover services
kubectl delete service -l app.kubernetes.io/instance=geopulse -n default --ignore-not-found=true 2>/dev/null

# Clean up any leftover deployments/statefulsets
kubectl delete deployment -l app.kubernetes.io/instance=geopulse -n default --ignore-not-found=true 2>/dev/null
kubectl delete statefulset -l app.kubernetes.io/instance=geopulse -n default --ignore-not-found=true 2>/dev/null

# Clean up any leftover jobs
kubectl delete job -l app.kubernetes.io/instance=geopulse -n default --ignore-not-found=true 2>/dev/null

echo "✅ Cleanup complete"

echo ""
echo "🔍 Checking for any remaining GeoPulse resources..."
remaining=$(kubectl get all,pvc,secret,configmap -n default -l app.kubernetes.io/instance=geopulse 2>/dev/null)

if [ -z "$remaining" ]; then
    echo "✅ No remaining GeoPulse resources found"
else
    echo "⚠️  Some resources may still exist:"
    echo "$remaining"
fi

echo ""
echo "✅ GeoPulse uninstall complete!"
echo ""
echo "To reinstall GeoPulse, run:"
echo "  helm install geopulse ./helm/geopulse --namespace default --set mosquitto.enabled=true"
