#!/bin/bash
# Start GeoPulse - restarts after stopping

echo "▶️  Starting GeoPulse..."
echo ""

echo "Scaling PostgreSQL to 1..."
kubectl scale statefulset geopulse-postgres --replicas=1

echo "Waiting for PostgreSQL to be ready..."
kubectl wait --for=condition=ready pod -l app.kubernetes.io/component=database --timeout=120s

echo ""
echo "Scaling deployments to 1..."
kubectl scale deployment geopulse-backend --replicas=1
kubectl scale deployment geopulse-frontend --replicas=1

# Scale MQTT if it exists
if kubectl get statefulset geopulse-mosquitto 2>/dev/null; then
  echo "Scaling MQTT to 1..."
  kubectl scale statefulset geopulse-mosquitto --replicas=1
fi

echo ""
echo "⏳ Waiting for pods to be ready..."
sleep 5

echo ""
echo "📊 Status:"
kubectl get pods -l app.kubernetes.io/instance=geopulse

echo ""
echo "✅ GeoPulse started!"
echo ""
echo "🌐 Access frontend: kubectl port-forward svc/geopulse-frontend 5555:80"
echo "🛑 To stop again, run: ./helm/stop-geopulse.sh"