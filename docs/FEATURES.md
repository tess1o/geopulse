# GeoPulse Features

This document provides a comprehensive overview of all features available in GeoPulse.

## 📱 GPS Data Sources & Integration

### Supported Tracking Apps
- **OwnTracks** - Full bidirectional synchronization
  - Username/password authentication
  - Real-time location updates
  - Offline data sync when reconnected
  - End-to-end encryption support
  
- **Overland** - iOS location tracking
  - Token-based authentication
  - Battery-efficient background tracking
  - Automatic data batching and upload

### Data Management
- **Import/Export** - Full OwnTracks format compatibility
- **Data Validation** - Automatic GPS data cleaning and validation
- **Batch Processing** - Handle large datasets efficiently
- **Real-time Sync** - Live updates from connected tracking apps

## 🗺️ Timeline & Location Analysis

### Smart Timeline Generation
- **Automatic Categorization** - GPS data intelligently sorted into stays and trips
- **Stay Detection** - Identifies meaningful location stops based on:
  - Minimum duration thresholds
  - Distance clustering algorithms
  - User-configurable sensitivity settings
- **Trip Analysis** - Calculates:
  - Total distance traveled
  - Duration and average speed
  - Route visualization on maps

### Timeline Preferences
- **Customizable Algorithms** - Adjust sensitivity for:
  - Minimum stay duration (default: 10 minutes)
  - Location clustering distance (default: 100 meters)
  - Trip detection thresholds
- **Real-time Updates** - Changes apply immediately to timeline processing
- **Reset Options** - Restore default settings anytime

### Interactive Timeline View
- **Date Range Selection** - View any time period from hours to months
- **Detailed Information** - Each timeline item shows:
  - Start and end times
  - Duration
  - Location details (when available)
  - Distance traveled (for trips)
- **Map Synchronization** - Click timeline items to highlight on map

## 🗺️ Interactive Mapping

### Map Features
- **Multiple Base Maps** - Choose from various map styles
- **Layer System** - Toggle different data layers:
  - GPS tracks and routes
  - Timeline stays and trips
  - Favorite locations
  - Friend locations
  - Current location (live tracking)

### Map Interactions
- **Route Visualization** - Complete GPS tracks with:
  - Color-coded paths
  - Start/end markers
  - Intermediate waypoints
- **Zoom Controls** - Navigate from global view to street level
- **Marker System** - Different icons for:
  - Stay locations
  - Trip routes
  - Favorite places
  - Friend locations

### Real-time Features
- **Live Location** - See current position when viewing today's data
- **Auto-refresh** - Periodic updates for current day
- **Friend Tracking** - Real-time friend location updates

## 📊 Analytics & Statistics

### Dashboard Overview
- **Multi-period Statistics** - Compare data across:
  - Selected date range
  - Last 7 days
  - Last 30 days
- **Key Metrics Cards**:
  - Total distance traveled
  - Number of places visited
  - Trip count and averages
  - Most visited locations

### Journey Insights
- **Geographic Exploration**:
  - Countries visited with flag displays
  - Cities explored with visit counts
  - Visual geographic coverage maps
- **Achievement System**:
  - Activity streaks (5 levels: Inactive → Champion)
  - Activity levels (4 tiers: Low → Extreme)
  - Distance milestones and goals
- **Pattern Analysis**:
  - Daily/weekly movement patterns
  - Peak activity times
  - Route preferences

### Charts & Visualizations
- **Distance Charts** - Visual representation of travel patterns
- **Activity Graphs** - Movement trends over time
- **Top Places** - Most visited locations with statistics

## 👥 Social Features

### Friend System
- **Friend Management**:
  - Send friend invitations by email
  - Accept/reject incoming invitations
  - Manage existing friendships
- **Location Sharing**:
  - Real-time location sharing with friends
  - Privacy controls for what to share
  - Toggle sharing on/off per friend
- **Friends Map**:
  - See friends' current locations
  - View friends' shared areas
  - Interactive friend markers

