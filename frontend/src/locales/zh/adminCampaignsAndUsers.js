export default {
    campaignsPage: {
        breadcrumb: {
            title: "时间线批量重建"
        },
        header: {
            title: "时间线批量重建",
            subtitle: "创建并监控强制重新生成时间线的批量任务"
        },
        createCampaign: "创建批量任务",
        labels: {
            campaign: "批量任务",
            source: "来源",
            status: "状态",
            affectedFrom: "受影响数据起始时间",
            progress: "进度",
            reason: "原因",
            affectedUsers: "受影响用户",
            total: "总数",
            pending: "待处理",
            running: "运行中",
            completed: "已完成",
            failed: "失败",
            skipped: "已跳过",
            created: "创建时间",
            updated: "更新时间",
            actions: "操作",
            done: "已处理"
        },
        table: {
            currentPageReport: "共 {totalRecords} 项批量任务，显示第 {first} 至 {last} 项",
            empty: "未找到时间线批量重建任务。",
            retryTooltip: "重试失败的用户任务",
            progressText: "已处理 {processed} / {total} 项",
            pendingCount: "{count} 项待处理",
            runningCount: "{count} 项运行中",
            doneCount: "已处理 {count} 项"
        },
        mobile: {
            detailsButton: "详情",
            retryButton: "重试失败项"
        },
        createDialog: {
            header: "创建时间线批量重建任务",
            campaignKeyLabel: "批量任务标识",
            campaignKeyPlaceholder: "july-12-timeline-repair",
            regenerateFromLabel: "重建起始时间",
            regenerateFromPlaceholder: "选择起始日期和时间",
            reasonLabel: "原因",
            reasonPlaceholder: "说明必须重新生成时间线的原因。用户会看到这条消息。",
            previewRequired: "需要先预览",
            previewHelp: "预览将统计在所选时间及之后有 GPS 数据的用户人数。",
            runPreview: "查看预览",
            reviewCreate: "检查后创建"
        },
        confirmDialog: {
            header: "确认重建时间线"
        },
        detailsDialog: {
            header: "时间线批量重建详情",
            usersTitle: "正在处理、等待重试及处理失败的用户",
            retryButton: "重试失败项",
            table: {
                email: "电子邮箱",
                attempts: "尝试次数",
                lastError: "上次错误",
                nextAttempt: "下次尝试",
                empty: "没有正在处理、等待重试或处理失败的用户。"
            },
            close: "关闭"
        },
        toasts: {
            loadFailed: "无法加载时间线批量重建任务",
            previewFailedSummary: "预览失败",
            previewFailedDetail: "无法预览受影响的用户",
            createdSummary: "批量任务已创建",
            createdDetail: "已创建时间线批量重建任务。",
            createFailedSummary: "创建失败",
            createFailedDetail: "无法创建时间线批量重建任务",
            detailsLoadFailed: "无法加载批量任务详情",
            retryQueuedSummary: "重试已排队",
            retryQueuedDetail: "此批量任务中处理失败的用户已排队等待重试。",
            retryFailedSummary: "重试失败",
            retryFailedDetail: "无法重试处理失败的用户"
        }
    },
    userDetailsPage: {
        title: "用户详情",
        breadcrumb: {
            users: "用户",
            loading: "正在加载…",
            notFound: "未找到用户"
        },
        noName: "无姓名",
        never: "从未",
        infoCard: {
            title: "用户信息",
            authentication: "身份验证",
            password: "密码",
            oidcOnly: "仅 OIDC",
            timezone: "时区",
            linkedOidcProviders: "已关联的 OIDC 提供方"
        },
        statsCard: {
            title: "活动与统计",
            gpsPoints: "GPS 定位点",
            lastGpsPoint: "最近的 GPS 定位点",
            accountCreated: "账户创建时间",
            lastUpdated: "最近更新时间"
        },
        actionsCard: {
            title: "管理操作",
            demoteToUser: "降为普通用户",
            promoteToAdmin: "提升为管理员",
            resetPassword: "重置密码",
            warningMessage: "你无法停用或删除自己的账户。"
        },
        apiTokensCard: {
            title: "API 令牌",
            columnName: "名称",
            columnStatus: "状态",
            columnExpires: "到期时间",
            columnLastUsed: "最近使用",
            columnActions: "操作",
            revokeTooltip: "撤销令牌",
            empty: "未找到 API 令牌。",
            status: {
                active: "有效",
                expired: "已过期",
                revoked: "已撤销"
            }
        },
        notFound: {
            message: "未找到用户",
            backButton: "返回用户列表"
        },
        deleteDialog: {
            message: "确定要删除账户 {email} 吗？",
            detail: "此操作会永久删除该账户的所有数据。"
        },
        passwordDialog: {
            header: "密码重置",
            message: "{email} 的临时密码：",
            copyHint: "请通过安全渠道将此密码交给用户。",
            close: "关闭"
        },
        revokeDialog: {
            header: "撤销 API 令牌",
            message: "要撤销 {email} 的令牌 {tokenName} 吗？使用此令牌的自动化任务将立即停止。",
            revokeButton: "撤销"
        },
        toasts: {
            loadUserFailed: "无法加载用户详情",
            loadTokensFailed: "无法加载 API 令牌",
            roleChanged: "用户角色已改为{role}",
            roleChangeFailed: "无法更改用户角色",
            passwordResetSuccess: "密码已重置",
            passwordResetFailed: "无法重置密码",
            passwordCopied: "密码已复制到剪贴板",
            passwordCopyFailed: "无法将密码复制到剪贴板",
            tokenRevokedSummary: "已撤销",
            tokenRevokedDetail: "API 令牌已撤销",
            tokenRevokeFailed: "无法撤销 API 令牌"
        }
    }
}
