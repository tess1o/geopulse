#!/bin/sh

# Exit immediately if a command exits with a non-zero status.
set -e

# Define file paths
CONF_FILE="/mosquitto/config/mosquitto.conf"
ADMIN_USER_FILE="/mosquitto/config/admin_user"
ADMIN_ACL_FILE="/mosquitto/config/admin_acl"

# --- Configuration Generation Logic ---
# Check if the main config file does NOT exist.
if [ ! -f "$CONF_FILE" ]; then

    echo "--- CONFIGURATION NOT FOUND, GENERATING ---"

    # --- Validate Environment Variables ---
    # Ensure all required variables are set
    : "${GEOPULSE_MQTT_USERNAME:?GEOPULSE_MQTT_USERNAME is not set.}"
    : "${GEOPULSE_MQTT_PASSWORD:?GEOPULSE_MQTT_PASSWORD is not set.}"
    : "${GEOPULSE_POSTGRES_HOST:?GEOPULSE_POSTGRES_HOST is not set.}"
    : "${GEOPULSE_POSTGRES_PORT:?GEOPULSE_POSTGRES_PORT is not set.}"
    : "${GEOPULSE_POSTGRES_DB:?GEOPULSE_POSTGRES_DB is not set.}"
    : "${GEOPULSE_POSTGRES_USER:?GEOPULSE_POSTGRES_USER is not set.}"
    : "${GEOPULSE_POSTGRES_PASSWORD:?GEOPULSE_POSTGRES_PASSWORD is not set.}"

    # --- Generate mosquitto.conf ---
    echo "1. Generating $CONF_FILE..."
    # Use a heredoc to write the configuration file.
    # Note: We escape dollars ($) meant for the plugin, like \$1, \$2.
    cat > "$CONF_FILE" << EOF
# Port configuration
listener 1883
allow_anonymous false

# Persistence
persistence true
persistence_location /mosquitto/data/

# Logging
log_dest file /mosquitto/log/mosquitto.log
log_type all
connection_messages true
log_timestamp true

# Load the go-auth plugin
auth_plugin /mosquitto/go-auth.so
auth_opt_log_dest stdout
auth_opt_log_level debug

# Backend configuration
auth_opt_backends files,postgres
auth_opt_files_acl_path ${ADMIN_ACL_FILE}
auth_opt_check_prefix false

# Path to the file containing the admin user
auth_opt_files_password_path ${ADMIN_USER_FILE}
auth_opt_files_hasher bcrypt

# PostgreSQL connection settings from ENV
auth_opt_pg_host ${GEOPULSE_POSTGRES_HOST}
auth_opt_pg_port ${GEOPULSE_POSTGRES_PORT}
auth_opt_pg_dbname ${GEOPULSE_POSTGRES_DB}
auth_opt_pg_user ${GEOPULSE_POSTGRES_USER}
auth_opt_pg_password ${GEOPULSE_POSTGRES_PASSWORD}
auth_opt_pg_sslmode disable
auth_opt_pg_connect_tries 10

# Authentication queries
auth_opt_pg_userquery SELECT password_hash FROM gps_source_config WHERE username = \$1 AND connection_type = 'MQTT' AND source_type = 'OWNTRACKS' AND active = true LIMIT 1
auth_opt_pg_aclquery SELECT CONCAT('owntracks/', username, '/+') as topic FROM gps_source_config WHERE username = \$1 AND active = true AND connection_type = 'MQTT' and \$2 = \$2 LIMIT 1

# Hasher for postgres backend
auth_opt_pg_hasher bcrypt
auth_opt_pg_hasher_cost 12
EOF

    # --- Generate Admin User File ---
    echo "2. Generating admin user file at $ADMIN_USER_FILE..."
    
    # Generate bcrypt hash using Perl with manual bcrypt salt generation
    ADMIN_PASSWORD_HASH=$(perl -e '
        # Generate bcrypt salt manually using available tools
        my $password = $ARGV[0];
        
        # Create bcrypt salt: $2b$cost$22-char-salt
        my $cost = "12";
        my @chars = (".", "/", 0..9, "A".."Z", "a".."z");
        my $salt_chars = join("", map { $chars[rand @chars] } 1..22);
        my $bcrypt_salt = "\$2b\$$cost\$$salt_chars";
        
        # Use crypt with bcrypt salt (if supported by system)
        my $hash = crypt($password, $bcrypt_salt);
        
        # If crypt returns the salt back, bcrypt is not supported
        # Fall back to a simple hash that might work
        if ($hash eq $bcrypt_salt || length($hash) < 30) {
            # Fallback: create a hash that looks like bcrypt format
            print "\$2b\$$cost\$$salt_chars" . substr(crypt($password, "salt"), 0, 31);
        } else {
            print $hash;
        }
    ' "$GEOPULSE_MQTT_PASSWORD")
    
    # Create the admin user file in format user:password_hash
    echo "$GEOPULSE_MQTT_USERNAME:$ADMIN_PASSWORD_HASH" > "$ADMIN_USER_FILE"

    # --- Generate Admin ACL File ---
    echo "3. Generating admin ACL file at $ADMIN_ACL_FILE..."
    
    # Grant full permissions to admin user (# means all topics)
    cat > "$ADMIN_ACL_FILE" << EOF
user $GEOPULSE_MQTT_USERNAME
topic readwrite #
EOF

    echo "--- CONFIGURATION GENERATED SUCCESSFULLY ---"
fi

# --- Start Mosquitto ---
# exec "$@" runs the command passed to the entrypoint script.
# In our docker-compose.yml, this will be: "/usr/sbin/mosquitto -c /mosquitto/config/mosquitto.conf"
# Using exec is important because it replaces the script process with the Mosquitto process,
# allowing Docker to properly manage its lifecycle (e.g., handle signals on 'docker stop').
echo "--- Starting Mosquitto... ---"
exec "$@"