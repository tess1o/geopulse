# Kubernetes Deployment Guide

This guide explains how to deploy GeoPulse to a Kubernetes cluster using the provided helper scripts and Helm.

For advanced configuration and a full list of tunable parameters, please refer to the **[official chart README](../helm/geopulse/README.md)**.

## Prerequisites

- You have cloned this repository locally.
- Kubernetes cluster (1.19+)
- `kubectl` configured to access your cluster.
- `helm` version 3.2.0+ installed.

## 1. Clone the Repository

First, ensure you have a local copy of the GeoPulse repository.

```bash
git clone https://github.com/tess1o/GeoPulse.git
cd GeoPulse
```

## 2. Make Scripts Executable

The repository includes helper scripts to manage the deployment. You need to make them executable first.

```bash
chmod +x ./helm/*.sh
```

## 3. Install GeoPulse

The recommended way to install is with the interactive script, which will guide you through the available options.

```bash
./helm/install.sh
```

The script will present several installation types, from a minimal test setup to a full production deployment, and will run the necessary `helm` commands for you.

## 4. Manage Your Deployment

You can easily start, stop, and uninstall your GeoPulse deployment using the provided scripts. This is useful for saving resources in a development environment.

### Stop GeoPulse

This command scales all components to zero, effectively pausing the application while preserving all data and configuration.

```bash
./helm/stop-geopulse.sh
```

### Start GeoPulse

This resumes the application by scaling the components back up.

```bash
./helm/start-geopulse.sh
```

### Uninstall GeoPulse

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