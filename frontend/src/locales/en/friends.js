/**
 * Friends: live map, timeline, invitations, and management tables.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    sentInvites: {
        header: 'Sent Invites',
        loading: 'Loading sent invites...',
        sentAgo: 'Sent {time}',
        pending: 'Pending',
        cancelTooltip: 'Cancel invitation',
        cancelAllPending: 'Cancel All Pending',
        emptyTitle: 'No pending invites',
        emptyMessage: "When you send friend requests, they'll appear here until accepted or declined.",
        cancelDialog: {
            header: 'Cancel Invitation',
            message: 'Cancel invitation to {name}?',
            note: 'This action cannot be undone. You can send a new invitation later.',
            keep: 'Keep Invitation',
            confirm: 'Cancel Invitation'
        }
    },
    receivedInvites: {
        header: 'Received Invites',
        loading: 'Loading received invites...',
        wantsToConnect: 'Wants to connect with you',
        acceptTooltip: 'Accept invitation',
        declineTooltip: 'Decline invitation',
        acceptAll: 'Accept All',
        declineAll: 'Decline All',
        emptyTitle: 'No pending invites',
        emptyMessage: 'Friend requests from other users will appear here. Share your username to receive invitations!',
        acceptDialog: {
            header: 'Accept Friend Request',
            message: 'Add {name} as a friend?',
            note: "You'll be able to see each other's locations on the map.",
            confirm: 'Add Friend'
        },
        rejectDialog: {
            header: 'Decline Friend Request',
            message: 'Decline friend request from {name}?',
            note: "This person won't be notified, but they can send another request later.",
            keep: 'Keep Request',
            confirm: 'Decline'
        },
        bulkDialog: {
            acceptHeader: 'Accept All Requests',
            declineHeader: 'Decline All Requests',
            message: '{action} all {count} pending friend requests?',
            acceptAction: 'Accept',
            declineAction: 'Decline',
            acceptNote: 'All these users will become your friends and see your location.',
            declineNote: 'All pending requests will be declined. Users can send new requests later.'
        },
        relativeTime: {
            daysAgo: '{count} day ago | {count} days ago',
            hoursAgo: '{count} hour ago | {count} hours ago',
            minutesAgo: '{count} minute ago | {count} minutes ago',
            justNow: 'just now',
            recently: 'recently'
        }
    },
    timelineTab: {
        loading: 'Loading timelines...',
        selectFriends: 'Select friends to view their timelines',
        noSharedTitle: 'No Shared Timelines',
        noSharedMessage: 'None of your friends have enabled timeline sharing yet. Ask them to enable it in Friends settings!',
        loadFailedTitle: 'Failed to Load Timelines',
        loadFailedDetail: 'Could not load friend timelines'
    },
    mergedList: {
        header: 'Timeline Items',
        loading: 'Loading timeline items...',
        empty: 'No timeline data for selected date range',
        noDataFor: 'No data for {duration}',
        loadMore: 'Load More',
        showingCount: 'Showing {shown} of {total} items'
    },
    locationTab: {
        liveLocation: 'Live Location',
        timelineHistory: 'Timeline History'
    },
    selection: {
        title: 'Select Friends',
        youLabel: '(You)'
    },
    invite: {
        header: 'Invite a friend',
        subtitle: 'Send a friendship request',
        nameLabel: 'Friend name',
        submit: 'Invite!'
    },
    list: {
        header: 'Friends',
        inviteFriend: 'Invite Friend',
        columns: {
            friendName: 'Friend Name',
            lastSeen: 'Last seen',
            lastLocation: 'Last location',
            remove: 'Remove'
        },
        removeTooltip: 'Remove friend',
        empty: 'No friends yet. Invite your first friend to start sharing locations!',
        removeDialog: {
            header: 'Remove Friend',
            message: 'Remove {name} from your friends?',
            note: "You'll no longer see each other's locations. You can send a new friend request later if needed.",
            confirm: 'Remove Friend'
        }
    },
    datePicker: {
        rangeLabel: 'Range:',
        placeholder: 'Select date range',
        presetPlaceholder: 'Quick Presets',
        today: 'Today',
        yesterday: 'Yesterday'
    },
    filters: {
        all: 'All',
        none: 'None',
        online: 'Online',
        onlineNow: 'Online now',
        lastSeenRecently: 'Last seen recently'
    },
    demo: {
        invitationsTooltip: 'Invitations are disabled in demo mode'
    },
    listTab: {
        empty: {
            title: 'No Friends Yet',
            description: 'Start building your network by inviting friends to connect and share locations',
            demoDisabled: 'Inviting friends is disabled in demo mode.',
            inviteFirst: 'Invite Your First Friend'
        },
        lastSeenLabel: 'Last seen: {text}',
        sharesWithYou: 'What this friend shares with you:',
        sharesWithFriend: 'What you share with this friend:',
        liveLocation: 'Live Location',
        timelineHistory: 'Timeline History',
        shared: 'Shared',
        notShared: 'Not Shared',
        demoPermissionsReadOnly: 'Sharing permissions are read-only in demo mode.',
        liveLocationInfo: 'Allows this friend to see your current location in real-time',
        timelineHistoryInfo: 'Allows this friend to view your complete location history',
        actions: {
            live: 'Live',
            liveTooltip: 'Show friend on live map',
            timeline: 'Timeline',
            timelineTooltip: "View friend's timeline history",
            removeTooltip: 'Remove friend',
            removeTooltipDemo: 'Removing friends is disabled in demo mode'
        },
        status: {
            noLocation: 'No Location',
            online: 'Online',
            recent: 'Recent',
            offline: 'Offline'
        },
        lastSeenText: {
            never: 'Never',
            justNow: 'Just now',
            minutesAgo: '{count}m ago',
            hoursAgo: '{count}h ago',
            daysAgo: '{count}d ago'
        },
        permissionDialog: {
            header: 'Confirm Permission Change',
            timelineAllow: 'This will allow {name} to view your complete location history (all past stays and trips). Continue?',
            timelineRevoke: "This will revoke {name}'s access to your location history. Continue?",
            liveAllow: 'This will allow {name} to see your current location in real-time. Continue?',
            liveRevoke: "This will revoke {name}'s access to your live location. Continue?"
        },
        permissionToast: {
            updatedSummary: 'Permission Updated',
            timelineGranted: '{name} can now view your timeline history',
            timelineRevoked: '{name} can no longer view your timeline history',
            liveGranted: '{name} can now view your live location',
            liveRevoked: '{name} can no longer view your live location',
            failedSummary: 'Failed to Update Permission',
            timelineFailedDetail: 'Could not update friend permissions',
            liveFailedDetail: 'Could not update live location permission'
        }
    },
    mapTab: {
        loading: 'Loading friends map...',
        noFriends: {
            title: 'No Friends to Show',
            description: 'Add friends to see their locations on the map',
            demoDisabled: 'Inviting friends is disabled in demo mode.',
            invite: 'Invite Friends'
        },
        noLocation: {
            title: 'No Location Data Available',
            description: "Your friends haven't shared their location yet. This could be because they've disabled location sharing or haven't used location tracking apps.",
            demoDisabled: 'Inviting more friends is disabled in demo mode.',
            refresh: 'Refresh',
            inviteMore: 'Invite More Friends'
        },
        noSelection: {
            title: 'No Friends Selected',
            description: 'Choose at least one friend in the filter to show locations on the map.',
            showAll: 'Show All Friends'
        }
    },
    invitationsTab: {
        demoDisabled: 'Invitation actions are disabled in demo mode.',
        received: {
            header: 'Received Invitations',
            acceptAll: 'Accept All',
            rejectAll: 'Reject All'
        },
        sent: {
            header: 'Sent Invitations',
            cancelAll: 'Cancel All',
            pending: 'Pending'
        },
        actions: {
            accept: 'Accept',
            reject: 'Reject',
            cancel: 'Cancel'
        },
        empty: {
            title: 'No Pending Invitations',
            description: 'All your invitations have been processed'
        }
    },
    page: {
        demoReadOnlyMessage: 'Demo mode: friend invitations, permission changes, and friend removal are disabled. Existing shared demo locations remain viewable.',
        demoChangesDisabled: 'Friend changes are disabled in demo mode.',
        inviteDialog: {
            header: 'Invite Friend',
            emailLabel: "Friend's Email Address or Name",
            emailPlaceholder: 'Enter email address or search users',
            send: 'Send Invitation'
        },
        tabs: {
            live: 'Live',
            timeline: 'Timeline',
            friends: 'Friends',
            invitations: 'Invitations'
        },
        validation: {
            emailRequired: 'Email address is required',
            emailInvalid: 'Please enter a valid email address'
        },
        toasts: {
            invitationSentSummary: 'Invitation Sent',
            invitationSentDetail: 'Friend request sent to {email}',
            invitationFailedSummary: 'Invitation Failed',
            invitationFailedDetail: 'Failed to send invitation',
            removeFriendConfirm: 'Are you sure you want to remove {name} from your friends?',
            removeFriendHeader: 'Remove Friend',
            removeFriendConfirmLabel: 'Remove',
            friendRemovedSummary: 'Friend Removed',
            friendRemovedDetail: 'The friend has been removed from your list',
            removeFailedSummary: 'Remove Failed',
            removeFailedDetail: 'Failed to remove friend',
            acceptedSummary: 'Invitation Accepted',
            acceptedDetail: 'You are now friends!',
            acceptFailedSummary: 'Accept Failed',
            acceptFailedDetail: 'Failed to accept invitation',
            rejectedSummary: 'Invitation Rejected',
            rejectedDetail: 'The invitation has been rejected',
            rejectFailedSummary: 'Reject Failed',
            rejectFailedDetail: 'Failed to reject invitation',
            cancelledSummary: 'Invitation Cancelled',
            cancelledDetail: 'The invitation has been cancelled',
            cancelFailedSummary: 'Cancel Failed',
            cancelFailedDetail: 'Failed to cancel invitation',
            allAcceptedSummary: 'All Invitations Accepted',
            allAcceptedDetail: 'Accepted {count} invitation(s)',
            bulkAcceptFailedSummary: 'Bulk Accept Failed',
            bulkAcceptFailedDetail: 'Failed to accept all invitations',
            allRejectedSummary: 'All Invitations Rejected',
            allRejectedDetail: 'Rejected {count} invitation(s)',
            bulkRejectFailedSummary: 'Bulk Reject Failed',
            bulkRejectFailedDetail: 'Failed to reject all invitations',
            allCancelledSummary: 'All Invitations Cancelled',
            allCancelledDetail: 'Cancelled {count} invitation(s)',
            bulkCancelFailedSummary: 'Bulk Cancel Failed',
            bulkCancelFailedDetail: 'Failed to cancel all invitations',
            trailsEnabledSummary: 'Location Trails Enabled',
            trailsEnabledDetailWithPoints: 'Showing {points} points across {trailCount} friend trail(s) for {range}',
            trailsEnabledDetailEmpty: 'No trail points found for {range}',
            trailLoadFailedSummary: 'Trail Loading Failed',
            trailLoadFailedDetail: 'Failed to load friend location trails',
            dataRefreshedSummary: 'Data Refreshed',
            dataRefreshedDetail: 'Friends data and locations have been updated',
            refreshFailedSummary: 'Refresh Failed',
            refreshFailedDetail: 'Failed to refresh friends data',
            loadingFailedSummary: 'Loading Failed',
            loadingFailedDetail: 'Failed to load page data'
        }
    },
    liveFilter: {
        selectPlaceholder: 'Select friends to show',
        trailDurationLabel: 'Trail duration',
        mobileFilterHeader: 'Filter Friends',
        doneButton: 'Done',
        friendFallbackLabel: 'Friend',
        noEmailFallback: 'No email',
        summary: {
            none: 'No friends available',
            noneSelected: 'None selected',
            allSelected: 'All {count} selected',
            partialSelected: '{selected} of {total} selected'
        },
        mobileButton: {
            allSelected: 'Filter Friends',
            partial: 'Filter ({count})'
        },
        selectionSummary: {
            none: 'Select friends',
            all: 'All friends',
            oneFriend: '1 friend',
            multiple: '{count} friends'
        }
    }
}
