# GeoPulse Timeline Architecture

## Executive Summary

The GeoPulse timeline system has been completely reimplemented with a simplified, existence-based caching architecture that eliminates complex version management and endless regeneration loops. This document describes the new streamlined architecture focused on predictable behavior and maintainable code.

## Table of Contents

1. [System Overview](#system-overview)
2. [Core Design Principles](#core-design-principles)
3. [Architecture Components](#architecture-components)
4. [Data Flow Pipeline](#data-flow-pipeline)
5. [Persistence & Caching Strategy](#persistence--caching-strategy)
6. [Event-Driven Processing](#event-driven-processing)
7. [Background Processing](#background-processing)
8. [Performance Characteristics](#performance-characteristics)
9. [Architectural Benefits](#architectural-benefits)
10. [Migration from Legacy System](#migration-from-legacy-system)

## System Overview

The timeline system transforms raw GPS data into structured movement timelines consisting of:
- **Stays**: Stationary periods at specific locations
- **Trips**: Movement between stays with path information

### Key Design Principles

1. **Today = Live**: Current day data is always generated fresh, never cached
2. **Past = Cache or Generate**: Historical data uses simple existence-based caching
3. **Mixed = Combine**: Requests spanning past and today combine cached + live data
4. **No Expansion**: If no data exists for a date range, return empty (no search expansion)
5. **Simple Events**: Favorite changes use hybrid fast/slow path processing

## Core Design Principles

### 1. Predictable Behavior
```java
public MovementTimelineDTO getTimeline(UUID userId, Instant startTime, Instant endTime) {
    LocalDate startDate = startTime.atZone(userTimeZone).toLocalDate();
    LocalDate endDate = endTime.atZone(userTimeZone).toLocalDate();
    LocalDate today = LocalDate.now(userTimeZone);
    
    if (startDate.equals(today) && endDate.equals(today)) {
        // Today: Always live generation
        return timelineGenerationService.generateLive(userId, startTime, endTime);
    } else if (endDate.isBefore(today)) {
        // Past: Check cache, generate if missing GPS data
        return getFromCacheOrGenerate(userId, startTime, endTime);
    } else {
        // Mixed: Combine past (cached) + today (live)
        return combinePastAndToday(userId, startTime, endTime, today);
    }
}
```

### 2. Existence-Based Caching
- **No version hashes**: Simple existence check in database
- **No staleness flags**: Clear cache invalidation on demand
- **No complex comparison**: Either data exists or it doesn't

### 3. No Data Expansion
- If user requests timeline for a date with no GPS data → return empty timeline
- No automatic search expansion to find "at least something"
- Clear, predictable user experience

## Architecture Components

### Service Layer Architecture

```
┌─────────────────────┐    ┌──────────────────────┐    ┌──────────────────────┐
│  TimelineQuery      │    │  TimelineGeneration  │    │  TimelineBackground  │
│  Service            │    │  Service (Live)      │    │  Service             │
│  (Orchestrator)     │    │                      │    │  (Priority Queue)    │
└─────────────────────┘    └──────────────────────┘    └──────────────────────┘
         │                           │                            │
         ▼                           ▼                            ▼
┌─────────────────────┐    ┌──────────────────────┐    ┌──────────────────────┐
│  TimelineCache      │    │  TimelineData        │    │  TimelineEvent       │
│  Service            │    │  Service             │    │  Service             │
│  (Simple Cache)     │    │  (GPS Retrieval)     │    │  (Impact Analysis)   │
└─────────────────────┘    └──────────────────────┘    └──────────────────────┘
```

### Key Services

#### TimelineQueryService
**Role**: Main orchestrator with simple decision logic

**Decision Logic**:
- **Today only** → Live generation
- **Past only** → Cache check → Generate if no GPS data → Return empty if no GPS
- **Mixed range** → Combine cached past + live today

#### TimelineCacheService
**Role**: Simple existence-based cache operations

**Key Methods**:
- `exists(userId, startTime, endTime)`: Check if timeline data exists
- `get(userId, startTime, endTime)`: Retrieve cached timeline (marked as CACHED)
- `save(userId, startTime, endTime, timeline)`: Persist timeline entities
- `delete(userId, dates)`: Remove cached data for specific dates

#### TimelineEventService
**Role**: Hybrid event processing for favorite changes

**Processing Paths**:
- **Fast Path**: Name-only changes update cached timeline entities directly
- **Slow Path**: Structural changes delete cache and queue background regeneration

#### TimelineBackgroundService
**Role**: Priority queue processor for timeline regeneration

**Priority Levels**:
- **HIGH**: Favorite changes (processed immediately)
- **LOW**: Bulk imports (processed in background)

#### FavoriteImpactAnalyzer
**Role**: Determines impact of favorite changes

**Analysis Types**:
- **Name Only**: Simple rename without structural changes
- **Structural**: Changes that affect timeline structure (location, geometry, etc.)

## Data Flow Pipeline

### Today's Timeline (Live Generation)
```
Request → TimelineQueryService → TimelineGenerationService → Live Timeline
   ↓              ↓                        ↓                      ↓
 User API    Check if today            GPS + Processing      Fresh Data
```

### Past Timeline (Cache-First)
```
Request → Cache Check → Exists? → Return Cached
   ↓           ↓           ↓           ↓
 User API   Database   Timeline    CACHED source
               ↓         Missing?
             GPS Check → Generate → Cache → Return
               ↓           ↓          ↓        ↓
           Data Exists  Processing  Store   LIVE source
               ↓
           No GPS → Empty Timeline
```

### Mixed Timeline (Combine)
```
Request → Split Range → Past (Cached) + Today (Live) → Merge → Return
   ↓          ↓              ↓              ↓           ↓        ↓
 User API  Date Logic   Cache Lookup   Generation   Combine   MIXED source
```

## Persistence & Caching Strategy

### Database Schema (Simplified)

#### Timeline Stays
```sql
CREATE TABLE timeline_stays (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    stay_duration INTEGER NOT NULL,
    location_name TEXT,
    location_source TEXT, -- FAVORITE, GEOCODING
    favorite_id BIGINT REFERENCES favorite_locations(id),
    geocoding_id BIGINT REFERENCES reverse_geocoding_location(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_updated TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

#### Timeline Trips
```sql
CREATE TABLE timeline_trips (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    start_latitude DOUBLE PRECISION NOT NULL,
    start_longitude DOUBLE PRECISION NOT NULL,
    end_latitude DOUBLE PRECISION NOT NULL,
    end_longitude DOUBLE PRECISION NOT NULL,
    distance_km DOUBLE PRECISION,
    trip_duration INTEGER NOT NULL,
    movement_type TEXT,
    path GEOMETRY(LineString, 4326),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_updated TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

#### Timeline Regeneration Queue
```sql
CREATE TABLE timeline_regeneration_queue (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    priority INTEGER NOT NULL, -- 1=HIGH, 2=LOW
    status TEXT NOT NULL, -- PENDING, PROCESSING, COMPLETED, FAILED
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    processing_started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    retry_count INTEGER DEFAULT 0,
    error_message TEXT
);
```

### Storage Strategy

- **Existence-Based**: Cache exists or doesn't exist (no version tracking)
- **Referential Integrity**: Foreign keys to users, favorites, and geocoding
- **Spatial Indexing**: PostGIS support for proximity queries
- **Priority Queue**: Background processing with retry logic

## Event-Driven Processing

### Favorite Change Events

#### Name-Only Changes (Fast Path)
```java
@EventHandler
public void onFavoriteRenamed(FavoriteRenamedEvent event) {
    ImpactAnalysis impact = impactAnalyzer.analyzeRename(event);
    
    if (impact.getType() == ImpactType.NAME_ONLY) {
        // Fast path: Direct database update
        handleSimpleNameUpdate(event.getFavoriteId(), event.getNewName());
    } else {
        // Slow path: Cache invalidation + background regeneration
        handleStructuralChange(impact);
    }
}
```

#### Structural Changes (Slow Path)
```java
private void handleStructuralChange(ImpactAnalysis impact) {
    // Delete affected cached timeline data
    timelineCacheService.delete(impact.getUserId(), impact.getAffectedDates());
    
    // Queue high-priority regeneration
    backgroundService.queueHighPriorityRegeneration(
        impact.getUserId(), 
        impact.getAffectedDates()
    );
}
```

### Import Processing

#### TimelineImportHelper
```java
public void triggerTimelineGenerationForImportedGpsData(ImportJob job) {
    Set<LocalDate> datesWithGpsData = analyzeDatesWithGpsData(job);
    
    // Queue low-priority background generation for all affected dates
    backgroundService.queueLowPriorityRegeneration(
        job.getUserId(),
        new ArrayList<>(datesWithGpsData)
    );
}
```

## Background Processing

### Priority Queue System

#### Task Priorities
- **HIGH (1)**: Favorite changes - processed immediately
- **LOW (2)**: Bulk imports - processed in background

#### Processing Flow
```java
@Scheduled(every = "30s")
public void processHighPriorityTasks() {
    List<TimelineRegenerationTask> tasks = taskRepository.findPendingTasks(
        Priority.HIGH, BATCH_SIZE_HIGH
    );
    
    for (TimelineRegenerationTask task : tasks) {
        processRegenerationTask(task);
    }
}

@Scheduled(every = "5m")  
public void processLowPriorityTasks() {
    // Only process if high priority queue is not too busy
    if (getHighPriorityQueueDepth() < MAX_HIGH_PRIORITY_THRESHOLD) {
        processLowPriorityBatch();
    }
}
```

#### Task Processing
```java
private void processRegenerationTask(TimelineRegenerationTask task) {
    try {
        task.setStatus(TaskStatus.PROCESSING);
        task.setProcessingStartedAt(Instant.now());
        
        // Generate fresh timeline
        MovementTimelineDTO timeline = timelineGenerationService.getMovementTimeline(
            task.getUser().getId(),
            task.getStartDate().atStartOfDay(ZoneOffset.UTC).toInstant(),
            task.getEndDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        );
        
        // Cache the result
        timelineCacheService.save(
            task.getUser().getId(),
            task.getStartDate().atStartOfDay(ZoneOffset.UTC).toInstant(),
            task.getEndDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
            timeline
        );
        
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(Instant.now());
        
    } catch (Exception e) {
        handleTaskFailure(task, e);
    }
}
```

## Performance Characteristics

### Response Times
- **Today's Timeline**: 200-800ms (live generation)
- **Cached Past Timeline**: 50-150ms (database lookup)
- **Mixed Timeline**: 250-900ms (combination)
- **Empty Timeline**: 20-50ms (quick GPS check)

### Cache Behavior
- **Cache Hit Rate**: ~95% for past data
- **Cache Miss Scenarios**: 
  - First request after GPS import
  - After favorite changes (structural)
  - After manual cache invalidation

### Memory Usage
- **No Complex Version Calculation**: Eliminated SHA-256 hashing overhead
- **Simplified Entities**: No version fields, staleness flags
- **Efficient Queue**: ~10KB per queued regeneration task

### Database Performance
- **Simple Existence Queries**: `SELECT COUNT(*) FROM timeline_stays WHERE ...`
- **No Complex Joins**: Removed version comparison logic
- **Efficient Cleanup**: Direct DELETE operations instead of flag updates

## Architectural Benefits

### 1. Predictable Behavior
- **Clear Decision Logic**: Today=live, past=cached, mixed=combined
- **No Surprise Expansion**: Empty data returns empty timeline
- **Consistent Response Types**: Clear data source markers (LIVE, CACHED, MIXED)

### 2. Maintainable Code
- **Eliminated Complex Services**: Removed TimelineVersionService, TimelineInvalidationService
- **Simple Cache Logic**: Existence-based instead of version-based
- **Clear Separation**: Fast path vs slow path for events

### 3. Debuggable System
- **No Endless Loops**: Removed complex staleness detection
- **Clear Error Paths**: Simple failure modes
- **Observable Queue**: Background tasks with status tracking

### 4. Performance Improvements
- **Faster Cache Checks**: No version hash calculation
- **Reduced Database Load**: Fewer complex queries
- **Efficient Event Processing**: Hybrid fast/slow path approach

## Migration from Legacy System

### Removed Components
- ❌ **TimelineVersionService**: Complex SHA-256 version hashing
- ❌ **TimelineInvalidationService**: Complex queue system with retry logic  
- ❌ **Complex TimelineQueryService**: Search expansion and version checking
- ❌ **Version Fields**: `timeline_version`, `is_stale` database columns
- ❌ **Staleness Detection**: Boolean flag system

### Added Components
- ✅ **Simple TimelineQueryService**: Clear today/past/mixed logic
- ✅ **TimelineCacheService**: Existence-based cache operations
- ✅ **TimelineEventService**: Hybrid fast/slow path processing
- ✅ **TimelineBackgroundService**: Priority queue processor
- ✅ **FavoriteImpactAnalyzer**: Impact analysis for favorite changes
- ✅ **TimelineImportHelper**: Simplified import processing

### Data Migration
The new system is backwards compatible:
- **Existing timeline data**: Continues to work (version fields ignored)
- **Database schema**: Additive changes only (new priority queue table)
- **API compatibility**: Same REST endpoints, same response formats

## Conclusion

The simplified GeoPulse timeline architecture eliminates the complexity and reliability issues of the previous version-based system while maintaining all functional capabilities. The new existence-based caching approach provides:

- **Predictable behavior** for users and developers
- **Maintainable code** with clear separation of concerns  
- **Reliable performance** without endless regeneration loops
- **Scalable processing** with priority-based background queues

The key architectural insight is that simple existence checks are sufficient for most caching scenarios, and the added complexity of version-based staleness detection created more problems than it solved. The new hybrid event processing (fast path for simple changes, slow path for complex changes) provides optimal performance while maintaining data consistency.