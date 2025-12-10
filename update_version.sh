#!/bin/bash

# Check if a new version is provided
if [ -z "$1" ]; then
  echo "Usage: ./update_version.sh <new-version> [current-version]"
  exit 1
fi

NEW_VERSION=$1

# Allow optional second parameter to specify current version
if [ -z "$2" ]; then
  CURRENT_VERSION=$(grep -m1 "<version>" pom.xml | sed -E 's/.*<version>(.*)<\/version>.*/\1/')
else
  CURRENT_VERSION=$2
fi

echo "Updating version from $CURRENT_VERSION to $NEW_VERSION"

# List of files to update
files_to_update=(
    ".env.example"
    "backend/pom.xml"
    "backend/src/main/resources/application.properties"
    "docs-website/docs/getting-started/deployment/helm.md"
    "docs-website/docs/getting-started/deployment/docker-compose.md"
    "frontend/pom.xml"
    "frontend/nginx.conf"
    "pom.xml"
    "charts/index.yaml"
    "docs/openapi/openapi.json"
    "docs/openapi/openapi.yaml"
    "helm/geopulse/values.yaml"
    "helm/geopulse/Chart.yaml"
    "helm/geopulse/README.md"
)

for file in "${files_to_update[@]}"; do
    if [ -f "$file" ]; then
        case "$file" in
            *.xml)
                # For XML files: only replace version within <version> tags
                sed -i '' "s|<version>$CURRENT_VERSION</version>|<version>$NEW_VERSION</version>|g" "$file"
                ;;
            *.yaml)
                # For YAML files: replace version values but not IP addresses
                # Match patterns like: "version: 1.8.0", "tag: 1.8.0", "appVersion: \"1.8.0\""
                sed -i '' -E \
                    -e "s/(version: )$CURRENT_VERSION/\1$NEW_VERSION/g" \
                    -e "s/(tag: )$CURRENT_VERSION/\1$NEW_VERSION/g" \
                    -e "s/(appVersion: \")$CURRENT_VERSION(\")/\1$NEW_VERSION\2/g" \
                    "$file"
                ;;
            *.md)
                # For Markdown: replace version references but avoid IPs
                # Match patterns like: ":1.8.0", "tag/1.8.0", "/1.8.0/", "`1.8.0`", "| 1.8.0 |"
                sed -i '' -E \
                    -e "s/(:)$CURRENT_VERSION/\1$NEW_VERSION/g" \
                    -e "s|(/)$CURRENT_VERSION(/)|\\1$NEW_VERSION\\2|g" \
                    -e "s|(tag/)$CURRENT_VERSION|\\1$NEW_VERSION|g" \
                    -e "s|(version )$CURRENT_VERSION|\\1$NEW_VERSION|g" \
                    -e "s|(\`)$CURRENT_VERSION|\1$NEW_VERSION|g" \
                    -e "s|$CURRENT_VERSION(\`)|$NEW_VERSION\\1|g" \
                    -e "s/(\\| )$CURRENT_VERSION( \\|)/\\1$NEW_VERSION\\2/g" \
                    -e "s|$CURRENT_VERSION(-native)|$NEW_VERSION\\1|g" \
                    "$file"
                ;;
            .env.example)
                # For .env files: only replace GEOPULSE_VERSION value
                sed -i '' "s|^GEOPULSE_VERSION=$CURRENT_VERSION|GEOPULSE_VERSION=$NEW_VERSION|g" "$file"
                ;;
            *.properties)
                # For properties files: replace application.version property and version strings in values
                sed -i '' -E \
                    -e "s|^application\.version=$CURRENT_VERSION|application.version=$NEW_VERSION|g" \
                    -e "s|(GeoPulse/)$CURRENT_VERSION|\1$NEW_VERSION|g" \
                    -e "s|(info\.version=)$CURRENT_VERSION|\1$NEW_VERSION|g" \
                    "$file"
                ;;
            *.json)
                # For JSON files: replace version in "version": "x.y.z" pattern
                sed -i '' -E "s/(\"version\": \")$CURRENT_VERSION(\")/\1$NEW_VERSION\2/g" "$file"
                ;;
            *.conf)
                # For nginx.conf: replace in max-age patterns and User-Agent headers
                sed -i '' -E \
                    -e "s|(max-age=)$CURRENT_VERSION|\1$NEW_VERSION|g" \
                    -e "s|(GeoPulse/)$CURRENT_VERSION|\1$NEW_VERSION|g" \
                    "$file"
                ;;
            *)
                # Default: use careful replacement avoiding IP addresses
                # Only replace if preceded by common version delimiters
                sed -i '' -E "s/([:=\/\"' ])$CURRENT_VERSION([\"' \/])/\1$NEW_VERSION\2/g" "$file"
                ;;
        esac
        echo "Updated $file"
    else
        echo "Warning: $file not found."
    fi
done

echo "Version update complete."
