export default {
    title: "通知",
    description: "选择接收哪些事件的提醒以及通知方式。",
    gpsHealth: {
        heading: "GPS 运行状态",
        description: "所有正在运行的实时 GPS 来源都停止发送数据时发出提醒。",
        monitor: {
            title: "监控 GPS 数据接收",
            description: "检测所有正在运行的实时来源是否长时间未发送数据。"
        },
        silence: {
            title: "无数据提醒阈值",
            description: "持续未收到数据达到设定时间后，发出首次提醒。",
            details: "可设置为 1 分钟至 7 天。",
            ariaLabel: "GPS 无数据提醒阈值（分钟）"
        },
        channelLabel: "GPS 状态通知方式"
    },
    rewind: {
        heading: "月度回顾",
        description: "上月的回顾可查看时发送提醒。",
        notify: {
            title: "月度回顾生成后提醒",
            description: "每月第一天发出提醒。"
        },
        channelLabel: "月度回顾通知方式"
    },
    productUpdates: {
        heading: "产品更新",
        description: "设置是否在 GeoPulse 中显示新功能公告。",
        whatsNew: {
            title: "显示更新亮点",
            description: "升级后显示一次“新功能”。",
            details: "更新亮点仅在 GeoPulse 内显示。"
        }
    },
    silenceSuffix: " 分钟",
    saveChanges: "保存更改",
    channels: {
        inApp: {
            title: "在通知中心显示",
            description: "在 GeoPulse 通知中心添加提醒。"
        },
        apprise: {
            title: "通过 Apprise 发送",
            description: "将此提醒转发至已配置的外部目的地。"
        },
        routing: {
            title: "Apprise 投递方式",
            description: "选择 GeoPulse 如何确定通知发送到哪里。",
            urls: "目标 URL",
            keyTag: "Apprise 配置键和标签"
        },
        destinationUrls: {
            title: "目标 URL",
            description: "输入一个或多个 Apprise 目标 URL。",
            ariaLabel: "Apprise 目标 URL"
        },
        configKey: {
            title: "配置键",
            description: "已保存的 Apprise 配置名称。",
            placeholder: "Apprise 配置键",
            ariaLabel: "Apprise 配置键"
        },
        configTag: {
            title: "配置标签",
            description: "可选，用于选择已配置的投递目标。",
            placeholder: "Apprise 标签（可选）",
            ariaLabel: "Apprise 配置标签"
        }
    }
}
