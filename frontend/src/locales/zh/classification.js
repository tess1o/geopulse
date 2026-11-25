export default {
    dialog: {
        header: "行程分类详情",
        loading: "正在加载分类详情…",
        explanationLink: "为什么行程从这里开始和结束？",
        close: "关闭"
    },
    overview: {
        title: "行程概览",
        startTime: "开始时间",
        duration: "时长",
        distance: "距离",
        effectiveClassification: "当前分类",
        automaticClassification: "自动分类",
        classificationSource: "分类来源",
        manualOverrideActive: "此行程采用手动设置的分类。"
    },
    editSection: {
        title: "编辑出行方式",
        unrecognizedWarning: "算法无法识别此出行方式。请在下方手动设置。",
        overrideInfo: "如果分类有误，可手动修改。",
        readOnlyError: "演示模式下无法编辑出行方式。",
        selectPlaceholder: "选择出行方式",
        saveButton: "保存手动设置",
        resetButton: "恢复自动分类"
    },
    stats: {
        title: "GPS 统计",
        avgSpeed: "GPS 平均速度",
        avgSpeedHint: "按每条读数的持续时间加权计算",
        maxSpeed: "GPS 最高速度",
        maxSpeedHint: "至少持续 30 秒的最高速度",
        calculatedAvgSpeed: "计算所得的平均速度",
        calculatedAvgSpeedHint: "用距离除以时长得出",
        speedVariance: "速度方差",
        lowAccuracyPoints: "低精度定位点",
        gpsReliability: "GPS 可靠性",
        reliable: "可靠",
        unreliable: "不可靠",
        reliableHint: "GPS 速度在预期范围内，用于分类",
        unreliableHint: "GPS 速度不可靠，使用距离除以时长所得的速度"
    },
    priority: {
        title: "分类优先顺序",
        description: "按从左到右的优先顺序判断行程类型，找到匹配类型后即停止。",
        learnMore: "了解更多"
    },
    steps: {
        title: "分类分析",
        description: "自动分类的各项阈值检查结果。"
    },
    finalDecisionLabel: "最终判定：",
    na: "无数据",
    toasts: {
        loadFailedFallback: "无法加载分类详情",
        loadFailedDetail: "无法加载行程的分类详情",
        movementUpdatedSummary: "出行方式已更新",
        movementUpdatedDetail: "行程已标记为{type}",
        updateFailedSummary: "更新失败",
        updateFailedFallback: "无法更新出行方式",
        movementResetSummary: "出行方式已重置",
        movementResetDetail: "行程分类已重置为{type}",
        resetFailedSummary: "重置失败",
        resetFailedFallback: "无法重置出行方式"
    },
    stepCard: {
        notEnabled: "未启用",
        passed: "通过",
        failed: "未通过",
        thresholdChecksHeader: "阈值检查：",
        actual: "（实际值：{value}）"
    }
}
