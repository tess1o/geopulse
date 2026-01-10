# Period Tags: Organizing Your Timeline

Period Tags allow you to organize your timeline by tagging specific date ranges with custom labels. Whether it's a vacation, business trip, or any significant period in your life, Period Tags help you categorize and quickly navigate through your location history.

## What are Period Tags?

Period Tags are user-defined labels that you can attach to specific time periods in your timeline. Think of them as bookmarks or chapters in your location history. Each tag has:

- **Tag Name**: A descriptive name (e.g., "Spain Vacation", "Work Trip to NYC")
- **Date Range**: Start and end dates for the period
- **Color**: A visual identifier to quickly spot tagged periods on your timeline

Period Tags can be created manually or automatically through integrations like OwnTracks.

---

## Creating Period Tags

### Manual Creation

To create a new Period Tag:

1. Navigate to the **Period Tags Management** page (accessible from the main navigation)
2. Click the **"Create Period Tag"** button
3. Fill in the required information:
   - **Tag Name** (required): Give your period a descriptive name
   - **Date Range** (required): Select the start and end dates
   - **Color**: Choose or randomize a color for visual identification

4. Click **"Create"** to save the tag

:::tip Random Colors
Each new tag automatically gets a random color from a carefully selected palette. You can click the "Random" button to get a different color, or manually choose one using the color picker.
:::

### Overlap Detection

When creating a Period Tag, GeoPulse automatically checks for overlapping periods. If your new tag overlaps with an existing one, you'll see a confirmation dialog showing which tags overlap. You can choose to:

- **Create Anyway**: Proceed with creating the overlapping tag
- **Cancel**: Return to the form to adjust the dates

This helps you avoid accidentally creating duplicate or conflicting period tags.

---

## Managing Period Tags

### Viewing Your Tags

The Period Tags Management page displays all your tags in a comprehensive table (desktop) or cards (mobile) showing:

- Tag name and active status
- Source (Manual or OwnTracks)
- Color indicator
- Start and end dates
- Duration
- Available actions

### Filtering and Searching

Find specific tags quickly using the filter options:

- **Search**: Type to search by tag name
- **Source Filter**: Show only Manual or OwnTracks tags

### Editing Tags

To edit an existing tag:

1. Click the **Edit** icon (pencil) next to the tag
2. Modify the desired fields
3. Click **"Update"** to save changes

:::warning OwnTracks Active Tags
Active tags created by OwnTracks cannot be edited or deleted while they're still active. You can only edit or delete them after they've been completed (when you change tags in OwnTracks).
:::

### Deleting Tags

To delete a tag:

1. Click the **Delete** icon (trash) next to the tag
2. Confirm the deletion in the dialog

You can also bulk delete multiple tags:

1. Select tags using the checkboxes
2. Click the **"Delete (X)"** button that appears
3. Confirm the deletion

:::warning Active Tags Protection
You cannot delete active OwnTracks tags. The bulk delete operation will warn you if you've selected any active tags and prevent the deletion.
:::

### Viewing Timeline for a Tag

To quickly view the timeline for a specific period:

1. Click the **Calendar** icon next to the tag
2. You'll be redirected to the Timeline page with the date range automatically set to the tag's period

This is perfect for reviewing your activities during a specific vacation or trip.

---

## Color Coding

Each tag has a color that appears:
- In the Period Tags management table or cards
- On the timeline when viewing that period
- In any visualizations or reports

The color palette includes 20 carefully selected colors that are:
- Visually distinct from each other
- Accessible and readable
- Aesthetically pleasing

You can:
- Use similar colors for related periods (e.g., all vacations in blue tones)
- Choose distinctive colors for important periods
- Keep the default random colors for variety

---

## OwnTracks Integration

### Automatic Tag Creation

If you use OwnTracks as a GPS source, you can create Period Tags directly from your mobile device:

1. Open OwnTracks app on your phone
2. Go to Share -> Set tag
3. Set a tag (e.g., "Vacation Mode")

GeoPulse will automatically:
- Create a new Period Tag when you set a tag in OwnTracks
- Mark the tag as "Active" (no end date yet)
- Set the source to "OwnTracks"
- Automatically end the tag when you change or clear it in OwnTracks

### Active Tags

OwnTracks tags that are currently active:
- Display with an **"Active"** badge
- Show "Since [date]" instead of an end date
- Cannot be edited or deleted manually (to maintain sync with OwnTracks)
- Automatically update when you change tags in OwnTracks

When you change or clear the tag in OwnTracks:
- The current tag automatically gets an end date
- A new tag starts if you set a different one
- Completed tags can then be edited or deleted manually

### Benefits of OwnTracks Integration

- **No manual entry**: Tag periods automatically as they happen
- **Real-time updates**: Changes in OwnTracks immediately reflect in GeoPulse
- **Mobile convenience**: Create tags on-the-go from your phone
- **Automatic completion**: Tags end automatically when you're done

:::tip Learn More
For detailed information about setting up OwnTracks integration, see the **[OwnTracks GPS Source Guide](/docs/user-guide/gps-sources/owntracks)**.
:::

---

## Using Period Tags in Your Timeline

