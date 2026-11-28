export default {
    tripDetails: {
        dialogTitleDefault: "行程详情",
        dialogTitleWithType: "行程详情 · {type}",
        sections: {
            route: "行程路线",
            timing: "时间",
            tripInfo: "行程详情",
            routeInfo: "路线",
            coordinates: "坐标"
        },
        labels: {
            start: "开始：",
            end: "结束：",
            duration: "时长：",
            distance: "距离：",
            averageSpeed: "平均速度：",
            routePoints: "路线定位点：",
            routePointsValue: "{count} 个定位点",
            origin: "起点：",
            destination: "终点："
        },
        unknownOrigin: "起点未知",
        unknownDestination: "终点未知",
        notAvailable: "无数据",
        close: "关闭",
        toasts: {
            copiedSummary: "已复制",
            copiedDetail: "坐标已复制到剪贴板",
            copyFailedSummary: "复制失败",
            copyFailedDetail: "无法复制坐标",
            gpsUnavailableSummary: "GPS 数据不可用",
            gpsUnavailableDetail: "无法加载此行程的 GPS 定位点"
        }
    },
    staySplit: {
        header: "插入停留以拆分行程",
        loadingPath: "正在加载行程轨迹…",
        clickToSetStay: "点击路线以设置停留地点。",
        stayStartLabel: "停留开始",
        stayEndLabel: "停留结束",
        placeLabel: "你在哪里停留？",
        selectedOnRoute: "路线上的定位点",
        updatingPreview: "正在更新拆分结果…",
        preview: {
            originalTrip: "原行程",
            splitResult: "拆分结果",
            tripFor: "行程持续 {duration}",
            to: "前往 {name}",
            stayAt: "在 {name} 停留",
            forDuration: "持续 {duration}",
            from: "从 {name} 出发"
        },
        saveSplit: "保存拆分结果",
        unknownTimeRange: "时间范围未知",
        selectedStayFallback: "所选停留",
        selectedPlaceFallback: "所选地点",
        validation: {
            tripMissing: "找不到行程。",
            readOnly: "时间线编辑已停用。",
            selectStayLocation: "在行程地图上选择停留地点。",
            selectValidTimes: "请选择有效的开始和结束时间。",
            endAfterStart: "停留结束时间必须晚于开始时间。",
            minDuration: "停留时长必须至少为 60 秒。",
            startAfterTripStart: "停留必须在行程开始后开始。",
            endBeforeTripEnd: "停留必须在行程结束前结束。",
            selectPlace: "请选择停留地点，或为新地点命名。",
            selectValidStayLocation: "请选择有效的停留位置。"
        },
        pathErrors: {
            noPoints: "此行程没有可用的轨迹定位点。",
            loadFailed: "无法加载行程轨迹"
        },
        previewErrorFallback: "无法预览行程拆分结果",
        toasts: {
            splitFailedSummary: "拆分失败",
            splitFailedFallback: "无法拆分行程"
        },
        distanceFromClickMeters: "距点击位置 {value} 米",
        distanceFromClickKm: "距点击位置 {value} 公里"
    },
    mapMatchingDetails: {
        header: "地图匹配详情",
        statusLabels: {
            completed: "已匹配",
            queued: "已排队",
            processing: "匹配中",
            failed: "失败",
            skipped: "已跳过"
        },
        neverQueuedNote: "此行程在进入地图匹配队列之前已被跳过，因此没有可用于重试的匹配记录。",
        detailCaption: "由路线规划引擎提供",
        lastAttemptedOn: "上次尝试于 {when}",
        refinedOn: "优化于 {when}",
        openSettings: "打开地图匹配设置",
        adminHint: "管理员可在「管理 → 设置 → 地图匹配」中重新运行地图匹配。",
        close: "关闭"
    },
    movementTypeQuickEdit: {
        header: "编辑出行方式",
        unknownAlgorithmWarning: "算法无法识别此出行方式，请手动设置。",
        readOnlyWarning: "演示模式下无法编辑出行方式。",
        selectPlaceholder: "选择出行方式",
        save: "保存",
        reset: "重置",
        close: "关闭",
        unknownTime: "时间未知",
        toasts: {
            updatedSummary: "出行方式已更新",
            updatedDetail: "此行程的出行方式已设为{type}",
            updateFailedSummary: "更新失败",
            updateFailedFallback: "无法更新出行方式",
            resetSummary: "出行方式已重置",
            resetDetail: "此行程的出行方式已重置为{type}",
            resetFailedSummary: "重置失败",
            resetFailedFallback: "无法重置出行方式",
            noCorrectionToReset: "没有可撤销的行程类型修正。请在修正历史中更改此活动。"
        }
    },
    gpsPointEdit: {
        header: "编辑 GPS 定位点",
        mapInstructions: "点击地图，为此 GPS 定位点选择新位置。",
        locationLabel: "位置",
        latitudeLabel: "纬度",
        longitudeLabel: "经度",
        speedLabel: "速度（公里/小时）",
        speedPlaceholder: "输入速度（公里/小时）",
        accuracyLabel: "精度（米）",
        accuracyPlaceholder: "输入精度（米）",
        stageChange: "添加更改",
        revertToOriginal: "恢复原始值",
        stagingHint: "更改暂未保存。请在 GPS 数据页面选择「应用更改」，一并保存。",
        validation: {
            latitudeRequired: "请输入纬度",
            latitudeRange: "纬度必须在 -90 到 90 之间",
            longitudeRequired: "请输入经度",
            longitudeRange: "经度必须在 -180 到 180 之间"
        },
        mapPopups: {
            currentLocation: "当前位置，点击地图移动",
            originalLocation: "原始位置"
        }
    }
}
