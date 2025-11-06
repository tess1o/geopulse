#!/bin/bash

# Check if a new version is provided
if [ -z "$1" ]; then
  echo "Usage: ./update_version.sh <new-version>"
  exit 1
fi

NEW_VERSION=$1
CURRENT_VERSION=$(grep -m1 "<version>" pom.xml | sed -E 's/.*<version>(.*)<\/version>.*/\1/')

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
        sed -i '' "s/$CURRENT_VERSION/$NEW_VERSION/g" "$file"
        echo "Updated $file"
    else
        echo "Warning: $file not found."
    fi
done

echo "Version update complete."
