---
title: Proxmox VE LXC Installation
sidebar_label: Proxmox VE LXC
description: Install GeoPulse as a Proxmox VE LXC using the community-scripts.org helper script.
---

# Proxmox VE LXC Installation

GeoPulse is available through the community-maintained Proxmox VE Helper Scripts project. The script creates an updateable LXC container with GeoPulse and its required services installed.

:::info Community-maintained installer
The Proxmox script is maintained by the [Proxmox VE Helper Scripts community](https://community-scripts.org/scripts/geopulse). Review the script before running it on your host, especially for production deployments.
:::

## Prerequisites

- A Proxmox VE host.
- Access to the Proxmox VE shell as `root`.
- Internet access from the Proxmox host.
- Enough free CPU, memory, and disk for a GeoPulse LXC.

The default install profile currently runs GeoPulse in an LXC on Debian and exposes the web UI on port `80`.

## Installation Modes

The community script offers two install modes:

| Mode | Use when |
|------|----------|
| **Default** | You want the standard recommended LXC profile with minimal prompts. |
| **Advanced** | You want to customize container settings such as resources, network, storage, or other Proxmox-specific options. |

## Install GeoPulse

Run this command in the **Proxmox VE Shell**:

```bash
bash -c "$(curl -fsSL https://raw.githubusercontent.com/community-scripts/ProxmoxVE/main/ct/geopulse.sh)"
```

Follow the prompts and choose the installation mode that matches your setup.

## Access GeoPulse

After the script finishes, open GeoPulse in your browser:

```text
http://<container-ip>/
```

You can find the container IP address from the Proxmox UI or from the Proxmox shell:

```bash
pct list
pct exec <container-id> -- hostname -I
```

## Create the First Admin Account

To create the first admin account, enter the GeoPulse container and run the helper script:

```bash
pct enter <container-id>
/usr/local/bin/create-geopulse-admin
```

Then register in the GeoPulse UI using that email address.

For the rest of the setup flow, continue with the [Initial Setup Guide](../../system-administration/initial-setup).

## Configuration

The Proxmox community script stores GeoPulse configuration at:

```text
/etc/geopulse/geopulse.env
```

After changing configuration, restart the GeoPulse services inside the container according to the service names installed by the script.

For available GeoPulse settings, see the [Environment Variables Reference](./environment-variables.md).

## More Information

- [GeoPulse Proxmox community script](https://community-scripts.org/scripts/geopulse)
- [GeoPulse updates and maintenance](../../system-administration/maintenance/updating)
