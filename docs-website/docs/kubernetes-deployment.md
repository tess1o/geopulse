# Kubernetes Deployment

This guide explains how to deploy GeoPulse to a Kubernetes cluster using the provided helper scripts and Helm.

For advanced configuration and a full list of tunable parameters, please refer to the **[official chart README](../helm/geopulse/README.md)**.

## Prerequisites

- You have cloned this repository locally.
- Kubernetes cluster (1.19+)
- `kubectl` configured to access your cluster.
- `helm` version 3.2.0+ installed.

## Installation from helm repository
```bash
helm repo add geopulse https://tess1o.github.io/geopulse/charts
helm repo update
helm install my-geopulse geopulse/geopulse
```

## Installation from source code

### 1. Clone the Repository

First, ensure you have a local copy of the GeoPulse repository.

```bash
git clone https://github.com/tess1o/GeoPulse.git
cd GeoPulse
```

### 2. Make Scripts Executable

The repository includes helper scripts to manage the deployment. You need to make them executable first.

```bash
chmod +x ./helm/*.sh
```

### 3. Install GeoPulse

The recommended way to install is with the interactive script, which will guide you through the available options.

```bash
./helm/install.sh
```

The script will present several installation types, from a minimal test setup to a full production deployment, and will run the necessary `helm` commands for you.

### 4. Manage Your Deployment

You can easily start, stop, and uninstall your GeoPulse deployment using the provided scripts. This is useful for saving resources in a development environment.

#### Stop GeoPulse

This command scales all components to zero, effectively pausing the application while preserving all data and configuration.

```bash
./helm/stop-geopulse.sh
```

#### Start GeoPulse

This resumes the application by scaling the components back up.

```bash
./helm/start-geopulse.sh
```

#### Uninstall GeoPulse

This will completely remove the GeoPulse deployment from your cluster.

**Warning:** By default, this will also delete the Persistent Volume Claims (PVCs), which means **all your data will be permanently deleted.**

```bash
./helm/uninstall-geopulse.sh
```

## Advanced Customization

If the interactive installer does not cover your needs, you can bypass it and use `helm` directly with a custom values file.

1.  **Review Example Configurations:** The `helm/examples/` directory contains templates for different scenarios, including the new `medium-deployment.yaml`.
2.  **Create `my-values.yaml`:** Copy and modify an example to create your own configuration.
3.  **Install Manually:**
    ```bash
    helm install geopulse ./helm/geopulse -f my-values.yaml
    ```

For a complete list of all available parameters, consult the **[Helm Chart README](../helm/geopulse/README.md)**.


## GeoPulse Installation Troubleshooting

This guide covers common issues during GeoPulse Helm installation on k3s and how to fix them.

---

## 1. Error: ✗ Cannot connect to Kubernetes cluster

**Symptom:**
```
✗ Cannot connect to Kubernetes cluster
```

**Cause:** kubectl cannot access your k3s cluster from the current user environment.

**Fix:**

Ensure k3s is running:
```bash
sudo systemctl status k3s
```

Make kubeconfig available to your user:
```bash
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown $USER:$USER ~/.kube/config
```

Export KUBECONFIG (if not already set):
```bash
export KUBECONFIG=~/.kube/config
```

Test connectivity:
```bash
kubectl get nodes
kubectl cluster-info
```

Re-run the installer:
```bash
./helm/install.sh
```

**Note:** If you previously ran the script with sudo, try running it without sudo after setting KUBECONFIG.

---

## 2. Error: INSTALLATION FAILED: Kubernetes cluster unreachable

**Symptom:**
```
Error: INSTALLATION FAILED: Kubernetes cluster unreachable: the server could not find the requested resource
```

**Cause:** Helm cannot find the cluster because `$KUBECONFIG` is missing or not passed to sudo.

**Fix:**

**Option 1 — Run without sudo (recommended):**
```bash
./helm/install.sh
```

**Option 2 — Run with sudo and explicit KUBECONFIG:**
```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml ./helm/install.sh
```

**Option 3 — Patch the install script to automatically export kubeconfig:**
```bash
if [ -z "$KUBECONFIG" ]; then
    export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
fi
```

---

## 3. Accessing GeoPulse UI

The GeoPulse frontend service (`geopulse-frontend`) listens on port 80 in the cluster.

### Option 1 — Port-forward (temporary)

```bash
kubectl port-forward -n default svc/geopulse-frontend 5555:80 --address 0.0.0.0
```

Access from any machine on the network:
```
http://<server-ip>:5555
```

Replace `<server-ip>` with your k3s host LAN IP:
```bash
hostname -I | awk '{print $1}'
```

### Option 2 — Change service type to NodePort (persistent)

```bash
kubectl edit svc geopulse-frontend -n default
```

Change:
```yaml
type: ClusterIP
```

to:
```yaml
type: NodePort
```

Save and get the assigned NodePort:
```bash
kubectl get svc -n default
```

Access the UI from a network machine:
```
http://<server-ip>:<node-port>
```