### Timeline Integration

When viewing your timeline with an active Period Tag:

1. Navigate to the Timeline page
2. If a Period Tag covers the selected date range, you'll see:
   - A visual indicator showing the tagged period
   - The tag name
   - Easy access to tag details

### Quick Navigation

Period Tags make it easy to jump to specific periods in your timeline:

1. From the Period Tags page, click the calendar icon
2. The timeline automatically loads with the tag's date range
3. You can immediately see all activities during that period

This is especially useful for:
- Reviewing vacation memories
- Analyzing work trips
- Comparing different time periods
- Sharing specific periods with others

---

## Best Practices

### Naming Conventions

Create clear, descriptive tag names that include context:
- ✅ "Summer Vacation 2026 - Italy"
- ✅ "Client Visit - Tokyo Office"
- ✅ "Annual Conference - San Francisco"
- ✅ "Work Trip: Boston"
- ✅ "Family Reunion - Miami"
- ❌ "Trip"
- ❌ "Vacation"
- ❌ "Meeting"

**Why descriptive names matter:**
- You'll have many tags over time - specific names help you find them quickly
- Tag names appear in search results and timeline views
- Clear names make it easier to share periods with others

**Suggested naming patterns:**
- For vacations: `"Vacation: [Destination] [Year]"` (e.g., "Vacation: Spain 2026")
- For work trips: `"Work Trip: [City/Client]"` (e.g., "Work Trip: NYC - Acme Corp")
- For events: `"[Event Type]: [Name]"` (e.g., "Conference: DevCon 2026")

### Color Usage

Make your tags visually distinctive:
- **Group related trips**: Use similar color tones for the same type of activity
  - Blue tones for vacations
  - Green tones for work trips
  - Orange tones for events
- **Highlight important periods**: Use bright, distinctive colors for significant trips
- **Keep it simple**: Don't overthink it - the random colors work great for most cases

### Regular Maintenance

Periodically review your tags:
- Delete duplicate or unnecessary tags
- Update tags with more descriptive names
- Merge overlapping tags that represent the same period
- Archive or document significant periods

---

## Statistics and Insights

The Period Tags page header shows helpful statistics:

- **Total Periods**: Number of tagged periods
- **Days Tagged**: Total number of days covered by all tags

These stats help you understand how well you're using Period Tags to organize your timeline.

---

## Common Use Cases

### Vacation Tracking

Tag your vacations to:
- Quickly review trip highlights
- Compare different vacation destinations
- Share vacation timelines with friends
- Build a history of your travels

### Work Travel Management

Track business trips to:
- Monitor travel frequency
- Analyze time spent in different locations
- Generate expense reports
- Review client visits

### Event Documentation

Tag significant events like:
- Conferences and seminars
- Family gatherings
- Sporting events
- Festivals and concerts

### Project Time Tracking

Use tags for time-based projects:
- Field research periods
- Construction projects
- Temporary assignments
- Volunteer work

---

## Tips and Tricks

### Bulk Organization

When organizing historical data:
1. Start with major periods (vacations, relocations)
2. Add work-related tags next
3. Fill in smaller events as needed
4. Use filters to review and refine

### Timeline Sharing

Combine Period Tags with Public Links:
1. Create a tag for the period you want to share
2. Use the tag's date range to set up a public link
3. Share memorable trips with friends and family

### Search Integration

Use the search bar to find specific tags:
- Search by tag name
- Search by location names mentioned in the tag name
- Filter by source (Manual or OwnTracks)

### Mobile-Friendly

On mobile devices, tags are displayed as cards instead of a table:
- Each card shows all tag information in an easy-to-read format
- Tap actions are larger for easier interaction
- Checkboxes allow bulk selection on mobile

---

## Troubleshooting

### Tag Not Appearing on Timeline

If your Period Tag isn't showing on the timeline:
- Verify the date range matches your timeline view
- Check that the tag was successfully created
- Refresh the timeline page

### OwnTracks Tag Not Syncing

If OwnTracks tags aren't appearing:
- Verify OwnTracks integration is properly configured
- Check your OwnTracks app settings
- Ensure the OwnTracks service is running
- See the OwnTracks integration documentation

### Overlapping Tags

If you have overlapping tags:
- This is allowed and can be intentional (e.g., a conference during a vacation)
- Use descriptive tag names to distinguish different aspects of the same period
- Review and merge if they represent the same period

### Can't Edit or Delete a Tag

If you can't edit or delete a tag:
- Check if it's an active OwnTracks tag (can't edit or delete while active)
- Wait for the tag to complete in OwnTracks (change or clear the tag in the app)
- Once completed, you can edit or delete the tag

---

## Related Features

Period Tags work well with other GeoPulse features:

- **[Timeline](/docs/user-guide/core-features/timeline)**: View tagged periods in context
- **[Dashboard](/docs/user-guide/core-features/dashboard)**: See statistics for tagged periods
- **[Public Links](/docs/user-guide/social-and-sharing/public-links)**: Share specific tagged periods
- **[OwnTracks Integration](/docs/user-guide/gps-sources/owntracks)**: Automatic tag creation
