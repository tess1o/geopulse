export default {
    progressMessages: {
        initializing: "正在初始化时间线生成任务",
        acquiringLock: "正在锁定时间线",
        cleaningUp: "正在清理旧的时间线数据",
        preparingGpsProcessing: "正在准备处理 GPS 数据",
        noGpsData: "没有需要处理的 GPS 数据",
        readyToProcess: "准备处理 {totalGpsPoints} 个 GPS 定位点",
        processingStateMachine: "正在通过状态机处理 GPS 定位点",
        postProcessingTrips: "正在复核并完善行程检测结果",
        mergingTimeline: "正在合并时间线",
        persistingTimeline: "正在将时间线事件保存到数据库",
        detectingDataGaps: "正在检测数据缺失",
        finalizing: "正在完成时间线生成",
        autoMatchingVisits: "正在自动匹配计划到访地点",
        recalculatingBadges: "正在重新计算成就徽章",
        completed: "时间线生成完成",
        preparingBoatSetup: "正在准备船舶识别",
        boatEvidenceReady: "船舶识别的水域判定数据已就绪",
        regenerationQueued: "强制重新生成时间线的任务已排队",
        startingLocationResolution: "正在开始解析地点",
        allResolvedFromFavorites: "所有地点都已通过收藏解析",
        resolvedFromFavorites: "已通过收藏解析 {favoritesResolved} 个地点",
        resolvedFromCache: "已从缓存解析 {cachedResolved} 个地点（批量查询 {batchResolved} 个，单独查询 {individualResolved} 个）",
        geocodingLocationProgress: "正在进行地理编码，地点 {current}/{total}",
        geocodingComplete: "地理编码完成：收藏 {favoritesResolved} 个，缓存 {cachedResolved} 个，调用外部 API {externalCompleted} 次",
        applyingCorrections: "正在应用修正"
    },
    steps: {
        queued: "等待开始",
        preparing: "正在准备",
        processingPoints: "处理 GPS 定位点",
        correctionWindow: "重新生成修正时段",
        geocoding: "解析停留地点名称",
        postProcessingTrips: "复核行程",
        merging: "合并停留",
        corrections: "应用修正",
        persisting: "保存时间线",
        finalizing: "收尾",
        badges: "重新计算徽章"
    },
    types: {
        FULL_REBUILD: "完整重新生成",
        PARTIAL_REBUILD: "部分重新生成",
        CORRECTION: "修正",
        IMPORT: "导入",
        CAMPAIGN: "批量重建",
        OTHER: "时间线任务"
    },
    listPage: {
        title: "时间线生成任务",
        description: "查看正在运行或最近完成的时间线生成任务。",
        loading: "正在查找运行中的任务…",
        noJob: {
            title: "当前没有时间线任务在运行",
            message: "如果你是从通知打开此页面的，计划刷新可能已经完成。最近完成或失败的任务会显示在下方。",
            hint: "修改时间线设置、更新收藏地点、手动重新生成或 GeoPulse 定期维护，都可能启动时间线任务。",
            viewTimelineButton: "查看时间线"
        },
        error: {
            title: "无法检查正在运行的任务"
        },
        history: {
            title: "任务历史",
            description: "最近的时间线生成任务",
            loading: "正在加载任务历史…",
            empty: "暂无近期时间线任务。",
            duration: "时长：{duration}",
            gpsPoints: "{count} 个 GPS 定位点",
            viewDetails: "查看详情"
        },
        notAvailable: "无数据",
        durationSeconds: "{seconds} 秒",
        durationMinutesSeconds: "{minutes} 分 {seconds} 秒"
    },
    detailsPage: {
        title: "时间线生成进度",
        description: "实时查看时间线重新生成的进度。",
        backButton: "返回时间线",
        loading: "正在加载任务详情…",
        error: {
            title: "无法加载任务详情"
        },
        jobIdLabel: "任务 ID：{jobId}",
        status: {
            loading: "正在加载…",
            queued: "排队等待处理",
            running: "正在处理时间线",
            completed: "已完成",
            failed: "失败"
        },
        details: {
            started: "开始时间：",
            completed: "完成时间：",
            duration: "时长：",
            type: "类型：",
            gpsPoints: "GPS 定位点：",
            produced: "生成结果：",
            producedValue: "{stays} 次停留、{trips} 段行程、{gaps} 处数据缺口",
            corrections: "修正：",
            correctionsValue: "已应用 {applied} 项，未应用 {notApplied} 项",
            memoryLimit: "内存上限：",
            noMemoryLimit: "无限制（堆内存最高 {heap}）",
            cpus: "CPU 核数："
        },
        stepsTitle: "处理步骤",
        stepBadges: {
            slowest: "耗时最长",
            memoryPeak: "内存峰值",
            failed: "在此步骤失败",
            pending: "等待处理"
        },
        stepMemory: {
            label: "内存峰值：",
            heap: "堆内存 {value}",
            process: "进程 {value}",
            container: "容器 {value}"
        },
        stepShare: "占任务耗时的 {percent}%",
        stepShareTiny: "占任务耗时不到 1%",
        durationUnderSeconds: "少于 {seconds} 秒",
        detail: {
            gpsPoints: "GPS 定位点：{loaded} / {total}",
            processingHeader: "正在处理 GPS 定位点：{processed} / {total}",
            remaining: "剩余 {count} 个定位点",
            geocodingHeader: "反向地理编码：{resolved} / {total} 个地点",
            favoritesInstant: "收藏地点（即时）：{count}",
            cachedInDatabase: "数据库缓存：{count}",
            externalApiCalls: "外部 API 调用：{count}",
            pending: "待处理：{count}"
        },
        diagnostics: {
            button: "复制诊断信息",
            title: "任务诊断信息",
            description: "可在寻求支持或反馈问题时提供此任务的步骤、耗时、内存、版本和设置。诊断信息不含坐标、地点名称、笔记或事件时间。",
            copy: "复制",
            copied: "已将诊断信息复制到剪贴板",
            copyFailed: "复制失败。请选择文本后手动复制。",
            loadFailed: "无法加载诊断信息"
        },
        completion: {
            title: "时间线生成完成！",
            message: "已纳入所有最新 GPS 数据，重新生成了你的时间线。"
        },
        durationZero: "0 秒",
        durationSeconds: "{seconds} 秒",
        durationMinutesSeconds: "{minutes} 分 {seconds} 秒",
        durationHoursMinutesSeconds: "{hours} 小时 {minutes} 分 {seconds} 秒"
    },
    regeneration: {
        alreadyRunning: {
            header: "时间线任务正在运行",
            message: "已有一个时间线生成任务正在运行（已完成 {progress}%）。请等待任务完成。",
            viewProgress: "查看进度"
        },
        savedSummary: "已保存",
        queuedDetail: "更改已保存。时间线将在后台更新。"
    }
}
