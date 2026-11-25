export default {
    title: "时间线与地图",
    description: "自定义时间线和地图的显示方式，无需重新生成时间线数据。",
    behavior: {
        heading: "时间线行为",
        description: "选择打开和使用时间线时显示的内容。",
        defaultDateRange: {
            title: "默认日期范围",
            description: "选择时间线、数据概览和时间线报告打开时的日期范围。",
            details: "清除选择将恢复默认设置（今天）。",
            placeholder: "使用默认设置（今天）"
        },
        unknownActivity: {
            title: "显示未知活动",
            description: "在主时间线上标出缺少数据的时段。",
            details: "关闭后，时间线将隐藏数据缺失卡片和没有停留或行程记录的日期。此设置仅影响显示。"
        },
        telemetry: {
            title: "当前位置的遥测数据",
            description: "在地图的当前位置弹窗中显示遥测数据。",
            details: "此设置仅控制弹窗是否显示遥测数据。"
        }
    },
    sources: {
        heading: "地图显示与来源",
        description: "选择地图渲染方式和地图数据来源。",
        renderMode: {
            title: "地图渲染模式",
            description: "选择各地图视图使用的渲染器。",
            details: "切换模式时会保留两个自定义来源的 URL。"
        },
        buildings3d: {
            title: "3D 建筑",
            description: "在兼容的矢量地图上显示建筑轮廓。",
            details: "仅适用于包含建筑高度数据的 MapTiler 样式。地图放大到街道级别时会显示建筑。"
        },
        rasterTiles: {
            title: "自定义栅格瓦片",
            description: "在栅格模式下使用的可选地图来源。",
            details: "URL 模板必须使用 HTTP 或 HTTPS，并包含 {'{z}'}、{'{x}'} 和 {'{y}'}。留空则使用 OpenStreetMap。",
            ariaLabel: "自定义栅格瓦片 URL"
        },
        vectorStyle: {
            title: "自定义矢量样式",
            description: "在矢量模式下使用的可选样式。",
            details: "输入使用 HTTP 或 HTTPS 的样式 JSON 文件 URL。留空则使用 OpenFreeMap。",
            ariaLabel: "自定义矢量样式 URL"
        }
    },
    processing: {
        heading: "地图数据处理",
        description: "设置地图匹配和显示性能选项。",
        mapMatching: {
            title: "地图匹配",
            descriptionAvailable: "有缓存时显示经过地图匹配的行程轨迹",
            descriptionUnavailable: "需要管理员配置 Valhalla 服务后才能使用。",
            detailsAvailable: "需要已配置的 Valhalla 实例。原始 GPS 数据、导出内容和时间线识别仍以原始轨迹为准。",
            detailsUnavailable: "管理员启用地图匹配并配置 Valhalla 后，你才能开启此选项。"
        },
        rawGps: {
            title: "使用原始 GPS 的移动类型",
            description: "为选中的移动类型显示原始 GPS 轨迹。",
            details: "地图匹配可能仍在后台运行。界面会隐藏匹配轨迹、处理进度、对比控件和详情。",
            placeholder: "为所有支持的类型使用匹配后的路线",
            ariaLabel: "显示原始 GPS 的移动类型"
        },
        simplification: {
            title: "轨迹简化",
            description: "减少绘制行程时使用的点数。",
            details: "使用 Douglas-Peucker 算法简化地图上绘制的轨迹，保留原有的时间线数据。"
        },
        tolerance: {
            title: "简化容差",
            description: "设置用于简化轨迹的距离阈值。",
            detailLowerLabel: "较小的值（1–10 m）",
            detailLowerValue: "保留更多细节，显示更多点",
            detailHigherLabel: "较大的值（20–100 m）",
            detailHigherValue: "进一步简化轨迹，显示更少的点"
        },
        maxPoints: {
            title: "最大点数",
            description: "限制每条轨迹显示的 GPS 点数。",
            details: "轨迹超过点数上限时，系统会自动增大容差。设为 0 可取消限制。"
        },
        adaptive: {
            title: "自适应简化",
            description: "根据行程长度自动调整简化程度。",
            details: "较长的行程采用较大的容差以改善性能，较短的行程则保留更多细节。"
        }
    },
    toggleAria: {
        mapMatching: "启用地图匹配"
    },
    pointsSuffix: " 个点",
    toleranceLabels: {
        low: "1 m（保留更多细节）",
        mid: "15 m（均衡）",
        high: "50 m（大幅简化）"
    },
    maxPointsLabels: {
        none: "0（不限）",
        balanced: "100（均衡）",
        high: "500（较高上限）"
    },
    dateRangeOptions: {
        today: "今天",
        yesterday: "昨天",
        lastWeek: "最近 7 天",
        lastMonth: "最近 30 天"
    },
    renderModeOptions: {
        vector: "矢量（MapLibre）",
        raster: "栅格（Leaflet）"
    },
    resetToDefaults: "恢复默认设置",
    saveChanges: "保存更改",
    validation: {
        tilePlaceholders: "URL 必须包含 {'{z}'}、{'{x}'} 和 {'{y}'} 占位符",
        protocol: "URL 必须使用 HTTP 或 HTTPS 协议",
        invalidProtocol: "URL 协议无效",
        invalidFormat: "URL 格式无效",
        styleEndpoint: "URL 应指向提供样式 JSON 的端点"
    }
}