### Privacy Controls
- **Granular Permissions** - Control exactly what friends can see
- **Temporary Sharing** - Set time limits on location sharing
- **Selective Sharing** - Choose which data to share with each friend

## 🔗 Public Sharing

### Share Links
- **Temporary Access** - Create time-limited sharing links
- **Password Protection** - Optional password protection for sensitive shares
- **Public Access** - Share with non-registered users
- **Link Management**:
  - View all active share links
  - Edit expiration dates
  - Revoke access anytime
  - Set maximum number of active links

### Share Options
- **Flexible Duration** - Set custom expiration times
- **Data Selection** - Choose what data to include in shares
- **Access Control** - Monitor who accesses shared links

## ⭐ Favorites & Places

### Favorite Locations
- **Point Favorites** - Save specific locations with custom names
- **Area Favorites** - Define rectangular areas of interest
- **Map Integration** - Add favorites directly from map interface
- **Search & Filter** - Find saved favorites quickly

### Management Features
- **Edit Favorites** - Update names and descriptions
- **Delete Management** - Remove unwanted favorites
- **Visual Markers** - See favorites displayed on all maps

## 📥 Data Export & Import

### Export Options
- **Multiple Formats**:
  - OwnTracks format (JSON)
  - Standard GPS formats
  - GeoPulse native format
- **Data Selection**:
  - GPS tracks
  - Timeline data
  - Favorites
  - Statistics
- **Date Range Export** - Export specific time periods
- **Batch Processing** - Handle large datasets efficiently

### Import Features
- **OwnTracks Import** - Direct import from OwnTracks exports
- **Data Validation** - Automatic data cleaning during import
- **Progress Tracking** - Monitor import job status
- **Conflict Resolution** - Handle duplicate data intelligently

### Job Management
- **Background Processing** - Large exports/imports run in background
- **Status Monitoring** - Track job progress and completion
- **File Download** - Secure download of completed exports
- **History** - View past export/import operations

## ⚙️ Customization & Settings

### User Profile
- **Profile Management**:
  - Update personal information
  - Change password
  - Avatar selection (20 predefined options)
- **Account Settings**:
  - Email preferences
  - Privacy settings
  - Data retention options

### Theme & Interface
- **Dark/Light Mode** - Complete theme switching
- **System Integration** - Respect system theme preferences
- **Mobile Optimization** - Responsive design for all devices
- **Accessibility** - WCAG compliant with keyboard navigation

### GPS Source Configuration
- **Multiple Sources** - Connect multiple tracking apps
- **Source Management**:
  - Add new GPS sources
  - Edit existing configurations
  - Enable/disable sources
  - Test connections
- **Authentication Setup**:
  - Username/password for OwnTracks
  - Token-based for Overland
  - Secure credential storage

## 🔧 Technical Features

### Performance
- **Efficient Data Processing** - Handle millions of GPS points
- **Smart Caching** - Optimize repeated queries
- **Background Jobs** - Non-blocking data processing
- **Real-time Updates** - Live data synchronization

### Security
- **Dual Authentication** - Cookie and localStorage modes
- **CSRF Protection** - Comprehensive security against attacks
- **Secure Headers** - Proper security header implementation
- **Data Encryption** - Secure data transmission and storage

### User Experience
- **Progressive Loading** - Skeleton states and loading indicators
- **Error Handling** - User-friendly error messages
- **Offline Support** - Graceful handling of connection issues
- **Touch Optimization** - Mobile-friendly interactions

### Development Features
- **API Documentation** - Comprehensive REST API
- **Docker Deployment** - Container-based deployment
- **Database Migrations** - Automatic schema updates
- **Health Monitoring** - Built-in status endpoints

## 🚀 Upcoming Features

### Planned Enhancements
- **Mobile App** - Native React Native application
- **Advanced Analytics** - Machine learning insights
- **GPX Support** - Import/export GPX files
- **Webhook System** - API webhooks for integrations
- **Multi-user Organizations** - Team and family accounts
- **Advanced Permissions** - Fine-grained sharing controls

---

For technical implementation details, see the [API Documentation](API.md) and [Development Guide](DEVELOPMENT.md).