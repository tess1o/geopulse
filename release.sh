#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if a new version is provided
if [ -z "$1" ]; then
  echo -e "${RED}❌ Error: No version specified${NC}"
  echo "Usage: ./release.sh <version>"
  echo "Example: ./release.sh 1.4.1"
  exit 1
fi

NEW_VERSION=$1

# Validate version format (x.y.z)
if ! [[ $NEW_VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo -e "${RED}❌ Error: Invalid version format${NC}"
  echo "Version must be in format: x.y.z (e.g., 1.4.1)"
  exit 1
fi

CURRENT_VERSION=$(grep -m1 "<version>" pom.xml | sed -E 's/.*<version>(.*)<\/version>.*/\1/')

echo -e "${GREEN}🚀 Starting release process${NC}"
echo -e "${YELLOW}Current version: $CURRENT_VERSION${NC}"
echo -e "${YELLOW}New version: $NEW_VERSION${NC}"
echo ""

# Check for uncommitted changes
if [[ -n $(git status -s) ]]; then
  echo -e "${RED}❌ Error: You have uncommitted changes${NC}"
  echo "Please commit or stash your changes before releasing"
  git status -s
  exit 1
fi

# Check if we're on main branch (optional safety check)
CURRENT_BRANCH=$(git branch --show-current)
if [[ "$CURRENT_BRANCH" != "main" && "$CURRENT_BRANCH" != "master" ]]; then
  echo -e "${YELLOW}⚠️  Warning: You are not on main/master branch (current: $CURRENT_BRANCH)${NC}"
  read -p "Do you want to continue? (y/N) " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${RED}Release cancelled${NC}"
    exit 1
  fi
fi

# Step 1: Update version
echo -e "${GREEN}📝 Step 1/4: Updating version files...${NC}"
./update_version.sh "$NEW_VERSION"
echo ""

# Step 2: Generate OpenAPI specs
echo -e "${GREEN}📘 Step 2/4: Generating OpenAPI specifications...${NC}"
make openapi
echo ""

# Step 3: Commit changes
echo -e "${GREEN}💾 Step 3/4: Committing changes...${NC}"
git add -A
git commit -m "Release $NEW_VERSION

- Updated version to $NEW_VERSION
- Generated OpenAPI specifications

🤖 Generated with automated release script"
echo ""

# Step 4: Create and push tag
echo -e "${GREEN}🏷️  Step 4/4: Creating and pushing tag v$NEW_VERSION...${NC}"
git tag "v$NEW_VERSION"
git push origin "$CURRENT_BRANCH"
git push origin "v$NEW_VERSION"
echo ""

echo -e "${GREEN}✅ Release $NEW_VERSION completed successfully!${NC}"
echo ""
echo -e "${YELLOW}📋 Next steps:${NC}"
echo "1. ⏳ Wait for GitHub Actions to build and publish images"
echo "2. 🔗 Go to: https://github.com/tess1o/geopulse/releases/new"
echo "3. 📝 Create release with tag v$NEW_VERSION and add changelog"
echo ""
echo -e "${GREEN}GitHub Actions will automatically:${NC}"
echo "  - Build multi-arch Docker images"
echo "  - Push to Docker Hub and GHCR"
echo "  - Publish Helm charts to GitHub Pages"
