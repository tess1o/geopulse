# Timeline Architecture Test Plan

## Overview
Comprehensive test plan for the redesigned timeline architecture. Tests must use real database, real GPS data, and validate actual timeline generation scenarios.

## Test Categories

### 1. TimelineOvernightProcessor Tests (Core Timeline Generation)
**File**: `TimelineOvernightProcessorIntegrationTest.java`

#### 1.1 Single Day Processing Tests
- **Basic Day**: GPS data 9AM-6PM → Should create 1 stay, 1 trip, 1 stay
- **Overnight Stay**: GPS data starts at home 8PM prev day, ends at work 10AM → Should extend home stay to 10AM, create trip
- **No GPS Data**: Empty day → Should create data gap for entire day
- **Sparse GPS Data**: Only 2-3 points → Should create appropriate stays/trips/gaps
- **Dense GPS Data**: Many points throughout day → Should merge and create clean timeline
- **Midnight Boundary**: Stay/trip that crosses midnight → Should handle boundary correctly

#### 1.2 Multi-Day Processing Tests  
- **3-Day Range**: Continuous GPS data → Should create timeline for all days
- **Mixed Data**: Some days with GPS, some without → Should create stays/trips + data gaps
- **Large Range**: 30-day range → Should handle efficiently without memory issues
- **GPS Ends Mid-Range**: GPS data ends on day 2 of 5-day request → Should create data gap for remaining days

#### 1.3 Data Gap Detection Tests
- **Long Gap**: 8-hour gap in GPS data → Should create data gap
- **Short Gap**: 1-hour gap → Should NOT create data gap (below threshold)
- **End-of-Data Gap**: GPS data ends before request period → Should create gap to end of period
- **Configuration Respect**: Different gap thresholds → Should respect user config

#### 1.4 Boundary Expansion Tests
- **Stay Expansion**: Request Aug 15-16, stay started Aug 14 6PM → Should include expanded stay
- **Trip Expansion**: Trip started before range but ends in range → Should include with adjusted duration
- **Data Gap Expansion**: Data gap from previous day → Should expand to show continuity

### 2. TimelineRequestRouter Tests (Request Classification)
**File**: `TimelineRequestRouterTest.java`

#### 2.1 Request Type Classification
- **Past Only**: Aug 1-15 (today is Aug 20) → Should route to PastRequestHandler
- **Mixed**: Aug 15-today (today is Aug 20) → Should route to MixedRequestHandler  
- **Future Only**: Tomorrow → Should return empty timeline
- **Today Only**: Today 00:00-23:59 → Should route to MixedRequestHandler
- **Single Moment**: Same start/end time → Should handle gracefully

#### 2.2 Route Verification
- **Verify Handler Called**: Mock handlers, verify correct one called
- **Parameter Passing**: Ensure exact timestamps passed to handlers
- **Error Handling**: Invalid time ranges → Should handle gracefully

### 3. PastRequestHandler Tests (Past Data Handling)  
**File**: `PastRequestHandlerIntegrationTest.java`

#### 3.1 Cache Hit Scenarios
- **Complete Cache**: All data exists in DB → Should return cached data with enhancements
- **Boundary Expansion**: Cached data + expansion → Should include previous context
- **Data Source**: Should mark as CACHED data source

#### 3.2 Cache Miss Scenarios  
- **No Cache**: Empty DB → Should regenerate from scratch using overnight processor
- **Partial Cache**: Some data exists → Should delete partial data and regenerate  
- **Stale Cache**: Old data exists → Should regenerate with fresh data

#### 3.3 Previous Context Prepending
- **Stay Prepending**: Latest event is stay → Should prepend with adjusted duration
- **Trip Prepending**: Latest event is trip → Should prepend with adjusted duration  
- **Data Gap Prepending**: Latest event is data gap → Should prepend data gap
- **No Previous Context**: No previous events → Should not prepend anything

### 4. MixedRequestHandler Tests (Past + Today Handling)
**File**: `MixedRequestHandlerIntegrationTest.java`

#### 4.1 Timeline Combining
- **Past + Today**: Cached past data + live today data → Should combine correctly
- **Cross-Day Continuity**: Stay/trip spanning midnight → Should merge properly
- **Cross-Day Gaps**: Gap between last past event and first today event → Should detect gap

#### 4.2 Cross-Day Gap Detection
- **Normal Gap**: 8-hour gap between days → Should create cross-day gap
- **No Gap**: Continuous activity → Should not create gap  
- **Large Gap**: 2-day gap → Should create appropriate gap duration
- **Configuration**: Different gap thresholds → Should respect settings

### 5. TimelineAssembler Tests (Complex Assembly Logic)
**File**: `TimelineAssemblerTest.java`

#### 5.1 Previous Context Prepending Logic
- **Stay Extension**: Previous stay should extend to first current activity
- **Trip Extension**: Previous trip should extend to first current activity
- **Data Gap Extension**: Previous data gap should extend to show continuity
- **Multiple Event Types**: Mixed events → Should find truly latest event

#### 5.2 Timeline Combining Logic
- **Event Merging**: Combine two timelines → Should merge all event types correctly
- **Chronological Sorting**: Combined events → Should be sorted by timestamp
- **Cross-Day Gap Detection**: Integration test of gap detection logic

