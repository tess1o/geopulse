#!/bin/bash
# Stop GeoPulse - keeps installation and data intact

echo "🛑 Stopping GeoPulse..."
echo ""

echo "Scaling deployments to 0..."
kubectl scale deployment geopulse-backend --replicas=0
kubectl scale deployment geopulse-frontend --replicas=0

echo "Scaling PostgreSQL to 0..."
kubectl scale statefulset geopulse-postgres --replicas=0

# Scale MQTT if it exists
if kubectl get statefulset geopulse-mosquitto 2>/dev/null; then
  echo "Scaling MQTT to 0..."
  kubectl scale statefulset geopulse-mosquitto --replicas=0
fi

# Delete completed keygen job
kubectl delete job geopulse-keygen 2>/dev/null || true

echo ""
echo "⏳ Waiting for pods to terminate..."
sleep 5

echo ""
echo "📊 Status:"
kubectl get pods -l app.kubernetes.io/instance=geopulse

echo ""
echo "✅ GeoPulse stopped!"
echo ""
echo "💾 Data preserved in PVCs:"
kubectl get pvc -l app.kubernetes.io/instance=geopulse

echo ""
echo "▶️  To start again, run: ./helm/start-geopulse.sh"