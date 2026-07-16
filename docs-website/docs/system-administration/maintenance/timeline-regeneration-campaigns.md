---
title: Timeline Regeneration Campaigns
description: Force timeline regeneration for affected users after important timeline logic changes or timeline corruption.
---

# Timeline Regeneration Campaigns

Timeline regeneration campaigns let administrators force GeoPulse to rebuild user timelines from a selected point in
time. They are used when an important change in timeline generation logic needs to be applied to existing data, or when
a bug corrupted generated timeline entries.

Campaigns can be created in two ways:

| Source | Description |
|--------|-------------|
| `RELEASE` | Created by GeoPulse developers as part of an application release. Use these when release notes or the Admin Panel show that a release campaign was scheduled automatically. |
| `ADMIN` | Created manually by an administrator from the Admin Panel. Use these for instance-specific timeline repair or after an administrator confirms that existing timelines need to be regenerated. |

:::info No user action required
Users do not need to manually regenerate their own timelines. When a campaign is created, GeoPulse queues the affected
users automatically and processes them in the background. Users may receive a "Timeline refresh scheduled" notification,
but the notification is informational.
:::

## When to Create a Campaign

Create an admin campaign only when existing generated timeline data is known or strongly suspected to be wrong.

Good reasons include:

- a timeline generation bug produced corrupted stays, trips, or gaps
- an important timeline logic change should be applied to already-generated historical data
- an operational repair requires rebuilding timelines for many users from the same timestamp

Do not create a campaign for routine user preference changes, favorite place changes, or imports that already trigger
their own timeline regeneration flow.

## How Campaigns Work

1. An admin selects a **Regenerate From** timestamp.
2. GeoPulse previews the number of users with GPS points at or after that timestamp.
3. After confirmation, GeoPulse creates one campaign entry and one queued work item for each affected user.
4. A background worker regenerates each affected user's timeline from the selected timestamp.
5. While a user has an active campaign work item, normal real-time timeline processing is skipped for that user to avoid overlapping timeline jobs.
6. The campaign completes when every affected user has completed or been skipped. Campaigns with failed users remain active until the failures are retried or otherwise resolved.

The selected timestamp matters. Choose the earliest point that could be affected by the bug or logic change, but avoid
unnecessarily old timestamps because regenerating more history takes longer.

## Create a Campaign

Navigate to **Admin Dashboard > Timeline Regeneration** or open:

```text
/app/admin/timeline-regeneration-campaigns
```

To create a campaign:

1. Click **Create Campaign**.
2. Enter a unique **Campaign Key**. The key can be up to 120 characters and is used to identify this campaign in the Admin Panel and audit logs.
3. Select **Regenerate From**. Users with GPS data at or after this timestamp are eligible.
4. Enter a clear **Reason**. Users can see this message in the timeline refresh notification, so keep it concise and user-facing.
5. Click **Run Preview** to count affected users.
6. Click **Review Create** and verify the campaign key, timestamp, reason, and affected user count.
7. Click **Create Campaign**.

After creation, the campaign appears in the campaign list and starts processing automatically on the next campaign worker
run.

## Monitor Progress

The Timeline Regeneration page lists campaigns newest first. Use **Refresh** to reload the latest counters.

| Column | Description |
|--------|-------------|
| **Campaign** | Campaign key and reason |
| **Source** | `ADMIN` for campaigns created by admins, `RELEASE` for campaigns shipped by developers |
| **Status** | Overall campaign status |
| **Affected From** | Timestamp from which timelines are rebuilt |
| **Progress** | Processed users compared with total affected users |
| **Failed** | Number of users whose campaign work item failed |
| **Created** | Campaign creation time |
| **Completed** | Completion time, when available |

Campaign statuses:

| Status | Meaning |
|--------|---------|
| `ACTIVE` | The campaign still has pending, running, or failed user work items. |
| `COMPLETED` | All affected users completed or were skipped. |
| `CANCELLED` | The campaign was stopped by an internal or release-level operation. Cancelled campaigns cannot be retried from the Admin Panel. |

Click **View Details** to inspect a campaign. The details view shows total, pending, running, completed, failed, and
skipped counts. It also lists failed users with their email, name or user ID, attempt count, last error, and last update
time.

## Retry Failed Users

GeoPulse automatically retries campaign work items up to the configured maximum attempt count. If users remain failed
after the underlying problem is fixed:

1. Open **Timeline Regeneration**.
2. Find the campaign with failed users.
3. Click **Retry Failed Users** from the list or campaign details dialog.

Only failed users are queued again. Completed users are not regenerated a second time by the retry action.

## User Experience

Affected users may see a notification titled **Timeline refresh scheduled**. The campaign reason is used as the
notification message and the notification links to the user's timeline jobs page.

No confirmation or manual action is required from the user. The background worker creates and runs the timeline
regeneration job for each affected user.

## Audit Logs

Admin-created campaigns and failed-user retries are recorded in **Admin Dashboard > Audit Logs**.

| Action | When it is written |
|--------|--------------------|
| `TIMELINE_REGENERATION_CAMPAIGN_CREATED` | An admin creates a campaign. The audit details include the campaign key, affected timestamp, and reason. |
| `TIMELINE_REGENERATION_CAMPAIGN_RETRIED` | An admin retries failed users for a campaign. |

## Worker Tuning

Most installations can use the defaults. For large instances, administrators can tune the campaign worker with
environment variables:

| Environment Variable | Default | Description |
|----------------------|---------|-------------|
| `GEOPULSE_TIMELINE_REGENERATION_CAMPAIGN_INTERVAL` | `5m` | How often the backend checks active campaigns and queues due work. |
| `GEOPULSE_TIMELINE_REGENERATION_CAMPAIGN_DELAY` | `2m` | Delay after backend startup before the first campaign worker run. |
| `GEOPULSE_TIMELINE_REGENERATION_CAMPAIGN_MAX_CONCURRENT_TASKS` | `2` | Maximum number of campaign user jobs processed concurrently. |
| `GEOPULSE_TIMELINE_REGENERATION_CAMPAIGN_MAX_ATTEMPTS` | `5` | Maximum automatic attempts before a user work item remains failed. |

These variables require a backend restart. The worker uses the timeline processing executor, so increasing concurrency
can increase CPU, database, and geocoding load.

For the broader timeline processing settings, see
[Timeline Global Configuration](/docs/system-administration/configuration/timeline-global-config).
