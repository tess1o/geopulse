export default {
    page: {
        title: "通知",
        description: "查看 GeoPulse 发给你的通知。",
        refresh: "刷新",
        markAllSeen: "全部标为已读"
    },
    filters: {
        unread: "未读",
        all: "全部"
    },
    sourceOptions: {
        all: "全部来源",
        geofence: "地理围栏",
        timeline: "时间线",
        import: "导入",
        export: "导出",
        friends: "好友邀请"
    },
    table: {
        source: "来源",
        notification: "通知",
        time: "时间",
        status: "状态",
        actions: "操作"
    },
    status: {
        seen: "已读",
        unread: "未读"
    },
    newNotification: "新通知",
    markSeen: "标为已读",
    titleFallback: "通知",
    empty: {
        unread: "没有未读通知。",
        none: "暂无通知。"
    },
    toast: {
        errorSummary: "通知出错",
        loadFailed: "加载通知失败",
        markSeenFailed: "无法将通知标为已读",
        markAllSeenFailed: "无法将所有通知标为已读"
    },
    store: {
        loadUnreadCountFailed: "无法加载未读通知数",
        loadPreferencesFailed: "无法加载通知偏好",
        savePreferencesFailed: "无法保存通知偏好",
        loadReleaseAnnouncementFailed: "无法加载版本公告",
        unreadToastSummary: "未读通知",
        unreadToastDetail: "你有 {count} 条未读通知。",
        viewAllNotifications: "查看全部通知",
        browserBlockedSummary: "浏览器通知已被阻止",
        browserBlockedDetail: "请在浏览器设置中允许通知，以接收桌面提醒。",
        browserNotSentSummary: "浏览器通知未发送",
        browserNotSentDetail: "浏览器尚未授予通知权限。请从铃铛菜单重新启用浏览器提醒。",
        browserFailedSummary: "浏览器通知发送失败",
        browserFailedDetail: "浏览器或操作系统阻止了桌面提醒。应用内通知仍可正常接收。"
    },
    bell: {
        openAriaLabel: "打开通知列表",
        unreadTag: "{count} 条未读",
        browserAlerts: "浏览器提醒",
        browserNotSupported: "当前浏览器不支持通知。",
        noUnread: "没有未读通知。",
        noNotifications: "没有找到通知。",
        defaultMessage: "有一条新通知。",
        markSeen: "标为已读",
        markAllSeen: "全部标为已读",
        errorSummary: "通知出错"
    },
    sourceNotificationSuffix: "{source}通知",
    display: {
        sources: {
            geofence: "地理围栏",
            timeline: "时间线",
            import: "导入",
            export: "导出",
            friendInvite: "好友",
            weather: "天气",
            backupHealth: "备份状态",
            gpsHealth: "GPS 状态",
            product: "产品动态",
            rewind: "回顾"
        },
        types: {
            geofenceEnter: "进入地理围栏",
            geofenceLeave: "离开地理围栏",
            timelineRegenerationRequired: "已安排刷新时间线",
            importCompleted: "导入完成",
            importFailed: "导入失败",
            exportCompleted: "导出完成",
            exportFailed: "导出失败",
            friendInviteReceived: "好友邀请",
            friendInviteAccepted: "好友邀请已接受",
            weatherQuotaReached: "天气查询额度已用尽",
            weatherQuotaRestored: "天气查询额度已恢复",
            gpsHealthIncidentOpened: "暂未收到 GPS 数据",
            gpsHealthIncidentResolved: "GPS 数据接收已恢复",
            productReleaseAvailable: "新功能",
            rewindReady: "回顾已就绪"
        },
        actions: {
            openGeofenceEvents: "查看地理围栏事件",
            viewTimelineStatus: "查看时间线状态",
            openImports: "查看导入",
            openExports: "查看导出",
            openInvitations: "查看邀请",
            openFriends: "查看好友",
            openAdminDashboard: "打开管理概览",
            openBackupSettings: "打开备份设置",
            openNotifications: "查看通知",
            openRewind: "打开回顾",
            openNotification: "查看通知"
        }
    }
}