### 6. TimelineEventRetriever Tests (Database Access Layer)
**File**: `TimelineEventRetrieverTest.java`

#### 6.1 Boundary Expansion Queries
- **Stay Expansion**: SQL query should find stays that extend into range
- **Trip Expansion**: SQL query should find trips that extend into range  
- **Data Gap Expansion**: SQL query should find gaps that extend into range
- **No False Positives**: Should not include events that don't actually overlap

#### 6.2 Latest Event Finding
- **Compare End Times**: Should compare event END times, not start times
- **Mixed Event Types**: Should find latest among stays, trips, and data gaps
- **Edge Cases**: Events ending at exact same time → Should handle deterministically

#### 6.3 Data Operations
- **Bulk Delete**: Should delete all timeline data in range efficiently
- **Complete Data Check**: Should correctly determine if timeline data exists
- **Performance**: Large datasets → Should perform efficiently

### 7. End-to-End Integration Tests
**File**: `TimelineGenerationIntegrationTest.java`

#### 7.1 Full Workflow Tests
- **GPS Import → Timeline**: Import GPS data → Should trigger timeline generation → Verify results
- **GPS Edit → Regeneration**: Edit GPS point → Should trigger regeneration → Verify updated timeline  
- **GPS Delete → Regeneration**: Delete GPS points → Should trigger regeneration → Verify gaps
- **Favorite Add → Regeneration**: Add favorite location → Should trigger regeneration → Verify location names
- **Favorite Delete → Regeneration**: Delete favorite → Should revert to geocoding → Verify names

#### 7.2 Real User Scenarios
- **Daily Commuter**: Home→Work→Home pattern → Should create 3 stays, 2 trips
- **Weekend Traveler**: Home→Airport→Destination→Airport→Home → Should create complex trip timeline
- **Work From Home**: Minimal movement → Should create long stays with minimal trips
- **Vacation**: Different timezone, long stays → Should handle timezone correctly

#### 7.3 Performance & Scale Tests  
- **Large Dataset**: 10,000+ GPS points → Should generate timeline efficiently
- **Long Time Range**: 6-month range → Should handle without memory issues
- **Concurrent Requests**: Multiple users → Should handle concurrent timeline generation

#### 7.4 Data Consistency Tests
- **Database State**: After timeline generation → Should have consistent DB state
- **Transaction Integrity**: Failed generation → Should not leave partial data  
- **Idempotency**: Multiple runs → Should produce identical results

### 8. Background Service Integration Tests
**File**: `TimelineBackgroundServiceIntegrationTest.java`

#### 8.1 Queue Processing Tests
- **High Priority Queue**: Favorite changes → Should process immediately
- **Low Priority Queue**: Import data → Should process after high priority
- **Error Handling**: Failed processing → Should retry appropriately  
- **Bulk Processing**: Large date ranges → Should split and process efficiently

#### 8.2 Event-Driven Tests
- **GPS Point Events**: Edit/delete → Should queue regeneration
- **Favorite Events**: Add/delete/rename → Should queue regeneration  
- **Timeline Preferences**: Config change → Should regenerate all data

## Test Data Patterns

### GPS Data Patterns
1. **Home-Work-Home**: Classic commuter pattern
2. **Multi-Stop Trip**: Home→Store→Restaurant→Home  
3. **Overnight Stay**: Stay extending past midnight
4. **Long Distance Travel**: Airport trips, hotels
5. **Sparse Data**: Few GPS points spread over time
6. **Dense Data**: Many GPS points in short time
7. **Data Gaps**: Missing GPS data periods

### Time Patterns
1. **Single Day**: 00:00 to 23:59 same day
2. **Multi-Day**: Several consecutive days
3. **Large Range**: Weeks or months
4. **Boundary Cases**: Midnight crossings
5. **Mixed Ranges**: Past + today combinations

### Configuration Patterns  
1. **Default Config**: Standard gap thresholds
2. **Strict Config**: Low gap thresholds (more gaps)
3. **Lenient Config**: High gap thresholds (fewer gaps)
4. **Edge Configs**: Min/max values

## Test Infrastructure

### Database Setup
- Use `@QuarkusTest` with `PostgisTestResource`
- Create test users with known UUIDs
- Create GPS data with controlled timestamps
- Clean up between tests

### Test Data Builders
- `GpsPointTestDataBuilder`: Create GPS points with patterns
- `TimelineTestDataBuilder`: Create expected timeline data
- `UserTestDataBuilder`: Create test users with specific configs

### Assertions
- `TimelineAssertions`: Custom assertions for timeline validation
- Verify event counts, timestamps, durations
- Verify data persistence in database
- Verify data source flags and metadata

## Success Criteria

### Functional
- ✅ All test scenarios pass
- ✅ Real database operations work
- ✅ Timeline data persists correctly
- ✅ All edge cases handled

### Performance  
- ✅ Timeline generation < 5 seconds for 1000 GPS points
- ✅ Memory usage reasonable for large datasets
- ✅ Database queries efficient

### Coverage
- ✅ >90% code coverage for redesigned services
- ✅ All critical paths tested
- ✅ Error conditions tested
- ✅ Configuration variations tested