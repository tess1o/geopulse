#!/bin/bash

# Script to delete and recreate a git tag on the latest commit
# Usage: ./retag.sh <tag-name> [commit-message]

# Check if a tag name is provided
if [ -z "$1" ]; then
  echo "Usage: ./retag.sh <tag-name> [commit-message]"
  echo "Example: ./retag.sh v1.8.0"
  echo "Example: ./retag.sh v1.8.0 \"Release 1.8.0\""
  exit 1
fi

TAG_NAME=$1
COMMIT_MESSAGE=${2:-"Release $TAG_NAME"}

echo "========================================"
echo "Re-tagging: $TAG_NAME"
echo "Message: $COMMIT_MESSAGE"
echo "========================================"

# Check if tag exists locally
if git rev-parse "$TAG_NAME" >/dev/null 2>&1; then
    echo "Deleting local tag: $TAG_NAME"
    git tag -d "$TAG_NAME"
else
    echo "Local tag $TAG_NAME does not exist, skipping local deletion"
fi

# Check if tag exists on remote
if git ls-remote --tags origin | grep -q "refs/tags/$TAG_NAME"; then
    echo "Deleting remote tag: $TAG_NAME"
    git push origin ":refs/tags/$TAG_NAME"
    if [ $? -ne 0 ]; then
        echo "Error: Failed to delete remote tag"
        exit 1
    fi
else
    echo "Remote tag $TAG_NAME does not exist, skipping remote deletion"
fi

# Get the latest commit
LATEST_COMMIT=$(git log -1 --oneline)
echo ""
echo "Latest commit: $LATEST_COMMIT"
echo ""

# Create new annotated tag
echo "Creating new tag: $TAG_NAME"
git tag -a "$TAG_NAME" -m "$COMMIT_MESSAGE"
if [ $? -ne 0 ]; then
    echo "Error: Failed to create tag"
    exit 1
fi

# Push the new tag
echo "Pushing tag to origin: $TAG_NAME"
git push origin "$TAG_NAME"
if [ $? -ne 0 ]; then
    echo "Error: Failed to push tag"
    exit 1
fi

echo ""
echo "========================================"
echo "✓ Successfully re-tagged $TAG_NAME"
echo "========================================"
echo ""
git tag -l -n1 "$TAG_NAME"
git log --oneline -1 "$TAG_NAME"
