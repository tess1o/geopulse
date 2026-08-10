---
title: Install GeoPulse
description: Compare GeoPulse installation methods and choose the right deployment path for your server or homelab.
slug: /getting-started/deployment
---

# Install GeoPulse

GeoPulse can run on a single Docker host, a NAS or homelab platform, Kubernetes, or a manually managed Linux server. Start with the path that matches your environment, then use the upgrade and configuration references when you are ready to maintain the deployment.

## Choose an Installation Method

| Method | Best For | Start Here |
|---|---|---|
| Docker Compose | Fastest setup for most single-server installs | [Docker Compose](./docker-compose.md) |
| Unraid | NAS and homelab deployments managed through Unraid | [Unraid](./unraid.md) |
| Proxmox VE LXC | Proxmox hosts using the community helper script | [Proxmox VE LXC](./proxmox-lxc.md) |
| Kubernetes Quick Install | Scripted Kubernetes installation with Helm | [Kubernetes Quick Install](./kubernetes-helm.md) |
| Helm Values Reference | Advanced Kubernetes configuration and chart values | [Helm Values Reference](./helm-deployment.md) |
| Manual Installation | Advanced bare-metal or VM installs without containers | [Manual Installation](./manual-installation.md) |

## After Installation

- Complete the [Quick Start](/docs/getting-started/quick-start) to reach first login and connect your first location source.
- Review [Environment Variables](/docs/getting-started/deployment/environment-variables) when customizing runtime behavior.
- Follow [Upgrading GeoPulse](/docs/system-administration/maintenance/updating) when moving between versions.

## Recommended Path

For most users, start with [Docker Compose](./docker-compose.md). It is the simplest supported deployment path and maps directly to the default GeoPulse configuration.
