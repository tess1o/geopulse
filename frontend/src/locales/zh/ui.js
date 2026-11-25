export default {
    mainApp: {
        shareLabel: "分享",
        shareAria: "分享时间线",
        reconstructTooltip: "在时间线的空白时段手动添加停留或行程。",
        reconstructLabel: "补充缺失活动…",
        reconstructAria: "补充缺失的时间线数据",
        timelineReportsTab: "时间线报告"
    },
    dashboard: {
        page: {
            title: "数据概览",
            subtitle: "查看你的位置数据和分析结果",
            selectedPeriodSummary: "所选时段概览",
            sevenDaysOverview: "最近 7 天概览",
            thirtyDaysOverview: "最近 30 天概览",
            topPlacesSelected: "所选时段的常去地点",
            topPlacesLast7: "最近 7 天的常去地点",
            topPlacesLast30: "最近 30 天的常去地点",
            routeStatsSelected: "所选时段的路线统计",
            routeStatsLast7: "最近 7 天的路线统计",
            routeStatsLast30: "最近 30 天的路线统计",
            noDataTitle: "暂无数据",
            noDataMessage: "所选时段内没有位置数据。请检查 GPS 位置来源或选择其他日期范围。",
            toasts: {
                selectedRangeFailed: "无法加载所选时段的统计数据",
                weeklyFailed: "无法加载最近 7 天的统计数据",
                monthlyFailed: "无法加载最近 30 天的统计数据"
            }
        },
        metrics: {
            totalDistance: "总距离",
            timeMoving: "移动时长",
            dailyAverage: "日均距离",
            averageSpeed: "平均速度",
            mostActiveDay: "最活跃的一天",
            uniqueLocations: "不同地点数",
            notAvailable: "暂无"
        },
        distanceActivityChart: "出行距离",
        distanceAxisTitle: "距离（{unit}）",
        tooltip: {
            distance: "距离：{value}",
            travelTime: "出行时长：{value}",
            locations: "地点：{value}"
        },
        routeAnalysis: {
            noDataTitle: "暂无路线数据",
            noDataMessage: "此时段内没有已分析的路线。",
            uniqueRoutes: "不同路线数",
            mostCommonRoute: "最常走的路线",
            tripsCount: "{count} 次行程",
            avgTripDuration: "平均行程时长",
            longestTripDuration: "耗时最长的行程",
            longestTripDistance: "距离最远的行程",
            notAvailable: "暂无"
        },
        topPlaces: {
            noDataTitle: "暂无地点数据",
            noDataMessage: "此时段内没有到访任何地点。",
            visitsCount: "到访 {count} 次",
            totalDuration: "共 {duration}",
            defaultLocationName: "所选位置"
        }
    },
    home: {
        loadContentFailed: "无法加载首页内容",
        nav: {
            resourcesAriaLabel: "项目资源",
            documentation: "文档",
            github: "GitHub",
            whatsNewAriaLabel: "新功能",
            updateAvailable: "新版本 {version} 已发布",
            updateAvailableShort: "新版 {version}",
            whatsNewTitle: "{version} 版的新功能",
            whatsNewEmpty: "此版本尚无发布说明。",
            releaseNotesLink: "在 GitHub 上查看完整发布说明"
        },
        hero: {
            eyebrow: "可自行托管的 Google 时间线替代方案",
            titleWelcomeBack: "欢迎回来",
            titleDefault: "自己掌控位置时间线",
            subtitle: "GeoPulse 将原始 GPS 点整理成私有的位置时间线，提供停留、行程、地图和出行分析，数据由你掌控。",
            accessDisabled: "管理员已关闭登录和注册。",
            registrationDisabled: "注册已关闭，已有账户的用户仍可登录。",
            tryDemo: "体验演示",
            startJourney: "开始使用",
            goToTimeline: "前往时间线",
            loadingWorkspace: "正在加载工作区…",
            socialProofText: "源码可在 GitHub 查看 · 个人使用免费",
            stars: "星标",
            forks: "分叉"
        },
        features: {
            liveTracking: "实时追踪",
            smartImport: "智能导入",
            autoTimeline: "自动生成时间线",
            deepInsights: "深度分析",
            immichIntegration: "Immich 集成",
            friends: "好友",
            geofences: "地理围栏",
            ai: "AI"
        },
        panel: {
            exploreAriaLabel: "探索 GeoPulse 功能和使用提示",
            tabsAriaLabel: "移动端内容选项卡",
            featuresTab: "功能",
            tipTab: "提示",
            tipOfDayTitle: "每日提示",
            tipOfDayAriaLabel: "每日提示"
        },
        footer: {
            copyright: "© GeoPulse。保留所有权利。"
        }
    },
    mobileAuth: {
        title: "移动端",
        preparing: "正在准备移动端身份验证…",
        payloadMissing: "未收到移动端身份验证数据。",
        opening: "正在打开应用…",
        timedOut: "打开应用超时，请返回应用后重试。",
        failed: "无法将身份验证结果传回移动应用。"
    },
    notFound: {
        subtitle: "位置未找到",
        title: "找不到这个页面",
        description: "你访问的页面不存在，或已移到其他位置。",
        optionsTitle: "你想去哪里？",
        options: {
            home: {
                title: "首页",
                description: "返回 GeoPulse 首页"
            },
            timeline: {
                title: "时间线",
                description: "查看你的位置时间线"
            },
            signIn: {
                title: "登录",
                description: "登录你的 GeoPulse 账户"
            },
            dashboard: {
                title: "数据概览",
                description: "查看你的位置分析结果"
            },
            signUp: {
                title: "注册",
                description: "创建新账户"
            }
        },
        goBack: "返回",
        helpSummary: "还在找你需要的页面？",
        helpIntro: "试试以下常用页面",
        helpLinks: {
            timeline: "时间线",
            timelineDesc: "查看你的位置记录",
            dashboard: "数据概览",
            dashboardDesc: "查看位置数据与分析结果",
            journeyInsights: "出行洞察",
            journeyInsightsDesc: "了解你的出行规律",
            friends: "好友",
            friendsDesc: "管理好友关系",
            signIn: "登录",
            signInDesc: "登录你的账户"
        },
        helpNote: "如果你认为链接已失效，请联系支持人员。"
    },
    darkModeSwitcher: {
        themeTooltip: "主题：{label}",
        themeAriaLabel: "主题模式：{label}",
        light: "浅色",
        dark: "深色",
        system: "跟随系统",
        currentSuffix: "{label}（当前）"
    },
    errorReferenceToast: {
        copyAriaLabel: "复制错误参考 ID"
    },
    providerIcon: {
        defaultAlt: "提供方图标"
    },
    gpsFiltering: {
        title: "GPS 数据过滤",
        filterLabel: "过滤不准确的数据点",
        filterHint: "启用后，定位误差或速度超过设定上限的 GPS 点将被过滤。",
        maxAccuracyLabel: "最大允许定位误差（米）",
        maxAccuracyPlaceholder: "例如 100",
        maxAccuracyHint: "定位误差超过此值的点将被拒绝。",
        maxSpeedLabel: "最大允许速度（公里/小时）",
        maxSpeedPlaceholder: "例如 250",
        maxSpeedHint: "速度超过此值的点将被拒绝。",
        duplicateDetectionTitle: "重复点检测",
        duplicateDetectionLabel: "启用重复点检测",
        duplicateDetectionHint: "跳过同一时间窗口内位置相同的 GPS 点，适用于静止时反复上报相同位置的设备。",
        thresholdLabel: "时间阈值（分钟）",
        thresholdPlaceholder: "例如 2",
        thresholdHint: "同一时间窗口内，相距约 11 米以内的重复点将被跳过。留空则使用全局默认值。"
    },
    pullToRefresh: {
        pullText: "下拉刷新",
        readyText: "松开刷新",
        refreshingText: "正在刷新…"
    },
    tipOfDayCard: {
        nextTip: "下一条提示",
        noTipTitle: "暂无提示",
        noTipDescription: "首页内容可用时，提示会显示在这里。"
    },
    dateRangePicker: {
        labelDefault: "选择日期",
        placeholderDefault: "选择日期范围",
        presetPlaceholderDefault: "选择预设",
        presets: {
            today: "今天",
            yesterday: "昨天",
            last7Days: "最近 7 天",
            last30Days: "最近 30 天"
        },
        maxRangeError: "日期范围最多为 {days} 天",
        periodFallbackName: "时段",
        labelFallbackName: "标签",
        timelineLabelsGroup: "时间线标签",
        presetsGroup: "预设"
    },
    transportTypeCard: {
        enableAriaLabel: "启用{title}检测",
        alwaysActive: "始终启用",
        expandAriaLabel: "展开{title}",
        collapseAriaLabel: "收起{title}",
        disabledMessage: "{title}检测已关闭。启用后即可配置阈值。"
    },
    appLayout: {
        viewTimelineJob: "查看时间线任务",
        whatsNew: "新功能",
        readFullReleaseNotes: "阅读完整发布说明",
        gotIt: "知道了",
        notificationDefaultSummary: "通知",
        viewAllNotifications: "查看全部通知",
        openNotification: "查看通知"
    },
    exploreFeatures: {
        ariaLabel: "探索 GeoPulse",
        kicker: "探索 GeoPulse",
        tabsAriaLabel: "GeoPulse 功能",
        readDocs: "查看文档",
        openInApp: "在应用中打开",
        liveTracking: {
            tabLabel: "实时追踪",
            title: "接入实时位置来源",
            description: "将 OwnTracks、Overland、Traccar、GPSLogger、Dawarich、Home Assistant 和 Colota 的位置记录汇总到同一条时间线。",
            highlights: [
                "可通过 HTTP 和 MQTT 接收支持的追踪器数据",
                "为每个来源单独设置 GPS 过滤规则，提高时间线的准确性",
                "将多个设备和来源的记录合并到一起"
            ]
        },
        imports: {
            tabLabel: "导入",
            title: "导入和迁移历史记录",
            description: "导入旧数据和备份，在后台处理数据并重建时间线。",
            highlights: [
                "支持 GeoPulse、OwnTracks、Google 时间线、GPX、GeoJSON 和 CSV",
                "按日期范围导入部分数据",
                "重新导入时可选择替换或合并数据"
            ]
        },
        timeline: {
            tabLabel: "时间线",
            title: "停留、行程和数据缺口",
            description: "GeoPulse 将原始 GPS 点整理为时间线事件，你可以调整路线并补充遗漏的停留。",
            highlights: [
                "自动检测停留、行程和数据缺口",
                "可配置步行、骑行、摩托车、火车、飞机和船舶等出行方式",
                "将遗漏的停留补记为「行程 → 停留 → 行程」，重建时间线时会保留修正",
                "可选用 Valhalla 地图匹配将轨迹贴合道路，以原始 GPS 记录为准"
            ]
        },
        insight: {
            tabLabel: "洞察",
            title: "数据概览、出行洞察和位置分析",
            description: "按不同时间范围查看出行规律、地点、路线和成就。",
            highlights: [
                "查看所选时段、最近 7 天及最近 30 天的概览",
                "常去地点、路线分析和活动明细",
                "在出行洞察中查看到访的国家、城市和旅行里程碑"
            ]
        },
        friends: {
            tabLabel: "好友",
            title: "好友与分享权限",
            description: "添加好友并决定向他们分享哪些内容。",
            highlights: [
                "发送、接受、拒绝和取消好友邀请",
                "分别设置实时位置和历史时间线的分享权限",
                "查看实时地图和分享的时间线，分享的位置可嵌入其他页面"
            ]
        },
        geofences: {
            tabLabel: "地理围栏",
            title: "地理围栏规则与事件",
            description: "设置进入和离开围栏的规则，接收应用内通知，也可将通知发送至外部渠道。",
            highlights: [
                "为选定的追踪对象配置规则条件",
                "使用支持宏的模板生成通知",
                "在事件选项卡筛选未读事件并标为已读"
            ]
        },
        immich: {
            tabLabel: "Immich",
            title: "Immich 照片图层",
            description: "在时间线地图上直接查看所选日期范围内的 Immich 照片。",
            highlights: [
                "在个人设置中填写 URL 和 API 密钥",
                "切换时间线地图上的照片图层",
                "在应用内查看照片并下载原图"
            ]
        },
        weather: {
            tabLabel: "天气",
            title: "时间线上的天气",
            description: "为位置记录中的地点和行程补充当地天气。",
            highlights: [
                "在停留、行程和时间线地图上查看气温、降水、风况和天气",
                "在出行洞察中比较天气规律，并获得天气相关徽章",
                "默认使用 Open-Meteo 持续收集天气数据，管理员可选择补录历史数据"
            ]
        },
        ai: {
            tabLabel: "AI",
            title: "AI 助手与 MCP",
            description: "用自然语言查询出行记录，或连接第三方 AI 客户端。",
            highlights: [
                "可使用自己的 OpenAI 兼容 API 密钥和模型，用户设置单独加密",
                "查询停留、行程、地点和出行规律",
                "默认启用 MCP，通过 API 令牌验证后可调用只读 AI 工具"
            ]
        }
    },
    onboardingTour: {
        welcomeTitle: "🎉 欢迎使用 GeoPulse！",
        welcomeDescription: "准备好记录出行轨迹了吗？先来配置你的第一个位置来源。",
        startTour: "🚀 开始引导",
        exploreOnMyOwn: "自行探索",
        closeTour: "关闭引导",
        stepCounter: "第 {current} 步，共 {total} 步",
        previous: "上一步",
        next: "下一步",
        getStarted: "开始使用",
        steps: {
            welcome: {
                title: "欢迎使用 GeoPulse！",
                description: "先配置位置来源，然后就可以记录你的行程。"
            },
            addSource: {
                title: "添加第一个位置来源",
                description: "点击「添加来源」，选择 OwnTracks 或 Overland 作为位置来源。"
            },
            followInstructions: {
                title: "按说明完成配置",
                description: "添加来源后，你将看到详细的配置说明，以及专用的接口地址和令牌。"
            },
            allSet: {
                title: "准备就绪！",
                description: "配置好位置来源后，前往时间线查看位置记录，并探索 GeoPulse 的其他功能。"
            }
        }
    },
    appNavbar: {
        demoBadge: "演示",
        locationSharingEnabledTooltip: "位置分享已开启",
        locationSharingDisabledTooltip: "位置分享已关闭",
        shareLocation: "分享位置",
        inviteFriend: "邀请好友",
        inviteFriendDisabledAriaLabel: "演示模式下无法邀请好友",
        inviteFriendDisabledTooltip: "演示模式下无法发送邀请"
    },
    globalSearch: {
        bar: {
            placeholder: "搜索地点、页面、设置…",
            groupPages: "页面",
            groupSettings: "设置",
            groupLocations: "地点",
            visitsCount: "到访 {count} 次",
            sinceDate: "从 {date} 起"
        },
        settingsTrigger: {
            defaultPlaceholder: "搜索设置…",
            defaultButtonLabel: "查找设置"
        },
        registry: {
            tabAuthentication: "身份验证",
            tabGeocoding: "反向地理编码",
            tabWeather: "天气",
            tabPlaceDiscovery: "地点发现",
            tabMapMatching: "地图匹配",
            tabAiAssistant: "AI 助手",
            tabImport: "导入",
            tabExport: "导出",
            tabBackup: "备份与恢复",
            tabNotifications: "通知",
            tabSystem: "系统",
            timelineTabStayPoints: "停留点检测",
            timelineTabTrips: "行程分类",
            timelineTabGpsGaps: "GPS 数据缺口检测",
            timelineTabMerging: "停留点合并",
            subtitleTimelinePreferences: "时间线偏好 / {tab}",
            subtitleProfile: "个人设置 / {subtitle}",
            subtitleSystemSettings: "系统设置 / {tab}",
            subtitlePage: "页面",
            subtitleAdministration: "系统管理"
        }
    },
    charts: {
        barChart: {
            defaultTitle: "数据",
            seriesFallback: "系列 {number}",
            valueKm: "{value} 公里"
        }
    }
}
