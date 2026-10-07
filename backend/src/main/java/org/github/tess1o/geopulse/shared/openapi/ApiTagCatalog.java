package org.github.tess1o.geopulse.shared.openapi;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ordered catalog of API documentation groups and their tags. The order here is the order of the generated API
 * reference sidebar; descriptions are rendered as the overview page of each tag.
 */
public final class ApiTagCatalog {

    public record TagDoc(String name, String displayName, String description) {
    }

    public record TagGroup(String name, List<TagDoc> tags) {
    }

    public static final List<TagGroup> GROUPS = List.of(
            new TagGroup("Authentication", List.of(
                    tag(ApiTags.SESSIONS, """
                            Sign in with email and password to get an access token and a refresh token, renew them, \
                            and check which sign-in methods the server offers. Scripts and long-running \
                            integrations should use an API token \
                            instead of signing in with a password; see \
                            [API Tokens](https://geopulse.cc/docs/api/api-tokens)."""),
                    tag(ApiTags.OIDC_SIGN_IN, """
                            Sign in with an OpenID Connect provider and link or unlink provider identities on an \
                            existing account. Login starts with an authorization request that returns the provider \
                            URL; the provider redirects back to GeoPulse, which completes the flow through the \
                            callback endpoint."""),
                    tag(ApiTags.MOBILE_SIGN_IN, """
                            Sign a mobile app in without typing a password on the device. A signed-in user creates a \
                            short-lived one-time code, and the mobile app exchanges that code for its own session."""),
                    tag(ApiTags.REGISTRATION, """
                            Create new accounts, either through open registration (when enabled by the \
                            administrator) or through an invitation link created by an administrator."""),
                    tag(ApiTags.API_TOKENS, """
                            Create and manage personal API tokens. A token acts as the user who created it and is \
                            sent with `X-API-Key: <token>` or `Authorization: Bearer <token>`. The token secret is \
                            returned only once, when the token is created. See \
                            [API Tokens](https://geopulse.cc/docs/api/api-tokens).""")
            )),
            new TagGroup("Location Data", List.of(
                    tag(ApiTags.GPS_TRACKER_INGEST, """
                            Endpoints that GPS tracking apps send location updates to: OwnTracks, Overland, \
                            Traccar, GPSLogger, Colota, Home Assistant, and Dawarich-compatible clients. They do not \
                            use GeoPulse sessions or API tokens; each request authenticates with the credentials of \
                            a GPS source configured under **GPS Sources** (HTTP Basic or a bearer token, depending on \
                            the app). Responses use the format each app expects."""),
                    tag(ApiTags.GPS_POINTS, """
                            Read, correct, export, and delete the raw GPS points that every other feature is built \
                            from. Points arrive from GPS tracker apps, the GeoPulse mobile app, or file imports. \
                            Editing or deleting points updates the affected part of the timeline automatically."""),
                    tag(ApiTags.GPS_SOURCES, """
                            Configure where GPS points come from. A GPS source holds the credentials a tracker app \
                            uses to send data, an optional device filter, and filtering of inaccurate or duplicate \
                            points. Telemetry mappings control how extra fields sent by a tracker, such as battery \
                            level, are labeled and shown."""),
                    tag(ApiTags.IMPORT, """
                            Import location history from GPX, GeoJSON, CSV, OwnTracks, and Google Timeline files, \
                            or from a GeoPulse export. Imports run as background jobs: upload a file, then poll the \
                            import job until it finishes."""),
                    tag(ApiTags.EXPORT, """
                            Export your data. Full exports run as background jobs: create a job, poll its status, \
                            then download the result. Single trips and stays can be downloaded directly as GPX.""")
            )),
            new TagGroup("Timeline & Places", List.of(
                    tag(ApiTags.TIMELINE, """
                            The timeline is generated from GPS points and consists of **stays** (time spent at one \
                            place), **trips** (movement between stays, with a detected movement type), and **data \
                            gaps** (periods without GPS data). Use these endpoints to read the timeline for a time \
                            range, regenerate it after changing settings, and compare it with friends' timelines. \
                            All timestamps are ISO-8601 instants in UTC."""),
                    tag(ApiTags.TIMELINE_CORRECTIONS, """
                            Manual fixes for the generated timeline: set the movement type of a trip, split a trip \
                            where a stop was missed, or turn a data gap into a stay. Corrections are stored as \
                            overrides and survive timeline regeneration; each can be undone."""),
                    tag(ApiTags.TIMELINE_LABELS, """
                            Label a time range of the timeline, for example a vacation or a business trip. Labels \
                            may overlap; the overlap check lists conflicts before a label is saved. Labels are also \
                            created by OwnTracks tags, and a label can be turned into a trip."""),
                    tag(ApiTags.PLACES, """
                            Details about a single place on the timeline: its name, statistics, and visits. A place \
                            is either a favorite (`type=favorite`) or a reverse-geocoded location \
                            (`type=geocoding`)."""),
                    tag(ApiTags.FAVORITES, """
                            Favorite places and areas you name yourself. Stays inside a favorite use its name \
                            instead of the reverse-geocoded address. Adding, deleting, or moving a favorite starts a \
                            timeline regeneration in the background so existing stays pick up the change."""),
                    tag(ApiTags.GEOCODING, """
                            Manage reverse-geocoding results (address, city, and country resolved for stay \
                            locations). Correct names, re-resolve locations with a different provider, and define \
                            normalization rules that rename cities or countries consistently."""),
                    tag(ApiTags.NOTES, """
                            Notes attached to a time and place on the timeline. Notes are stored in GeoPulse or, \
                            when the Memos integration is enabled, in the user's Memos server.""")
            )),
            new TagGroup("Insights", List.of(
                    tag(ApiTags.STATISTICS, """
                            Movement statistics (distance, time moving, average speed, top places, frequent routes, \
                            and distance charts) for a custom range, the last 7 days, or the last 30 days."""),
                    tag(ApiTags.DIGESTS, """
                            Monthly and yearly summaries ("Rewind") with highlights, milestones, comparisons with \
                            the previous period, heatmaps, and a PDF version."""),
                    tag(ApiTags.JOURNEY_INSIGHTS, """
                            Long-term insights across all data: countries and cities visited, distance traveled, \
                            time patterns, weather, and earned achievements."""),
                    tag(ApiTags.LOCATION_ANALYTICS, """
                            Browse and search the countries, cities, and places you have visited, with visit \
                            history and CSV exports for each."""),
                    tag(ApiTags.COVERAGE, """
                            Coverage shows which areas of the map you have explored, as a grid of cells calculated \
                            from GPS points. Coverage must be enabled first; recalculation runs in the background.""")
            )),
            new TagGroup("Trips", List.of(
                    tag(ApiTags.TRIPS, """
                            Trips are named date ranges, such as a vacation, with their own timeline, map path, \
                            summary, and plan. A trip can be linked to a timeline label and shared with friends as \
                            collaborators. Not to be confused with trips on the timeline, which are movements \
                            between stays."""),
                    tag(ApiTags.TRIP_PLANNING, """
                            Plan what to visit on a trip. Plan items are places to visit that GeoPulse matches \
                            against actual stays. Reconstruction fills in missing parts of a trip by generating GPS \
                            points from stays and movements you describe.""")
            )),
            new TagGroup("Friends & Sharing", List.of(
                    tag(ApiTags.FRIENDS, """
                            Invite other users of the same GeoPulse instance as friends and choose what each friend \
                            can see: live location, timeline, or both. Friend data is only returned when the \
                            friend has granted the matching permission."""),
                    tag(ApiTags.SHARE_LINKS, """
                            Create links that share your live location or a timeline period with people who do not \
                            have a GeoPulse account. Links can expire and can be protected with a password. The \
                            public side of a link is served by **Shared Link Viewer**."""),
                    tag(ApiTags.SHARED_LINK_VIEWER, """
                            Anonymous endpoints used by the page that opens a share link. They need no GeoPulse \
                            account. Read the link info, get a short-lived access token (sending the password for \
                            protected links), and send it as `Authorization: Bearer <token>` on the other requests. \
                            Live-location links and timeline links expose different endpoints.""")
            )),
            new TagGroup("Alerts", List.of(
                    tag(ApiTags.GEOFENCES, """
                            Geofence rules watch an area and record an event when you or a friend enters or leaves \
                            it. Events can be delivered as notifications through templates (Apprise or in-app)."""),
                    tag(ApiTags.NOTIFICATIONS, """
                            In-app notifications, such as geofence events, finished imports and exports, friend \
                            invitations, GPS health alerts, and release announcements, and the user's notification \
                            preferences.""")
            )),
            new TagGroup("Integrations", List.of(
                    tag(ApiTags.IMMICH, """
                            Connect an Immich photo server to show photos on the timeline and map. GeoPulse \
                            proxies photo thumbnails, previews, and downloads, so Immich does not need to be \
                            reachable from the browser."""),
                    tag(ApiTags.MEMOS, """
                            Connect a Memos server to store timeline notes there. See **Notes** for reading and \
                            writing the notes themselves."""),
                    tag(ApiTags.AI_ASSISTANT, """
                            Configure an OpenAI-compatible AI provider and chat with an assistant that can answer \
                            questions about your timeline data."""),
                    tag(ApiTags.PLACES_TO_VISIT, """
                            Discover points of interest near a location, with thumbnails."""),
                    tag(ApiTags.WEATHER, """
                            Historical weather samples collected for your GPS data, when the weather integration is \
                            enabled.""")
            )),
            new TagGroup("Account & App", List.of(
                    tag(ApiTags.PROFILE, """
                            The signed-in user's profile, avatar, password, and timeline preferences. Timeline \
                            preferences control how stays and trips are detected; changing them usually requires \
                            regenerating the timeline."""),
                    tag(ApiTags.HOME, """
                            Content for the GeoPulse home page. Available without signing in."""),
                    tag(ApiTags.SYSTEM, """
                            Health and version of the GeoPulse server. Available without signing in, so they can be \
                            used by monitoring tools.""")
            )),
            new TagGroup("Administration", List.of(
                    admin(ApiTags.ADMIN_USERS, "Users", """
                            Manage user accounts: list and inspect users, change roles and status, reset \
                            passwords, and delete accounts."""),
                    admin(ApiTags.ADMIN_INVITATIONS, "Invitations", """
                            Create and revoke invitation links that let people register when open registration \
                            is disabled."""),
                    admin(ApiTags.ADMIN_API_TOKENS, "API Tokens", """
                            Review and revoke API tokens of all users. Token secrets cannot be recovered."""),
                    admin(ApiTags.ADMIN_AUDIT_LOGS, "Audit Logs", """
                            Review the audit trail of administrator actions and security-relevant events."""),
                    admin(ApiTags.ADMIN_BACKUPS, "Backups", """
                            Encrypted full database backups (scheduled or on demand, download, and restore), and \
                            export or import of the administrator-configured settings only. A restore is prepared \
                            while GeoPulse keeps running; activating it replaces all data and stops the backend so \
                            it can be restarted. See [Backup and Restore](\
                            https://geopulse.cc/docs/system-administration/maintenance/backup-restore)."""),
                    admin(ApiTags.ADMIN_SYSTEM_SETTINGS, "System Settings", """
                            View and change server-wide settings, such as sign-in and registration, geocoding, \
                            weather, map matching, AI, import, and export. A setting that has not been changed here \
                            uses the value from the environment configuration."""),
                    admin(ApiTags.ADMIN_OIDC_PROVIDERS, "OIDC Providers", """
                            Configure OpenID Connect providers that users can sign in with. Providers can come from \
                            environment variables or be created here; a database copy overrides the environment \
                            configuration until it is reset."""),
                    admin(ApiTags.ADMIN_GEOCODING_PROVIDERS, "Geocoding Providers", """
                            Configure additional reverse-geocoding provider instances, for example a self-hosted \
                            Nominatim or Photon server."""),
                    admin(ApiTags.ADMIN_TIMELINE_REGENERATION, "Timeline Regeneration", """
                            Regenerate timelines for many users at once, for example after changing global timeline \
                            settings. A campaign is previewed, created, and then processed in the background."""),
                    admin(ApiTags.ADMIN_WEATHER, "Weather", """
                            Monitor the weather pipeline and queue weather backfills for existing GPS data."""),
                    admin(ApiTags.ADMIN_DASHBOARD, "Dashboard", """
                            Server-wide metrics for the administrator dashboard.""")
            ))
    );

    private static final Map<String, TagDoc> TAGS_BY_NAME = GROUPS.stream()
            .flatMap(group -> group.tags().stream())
            .collect(Collectors.toMap(TagDoc::name, Function.identity()));

    private ApiTagCatalog() {
    }

    public static Optional<TagDoc> find(String tagName) {
        return Optional.ofNullable(TAGS_BY_NAME.get(tagName));
    }

    private static TagDoc tag(String name, String description) {
        return new TagDoc(name, null, description);
    }

    private static TagDoc admin(String name, String displayName, String description) {
        return new TagDoc(name, displayName, description);
    }
}
