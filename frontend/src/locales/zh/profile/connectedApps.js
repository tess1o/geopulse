export default {
    title: "已连接的应用",
    description: "配置为 GeoPulse 提供 AI、照片和笔记的服务。",
    listAria: "已连接的应用",
    apps: {
        ai: "AI 助手",
        immich: "Immich",
        memos: "Memos",
        mcp: "MCP"
    },
    status: {
        enabled: "已启用",
        notEnabled: "未启用",
        connected: "已连接",
        notConfigured: "未配置",
        available: "可用"
    },
    ai: {
        availability: {
            heading: "助手可用性",
            description: "设置是否在 GeoPulse 中提供 AI 聊天。",
            enabled: {
                title: "启用 AI 助手",
                description: "使用 AI 聊天并获取时间线相关帮助。"
            }
        },
        provider: {
            heading: "服务提供商",
            description: "配置助手使用的 OpenAI 服务或 OpenAI 兼容服务。",
            apiKeyRequired: {
                title: "要求 API 密钥",
                description: "服务提供商接受未经身份验证的请求时，可关闭此选项。"
            },
            apiKey: {
                title: "API 密钥",
                description: "添加或替换凭据时才需要输入新密钥。",
                placeholder: "输入 OpenAI API 密钥",
                placeholderConfigured: "已配置 API 密钥（输入新密钥可替换）",
                ariaLabel: "OpenAI API 密钥",
                configuredNote: "已配置 API 密钥，留空可继续使用当前密钥。",
                missingNote: "输入 API 密钥后即可发送经过身份验证的请求。"
            },
            baseUrl: {
                title: "API 基础 URL",
                description: "使用 OpenAI 的端点或其他兼容服务。"
            },
            model: {
                title: "模型",
                description: "从列表中选择模型，或输入模型标识符。",
                placeholder: "选择或输入模型名称",
                ariaLabel: "AI 模型"
            },
            refreshModels: {
                ariaLabel: "刷新服务提供商的模型列表",
                tooltip: "从服务器获取模型列表"
            }
        },
        behavior: {
            heading: "助手行为",
            description: "自定义每次对话时发送的指令。",
            systemMessage: {
                title: "系统消息",
                description: "清空消息以恢复服务器的默认设置。",
                details: "系统消息用于引导助手的语气和行为。",
                placeholder: "正在加载系统消息…",
                ariaLabel: "AI 系统消息"
            }
        },
        test: {
            success: "连接成功！",
            failure: "连接失败。请检查 URL 和 API 密钥。"
        },
        testConnection: "测试连接",
        saveSettings: "保存 AI 设置"
    },
    immich: {
        availability: {
            heading: "照片集成",
            description: "设置是否在时间线上显示 Immich 照片。",
            enabled: {
                title: "启用 Immich",
                description: "从 Immich 服务器同步时间线照片。",
                ariaLabel: "启用 Immich 集成"
            }
        },
        connection: {
            heading: "连接",
            description: "填写用于访问 Immich 的服务器地址和凭据。",
            serverUrl: {
                title: "服务器 URL",
                description: "输入 Immich 服务器的完整地址。",
                ariaLabel: "Immich 服务器 URL"
            },
            apiKey: {
                title: "API 密钥",
                description: "在 Immich 服务器设置中创建 API 密钥。",
                placeholder: "输入 Immich API 密钥",
                placeholderConfigured: "已配置 API 密钥（输入新密钥可替换）",
                ariaLabel: "Immich API 密钥",
                configuredNote: "已配置 API 密钥，留空可继续使用当前密钥。"
            }
        },
        messages: {
            connected: "已连接到 Immich 服务器。",
            userNotFound: "找不到配置的 Immich 用户",
            apiKeyRequired: "请输入 Immich API 密钥",
            authenticationFailed: "Immich 拒绝了 API 密钥",
            serverNotFound: "找不到 Immich 服务器",
            connectionTimeout: "连接 Immich 超时",
            connectionFailed: "无法连接到 Immich 服务器",
            testFailed: "无法测试连接",
            testError: "连接测试失败",
            unexpected: "发生意外错误",
            assetsAvailable: "有 {count} 个媒体文件可用。"
        },
        errors: {
            serverUrlRequired: "启用集成时必须填写服务器 URL",
            serverUrlInvalid: "请输入有效的 URL，例如 https://photos.example.com",
            apiKeyRequired: "启用集成时必须填写 API 密钥"
        },
        testConnection: "测试连接",
        reset: "重置",
        saveSettings: "保存设置"
    },
    memos: {
        availability: {
            heading: "笔记集成",
            description: "设置是否在时间线上显示带时间戳的 Memos 笔记。",
            enabled: {
                title: "启用 Memos",
                description: "从 Memos 服务器获取时间线上的笔记。",
                ariaLabel: "启用 Memos 集成"
            }
        },
        connection: {
            heading: "连接",
            description: "填写用于访问 Memos 的服务器地址和凭据。",
            serverUrl: {
                title: "服务器 URL",
                description: "输入 Memos 服务器的完整地址。",
                ariaLabel: "Memos 服务器 URL"
            },
            apiKey: {
                title: "API 密钥",
                description: "在 Memos 设置中创建 API 令牌。",
                placeholder: "输入 Memos API 密钥",
                placeholderConfigured: "已配置 API 密钥（输入新密钥可替换）",
                ariaLabel: "Memos API 密钥",
                configuredNote: "已配置 API 密钥，留空可继续使用当前密钥。"
            }
        },
        defaults: {
            heading: "时间线默认设置",
            description: "设置在 GeoPulse 中创建的笔记如何保存到 Memos。",
            destination: {
                title: "默认保存位置",
                description: "选择新笔记默认保存的位置。",
                ariaLabel: "默认保存位置",
                options: {
                    geopulse: "GeoPulse",
                    memos: "Memos"
                }
            },
            visibility: {
                title: "默认可见范围",
                description: "选择新笔记在 Memos 中的初始可见范围。",
                ariaLabel: "Memos 默认可见范围",
                options: {
                    private: "私密",
                    protected: "受保护",
                    public: "公开"
                }
            }
        },
        filtering: {
            heading: "筛选与性能",
            description: "设置搜索缓存，以及按标签筛选时间线中显示的笔记。",
            searchCache: {
                title: "搜索缓存",
                description: "复用最近的搜索结果，以加快加载时间线笔记。",
                ariaLabel: "启用 Memos 搜索缓存"
            },
            includeTags: {
                title: "包含标签",
                description: "仅加载至少包含其中一个标签的笔记。",
                placeholder: "输入标签后按 Enter 键"
            },
            excludeTags: {
                title: "排除标签",
                description: "隐藏包含任一这些标签的笔记。",
                placeholder: "输入标签后按 Enter 键"
            }
        },
        messages: {
            connected: "已连接到 Memos 服务器",
            userNotFound: "找不到用户",
            apiKeyRequired: "请输入 API 密钥",
            connectionFailed: "连接失败",
            testError: "连接测试失败",
            memoCount: "服务器返回了 {count} 条笔记"
        },
        errors: {
            serverUrlRequired: "启用集成时必须填写服务器 URL",
            serverUrlInvalid: "请输入有效的 URL",
            apiKeyRequired: "启用集成时必须填写 API 密钥"
        },
        testConnection: "测试连接",
        reset: "重置",
        saveSettings: "保存设置"
    },
    mcp: {
        connection: {
            heading: "连接",
            description: "GeoPulse 内置始终可用的 MCP 服务器，AI 客户端可通过其中的只读工具回答关于你的位置记录的问题。",
            serverUrl: {
                title: "服务器 URL",
                description: "MCP 客户端要连接的 Streamable HTTP 端点。"
            },
            token: {
                title: "API 令牌",
                description: "客户端使用只读 API 令牌登录。为每个客户端单独创建令牌，便于分别撤销。",
                create: "创建只读令牌",
                manage: "管理令牌",
                name: "MCP ({client})",
                shownOnce: "请立即复制此令牌。GeoPulse 仅显示一次。"
            }
        },
        client: {
            heading: "客户端设置",
            description: "选择客户端并复制配置。",
            options: {
                claudeDesktop: "Claude Desktop",
                claudeCode: "Claude Code",
                other: "其他客户端"
            },
            hints: {
                claudeDesktop: "将此配置添加到 {file}，然后重启 Claude Desktop。运行 npx 需要 Node.js。",
                claudeCode: "在终端中运行此命令，然后启动 Claude Code。",
                other: "任何支持 Streamable HTTP 和自定义请求头的客户端均可使用这些值。ChatGPT 通过隧道连接。",
                chatGptLink: "设置 ChatGPT"
            },
            placeholderHint: "用 API 令牌替换 {placeholder}，也可以在上方创建令牌并自动填入。"
        },
        tools: {
            heading: "AI 客户端可以读取的数据",
            description: "工具仅有权读取时间线、地点和共享设置。",
            areas: {
                timeline: {
                    title: "时间线",
                    example: "“我上周六做了什么？”"
                },
                now: {
                    title: "当前位置",
                    example: "“我现在在哪里？”"
                },
                places: {
                    title: "地点",
                    example: "“我去过哪些国家？”"
                },
                summaries: {
                    title: "汇总",
                    example: "“总结一下我 10 月的活动。”"
                },
                trips: {
                    title: "行程和时间段",
                    example: "“我是什么时候去日本的？”"
                },
                notesPhotosWeather: {
                    title: "笔记、照片和天气",
                    example: "“我徒步那天的天气怎么样？”"
                },
                geofences: {
                    title: "地理围栏",
                    example: "“我最近一次是什么时候离开公司的？”"
                },
                friends: {
                    title: "好友",
                    example: "“Alex 现在在哪里？”"
                }
            },
            requirements: "搜索照片需要启用 Immich 集成。查询天气和覆盖范围需要在服务器上启用对应功能。好友数据遵循你现有的共享权限。",
            docsLink: "MCP 文档"
        },
        copy: "复制",
        toasts: {
            copied: "已复制到剪贴板",
            copyFailed: "无法复制到剪贴板",
            createFailed: "无法创建令牌"
        }
    }
}
