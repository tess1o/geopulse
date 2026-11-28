export default {
    page: {
        title: "AI 聊天助手",
        description: "询问与位置记录有关的问题，了解自己的出行规律",
        disclaimer: "AI 根据数据生成的回答可能存在错误。请自行核实重要信息。",
        clearHistoryTooltip: "清空聊天记录",
        clearHistoryAriaLabel: "清空聊天记录"
    },
    status: {
        checking: "正在检查 AI 助手状态…",
        unavailable: {
            title: "AI 聊天不可用",
            disabled: "AI 助手当前已停用。",
            apiKeyMissing: "尚未配置 OpenAI API 密钥。",
            notConfigured: "AI 助手的配置有误。",
            configureButton: "配置 AI 助手",
            demoDisabledTooltip: "演示模式下的 AI 设置为只读",
            demoDisabledText: "演示模式下无法修改 AI 设置。"
        }
    },
    examples: {
        startTitle: "开始聊天",
        startDescription: "你可以询问与位置记录有关的问题，例如：",
        expiredTitle: "可以开始新的聊天了",
        expiredDescription: "上一次对话已过期。你可以继续询问与位置记录有关的问题：",
        walkOrDrive: "我步行多还是开车多？",
        busiestDay: "我一周中哪天出行最多？",
        citiesVisited: "这个月我去过多少个城市？",
        commonRoute: "我最常走的路线是哪一条？"
    },
    message: {
        error: "错误"
    },
    loading: {
        title: "AI 正在处理…",
        subtitle: "正在分析你的问题和位置记录"
    },
    input: {
        placeholder: "输入与位置记录有关的问题…",
        placeholderUnavailable: "AI 助手不可用",
        warningDisabled: "AI 助手已停用。",
        warningApiKeyMissing: "尚未配置 API 密钥。",
        warningUnavailable: "AI 助手不可用。"
    },
    timestamp: {
        justNow: "刚刚",
        minutesAgo: "{count} 分钟前",
        yesterday: "昨天 {time}"
    },
    toasts: {
        chatClearedSummary: "聊天已清空",
        chatClearedDetail: "聊天记录已清空。",
        chatErrorSummary: "聊天出错",
        genericError: "处理请求时出错。",
        aiDisabledError: "AI 助手已停用。请在个人设置中启用。",
        apiKeyRequiredError: "请在个人设置中添加 AI 服务的 API 密钥。",
        contextTooLargeError: "对话或数据量过大。请缩小问题范围或清空聊天记录。",
        rateLimitedError: "已达到 AI 服务的请求频率上限，请稍后再试。",
        authFailedError: "AI 服务拒绝了已配置的凭据。",
        invalidRequestError: "AI 服务拒绝了此请求。",
        unavailableSummary: "AI 聊天不可用",
        unavailableDemoDetail: "演示账户的 AI 设置为只读。",
        unavailableDisabledDetail: "AI 助手已停用。请在个人设置中启用。",
        unavailableApiKeyDetail: "请在个人设置中配置 OpenAI API 密钥，以使用聊天助手。",
        unavailableDefaultDetail: "请在个人设置中配置 AI 助手，以使用聊天功能。"
    }
}
