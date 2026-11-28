export default {
    page: {
        title: "帮助与支持",
        description: "查看文档、获取帮助和反馈问题",
        faq: {
            title: "常见问题",
            description: "选择一个常见问题，在文档中查看答案。",
            browseAll: "查看所有常见问题",
            adminTitle: "管理员问题",
            questions: {
                timelineNotGenerating: "为什么没有生成时间线？",
                timelineGaps: "为什么时间线上有数据缺口？",
                tripMisclassified: "为什么驾车行程被识别为步行，或反过来？",
                jobsFailing: "时间线任务一直失败，该怎么办？",
                importGoogle: "可以导入 Google 时间线历史吗？",
                backup: "如何备份我的数据？",
                reduceMemory: "如何降低 GeoPulse 的内存占用？",
                backendRestarts: "后端重启或内存不足，该怎么办？"
            }
        },
        issues: {
            title: "遇到问题？",
            description: "如果时间线生成、行程分类或其他功能出现问题，可以导出匿名化的调试数据，帮助我们排查。",
            debugExport: {
                title: "导出调试数据",
                description: "导出的 GPS 数据会对所有坐标应用随机偏移，隐藏真实位置，方便你分享数据以排查问题。",
                bullets: [
                    "偏移坐标以保护隐私",
                    "地点名称已匿名化",
                    "包含用于分析的时间线配置",
                    "可以描述问题和发生时间"
                ],
                demoDisabledText: "为保护共享的演示数据，演示模式下无法导出调试数据。",
                demoDisabledTooltip: "演示模式下无法导出调试数据",
                button: "导出调试数据"
            },
            reportIssue: {
                title: "反馈问题",
                description: "发现错误或有功能建议？请在 GitHub 上反馈。",
                stepsTitle: "反馈步骤",
                steps: [
                    "使用上方按钮导出调试数据",
                    "在 GitHub 上创建新议题",
                    "附上导出的调试数据 ZIP 文件",
                    "描述实际问题和预期行为"
                ],
                button: "创建 GitHub 议题"
            },
            demoModeToast: "演示模式下无法导出调试数据。"
        },
        docs: {
            title: "文档",
            description: "查看 GeoPulse 各项功能的使用指南",
            gettingStarted: {
                title: "入门指南",
                description: "快速上手与初始配置"
            },
            timelineFeatures: {
                title: "时间线功能",
                description: "了解时间线的生成和处理方式"
            },
            tripClassification: {
                title: "行程分类",
                description: "了解行程的分类方式"
            },
            locationSources: {
                title: "位置来源",
                description: "支持的 GPS 位置来源及配置方法"
            },
            faq: {
                title: "常见问题",
                description: "查看常见问题的解答"
            },
            fullDocumentation: {
                title: "完整文档",
                description: "浏览和搜索完整文档"
            }
        },
        about: {
            title: "关于 GeoPulse",
            description: "应用信息与致谢",
            version: "版本",
            loading: "正在加载…",
            license: "许可证",
            licenseLinkText: "BSL 1.1 许可证",
            repository: "代码仓库",
            repositoryLinkText: "GitHub",
            author: "作者",
            authorLinkText: "tess1o",
            builtWith: "所用技术"
        }
    }
}
