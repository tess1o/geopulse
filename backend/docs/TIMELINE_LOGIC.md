Timeline is a set of the following events: stays, trips between stays, gps data gaps. Between each stay we should have 1
or more trips. When there is no GPS data for more than X hours (or minutes) - we calculate it as Data Gap.

We work with complete days only, partial days are not supported and are out of scope.
The timeline for past days (trips, stays, gaps) must be saved to DB.
The timeline for today is calculated LIVE all the time.
Eventually, a daily nightly job will save it to DB.

The logic how timeline should be calculated (identificiation of stays and trips is out of scope):

* The user requests for a timeline from start to end dates
* We check the date range:
    * If `start-end` period is completely in the past - try to get data (stays, trips, gaps) from DB. Each entity has
      start time and duration, so use both fields to get proper data. For example if I have data for Aug 10 - Aug 19 and
      the latest event is data gap on Aug 14th for 5 days. So when I request timeline from Aug 16-Aug 17 we don't have
      events started on this day but we have event that is still in progress from past (start date for gap is Aug 14 + 5
      days duration)
    * If `start` is in the past and `end` is today or in the future - it's a `mixed` logic. We try to get data for full
      past period of time. If available - get it + append live timestamp for today (generate live). If not available -
      generate from start to end, save past data to DB.
    * If both dates are in future - return empty timeline.
* We have logic to expand the dates. What it means - we need to get the latest event (stay/trip/data gap) even if it
  happened outside of requested start/end range. It's required to properly cover overnight stays, so we extend
  previously generated timeline entities (change their duration basically). All types of events can be extended.
* The timeline generation can be requested from different sources:
    * Daily job (runs at night) - processes data from last known event till the end of today's day
    * Timeline request from user: org.github.tess1o.geopulse.timeline.rest.TimelineResource.getMovementTimeline
    * When we edit/delete GPS coordinates, add/delete favorite locations - we need to recalculate timeline since it
      might be affected


