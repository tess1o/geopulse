export default {
    sentInvites: {
        header: "已发送的邀请",
        loading: "正在加载已发送的邀请…",
        sentAgo: "发送于{time}",
        pending: "待处理",
        cancelTooltip: "取消邀请",
        cancelAllPending: "取消所有待处理邀请",
        emptyTitle: "没有待处理的邀请",
        emptyMessage: "发送好友邀请后，在对方接受或拒绝前，邀请会显示在这里。",
        cancelDialog: {
            header: "取消邀请",
            message: "确定要取消发给 {name} 的邀请吗？",
            note: "取消操作无法撤销，你可以稍后重新发送邀请。",
            keep: "保留邀请",
            confirm: "取消邀请"
        }
    },
    receivedInvites: {
        header: "收到的邀请",
        loading: "正在加载收到的邀请…",
        wantsToConnect: "想添加你为好友",
        acceptTooltip: "接受邀请",
        declineTooltip: "拒绝邀请",
        acceptAll: "全部接受",
        declineAll: "全部拒绝",
        emptyTitle: "没有待处理的邀请",
        emptyMessage: "其他用户发送的好友邀请会显示在这里。你可以分享自己的用户名以接收邀请。",
        acceptDialog: {
            header: "接受好友邀请",
            message: "确定要添加 {name} 为好友吗？",
            note: "成为好友后，你们可以在地图上查看对方的位置。",
            confirm: "添加好友"
        },
        rejectDialog: {
            header: "拒绝好友邀请",
            message: "确定要拒绝 {name} 的好友邀请吗？",
            note: "对方不会收到拒绝通知，仍可在之后重新发送邀请。",
            keep: "保留邀请",
            confirm: "拒绝"
        },
        bulkDialog: {
            acceptHeader: "接受全部邀请",
            declineHeader: "拒绝全部邀请",
            message: "确定要{action}全部 {count} 条待处理的好友邀请吗？",
            acceptAction: "接受",
            declineAction: "拒绝",
            acceptNote: "这些用户都会成为你的好友，并可以查看你的位置。",
            declineNote: "所有待处理的邀请都会被拒绝，对方仍可重新发送邀请。"
        },
        relativeTime: {
            daysAgo: "{count} 天前",
            hoursAgo: "{count} 小时前",
            minutesAgo: "{count} 分钟前",
            justNow: "刚刚",
            recently: "最近"
        }
    },
    timelineTab: {
        loading: "正在加载时间线…",
        selectFriends: "选择要查看其时间线的好友",
        noSharedTitle: "暂无已共享的时间线",
        noSharedMessage: "目前没有好友开启时间线共享。你可以请他们在好友设置中开启。",
        loadFailedTitle: "加载时间线失败",
        loadFailedDetail: "无法加载好友的时间线"
    },
    mergedList: {
        header: "时间线记录",
        loading: "正在加载时间线记录…",
        empty: "所选日期范围内没有时间线数据",
        noDataFor: "{duration}内没有数据",
        loadMore: "加载更多",
        showingCount: "已显示 {shown} 条，共 {total} 条"
    },
    locationTab: {
        liveLocation: "实时位置",
        timelineHistory: "历史时间线"
    },
    selection: {
        title: "选择好友",
        youLabel: "（你）"
    },
    invite: {
        header: "邀请好友",
        subtitle: "发送好友邀请",
        nameLabel: "好友姓名",
        submit: "发送邀请"
    },
    list: {
        header: "好友",
        inviteFriend: "邀请好友",
        columns: {
            friendName: "好友姓名",
            lastSeen: "上次在线",
            lastLocation: "最近位置",
            remove: "移除"
        },
        removeTooltip: "移除好友",
        empty: "暂无好友。邀请好友后即可分享位置。",
        removeDialog: {
            header: "移除好友",
            message: "确定要将 {name} 从好友列表移除吗？",
            note: "移除后会停止互相共享位置。如有需要，你可以稍后重新发送邀请。",
            confirm: "移除好友"
        }
    },
    datePicker: {
        rangeLabel: "日期范围",
        placeholder: "选择日期范围",
        presetPlaceholder: "快速选择",
        today: "今天",
        yesterday: "昨天"
    },
    filters: {
        all: "全部",
        none: "无",
        online: "在线",
        onlineNow: "当前在线",
        lastSeenRecently: "最近在线"
    },
    demo: {
        invitationsTooltip: "演示模式下无法发送邀请"
    },
    listTab: {
        empty: {
            title: "暂无好友",
            description: "邀请好友，即可在地图上共享位置。",
            demoDisabled: "演示模式下无法邀请好友。",
            inviteFirst: "邀请好友"
        },
        lastSeenLabel: "上次在线：{text}",
        sharesWithYou: "好友与你共享的内容",
        sharesWithFriend: "你与此好友共享的内容",
        liveLocation: "实时位置",
        timelineHistory: "历史时间线",
        shared: "已共享",
        notShared: "未共享",
        demoPermissionsReadOnly: "演示模式下，共享权限为只读。",
        liveLocationInfo: "允许此好友实时查看你的位置",
        timelineHistoryInfo: "允许此好友查看你完整的位置记录",
        actions: {
            live: "实时位置",
            liveTooltip: "在实时地图上显示好友",
            timeline: "时间线",
            timelineTooltip: "查看好友的历史时间线",
            removeTooltip: "移除好友",
            removeTooltipDemo: "演示模式下无法移除好友"
        },
        status: {
            noLocation: "暂无位置",
            online: "在线",
            recent: "最近在线",
            offline: "离线"
        },
        lastSeenText: {
            never: "从未在线",
            justNow: "刚刚",
            minutesAgo: "{count} 分钟前",
            hoursAgo: "{count} 小时前",
            daysAgo: "{count} 天前"
        },
        permissionDialog: {
            header: "确认更改权限",
            timelineAllow: "是否允许 {name} 查看你完整的位置记录，包括过去的所有停留和行程？",
            timelineRevoke: "确定要撤销 {name} 查看你的位置记录的权限吗？",
            liveAllow: "是否允许 {name} 实时查看你的位置？",
            liveRevoke: "确定要撤销 {name} 查看你的实时位置的权限吗？"
        },
        permissionToast: {
            updatedSummary: "权限已更新",
            timelineGranted: "已允许 {name} 查看你的历史时间线",
            timelineRevoked: "已撤销 {name} 查看你的历史时间线的权限",
            liveGranted: "已允许 {name} 查看你的实时位置",
            liveRevoked: "已撤销 {name} 查看你的实时位置的权限",
            failedSummary: "更新权限失败",
            timelineFailedDetail: "无法更新好友权限",
            liveFailedDetail: "无法更新实时位置权限"
        }
    },
    mapTab: {
        loading: "正在加载好友地图…",
        noFriends: {
            title: "暂无好友可显示",
            description: "添加好友后即可在地图上查看对方的位置",
            demoDisabled: "演示模式下无法邀请好友。",
            invite: "邀请好友"
        },
        noLocation: {
            title: "暂无位置数据",
            description: "好友尚未共享位置。他们可能已关闭位置共享，或尚未使用位置追踪应用。",
            demoDisabled: "演示模式下无法邀请更多好友。",
            refresh: "刷新",
            inviteMore: "邀请更多好友"
        },
        noSelection: {
            title: "尚未选择好友",
            description: "请在筛选器中选择至少一位好友，以在地图上显示位置。",
            showAll: "显示全部好友"
        }
    },
    invitationsTab: {
        demoDisabled: "演示模式下无法处理邀请。",
        received: {
            header: "收到的邀请",
            acceptAll: "全部接受",
            rejectAll: "全部拒绝"
        },
        sent: {
            header: "已发送的邀请",
            cancelAll: "全部取消",
            pending: "待处理"
        },
        actions: {
            accept: "接受",
            reject: "拒绝",
            cancel: "取消"
        },
        empty: {
            title: "没有待处理的邀请",
            description: "你的邀请均已处理"
        }
    },
    page: {
        demoReadOnlyMessage: "演示模式下无法发送邀请、更改权限或移除好友，仍可查看已共享的演示位置。",
        demoChangesDisabled: "演示模式下无法更改好友设置。",
        inviteDialog: {
            header: "邀请好友",
            emailLabel: "好友的邮箱地址或姓名",
            emailPlaceholder: "输入邮箱地址或搜索用户",
            send: "发送邀请"
        },
        tabs: {
            live: "实时位置",
            timeline: "时间线",
            friends: "好友",
            invitations: "邀请"
        },
        validation: {
            emailRequired: "请输入邮箱地址",
            emailInvalid: "请输入有效的邮箱地址"
        },
        toasts: {
            invitationSentSummary: "邀请已发送",
            invitationSentDetail: "已向 {email} 发送好友邀请",
            invitationFailedSummary: "发送邀请失败",
            invitationFailedDetail: "无法发送好友邀请",
            removeFriendConfirm: "确定要将 {name} 从好友列表移除吗？",
            removeFriendHeader: "移除好友",
            removeFriendConfirmLabel: "移除",
            friendRemovedSummary: "好友已移除",
            friendRemovedDetail: "已从好友列表中移除此用户",
            removeFailedSummary: "移除失败",
            removeFailedDetail: "无法移除好友",
            acceptedSummary: "已接受邀请",
            acceptedDetail: "你们已成为好友！",
            acceptFailedSummary: "接受失败",
            acceptFailedDetail: "无法接受邀请",
            rejectedSummary: "已拒绝邀请",
            rejectedDetail: "已拒绝此好友邀请",
            rejectFailedSummary: "拒绝失败",
            rejectFailedDetail: "无法拒绝邀请",
            cancelledSummary: "已取消邀请",
            cancelledDetail: "已取消此好友邀请",
            cancelFailedSummary: "取消失败",
            cancelFailedDetail: "无法取消邀请",
            allAcceptedSummary: "已接受全部邀请",
            allAcceptedDetail: "已接受 {count} 条邀请",
            bulkAcceptFailedSummary: "批量接受失败",
            bulkAcceptFailedDetail: "无法接受全部邀请",
            allRejectedSummary: "已拒绝全部邀请",
            allRejectedDetail: "已拒绝 {count} 条邀请",
            bulkRejectFailedSummary: "批量拒绝失败",
            bulkRejectFailedDetail: "无法拒绝全部邀请",
            allCancelledSummary: "已取消全部邀请",
            allCancelledDetail: "已取消 {count} 条邀请",
            bulkCancelFailedSummary: "批量取消失败",
            bulkCancelFailedDetail: "无法取消全部邀请",
            trailsEnabledSummary: "已显示位置轨迹",
            trailsEnabledDetailWithPoints: "已显示 {range}内的 {trailCount} 条好友轨迹，共 {points} 个位置点",
            trailsEnabledDetailEmpty: "{range}内没有轨迹点",
            trailLoadFailedSummary: "加载轨迹失败",
            trailLoadFailedDetail: "无法加载好友的位置轨迹",
            dataRefreshedSummary: "数据已刷新",
            dataRefreshedDetail: "好友资料和位置已更新",
            refreshFailedSummary: "刷新失败",
            refreshFailedDetail: "无法刷新好友数据",
            loadingFailedSummary: "加载失败",
            loadingFailedDetail: "无法加载页面数据"
        }
    },
    liveFilter: {
        selectPlaceholder: "选择要显示的好友",
        trailDurationLabel: "轨迹时长",
        mobileFilterHeader: "筛选好友",
        doneButton: "完成",
        friendFallbackLabel: "好友",
        noEmailFallback: "未设置邮箱",
        summary: {
            none: "暂无可选好友",
            noneSelected: "尚未选择",
            allSelected: "已选中全部 {count} 位好友",
            partialSelected: "已选 {selected} 位，共 {total} 位"
        },
        mobileButton: {
            allSelected: "筛选好友",
            partial: "筛选（{count}）"
        },
        selectionSummary: {
            none: "选择好友",
            all: "全部好友",
            oneFriend: "1 位好友",
            multiple: "{count} 位好友"
        }
    }
}
