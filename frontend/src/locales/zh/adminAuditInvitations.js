export default {
    auditLogsPage: {
        title: "审计日志",
        subtitle: "查看管理员操作和系统更改",
        filters: {
            dateRangeLabel: "日期范围",
            dateRangePlaceholder: "选择日期范围",
            actionTypeLabel: "操作类型",
            actionTypePlaceholder: "所有操作",
            targetTypeLabel: "目标类型",
            targetTypePlaceholder: "所有目标",
            adminEmailLabel: "管理员邮箱",
            adminEmailPlaceholder: "按管理员邮箱筛选…",
            clearFilters: "清除筛选条件"
        },
        actionTypes: {
            settingChanged: "已更改设置",
            settingReset: "已重置设置",
            userEnabled: "已启用用户",
            userDisabled: "已停用用户",
            userDeleted: "已删除用户",
            userRoleChanged: "已更改用户角色",
            userPasswordReset: "已重置用户密码",
            oidcProviderCreated: "已创建 OIDC 提供方",
            oidcProviderUpdated: "已更新 OIDC 提供方",
            oidcProviderDeleted: "已删除 OIDC 提供方",
            oidcProviderReset: "已重置 OIDC 提供方",
            invitationCreated: "已创建邀请",
            invitationRevoked: "已撤销邀请",
            timelineRegenerationCampaignCreated: "已创建时间线批量重建任务",
            timelineRegenerationCampaignRetried: "已重试时间线批量重建任务",
            adminLogin: "管理员登录"
        },
        targetTypes: {
            setting: "设置",
            user: "用户",
            oidcProvider: "OIDC 提供方",
            invitation: "邀请",
            timelineRegenerationCampaign: "时间线批量重建任务"
        },
        table: {
            currentPageReport: "显示第 {first} 至 {last} 条审计日志，共 {totalRecords} 条",
            columns: {
                timestamp: "时间",
                admin: "管理员",
                action: "操作",
                targetType: "目标类型",
                targetId: "目标 ID",
                ipAddress: "IP 地址"
            },
            empty: "未找到审计日志。"
        },
        expansion: {
            detailsHeader: "详情",
            noDetails: "暂无更多详情",
            adminUserId: "管理员用户 ID",
            timestampLabel: "时间"
        },
        mobile: {
            targetId: "目标 ID",
            ipAddress: "IP 地址",
            detailsLabel: "详情"
        },
        toasts: {
            loadFailedDetail: "无法加载审计日志"
        }
    },
    invitationsPage: {
        title: "用户邀请",
        subtitle: "管理用户邀请链接",
        breadcrumb: "邀请",
        createInvitation: "创建邀请",
        statusFilterPlaceholder: "按状态筛选",
        statuses: {
            all: "所有状态",
            pending: "待使用",
            used: "已使用",
            expired: "已过期",
            revoked: "已撤销"
        },
        usedByDeletedUser: "已删除的用户",
        table: {
            currentPageReport: "显示第 {first} 至 {last} 条邀请，共 {totalRecords} 条",
            columns: {
                token: "令牌",
                createdBy: "创建者",
                created: "创建时间",
                expires: "到期时间",
                status: "状态",
                usedBy: "使用者",
                actions: "操作"
            },
            empty: "未找到邀请。",
            copyLinkTooltip: "复制链接",
            revokeTooltip: "撤销邀请"
        },
        mobile: {
            usedByPrefix: "使用者：{value}",
            copyLink: "复制链接",
            revoke: "撤销"
        },
        createDialog: {
            expirationDateLabel: "到期日期",
            expirationDatePlaceholder: "选择到期日期"
        },
        linkDialog: {
            header: "邀请链接已创建",
            shareText: "将此链接发给你想邀请的用户。",
            copyTooltip: "复制到剪贴板",
            expiresLabel: "到期时间：{date}",
            message: "此链接仅可使用一次，到期时间如上所示。",
            close: "关闭"
        },
        revokeDialog: {
            header: "确认撤销",
            message: "确定要撤销此邀请吗？",
            note: "撤销后，邀请链接将失效。",
            confirm: "撤销"
        },
        toasts: {
            loadFailedDetail: "无法加载邀请",
            createdDetail: "邀请已创建",
            createFailedFallback: "无法创建邀请",
            copiedDetail: "链接已复制到剪贴板",
            copyFailedDetail: "无法复制到剪贴板",
            revokedDetail: "邀请已撤销",
            revokeFailedFallback: "无法撤销邀请"
        }
    },
    oidcProvidersPage: {
        title: "OIDC 提供方",
        subtitle: "管理 OAuth/OIDC 身份验证提供方",
        addProvider: "添加提供方",
        table: {
            headerTitle: "已配置的提供方",
            columns: {
                name: "名称",
                displayName: "显示名称",
                enabled: "已启用",
                source: "来源",
                metadata: "元数据",
                clientId: "客户端 ID",
                actions: "操作"
            },
            enabledYes: "是",
            enabledNo: "否",
            sourceEnvironment: "环境变量",
            sourceCustom: "自定义",
            metadataCached: "已缓存",
            metadataNotCached: "未缓存",
            empty: "尚未配置 OIDC 提供方。",
            editTooltip: "编辑提供方",
            disableTooltip: "停用提供方",
            enableTooltip: "启用提供方",
            testTooltip: "测试连接",
            cannotDeleteTooltip: "仅通过环境变量定义的提供方需要从环境变量中移除。",
            revertTooltip: "删除自定义配置并恢复环境变量中的默认设置",
            deleteTooltip: "永久删除此自定义提供方"
        },
        mobile: {
            enabledBadge: "已启用",
            disabledBadge: "已停用",
            edit: "编辑",
            disable: "停用",
            enable: "启用",
            test: "测试",
            delete: "删除"
        },
        deleteDialog: {
            header: "确认删除",
            confirmMessage: "确定要删除提供方 {name} 吗？",
            envNoteLabel: "ℹ️ 提示",
            envNoteText: "此提供方也在环境变量中定义。",
            envNoteDetail: "删除后将移除数据库中的自定义配置，并恢复环境变量中的默认设置。",
            customWarningLabel: "⚠️ 警告",
            customWarningText: "此为自定义提供方。删除操作无法撤销。",
            revertButton: "恢复环境变量设置",
            deleteButton: "删除"
        },
        testResultDialog: {
            header: "提供方连接测试结果",
            successMessage: "已成功连接到 OIDC 提供方",
            endpointsTitle: "发现的端点",
            authorization: "授权端点",
            token: "令牌端点",
            userinfo: "用户信息端点",
            jwks: "JWKS 端点",
            issuer: "签发者",
            errorDetailsTitle: "错误详情",
            errorType: "类型",
            errorDetailsLabel: "详情",
            close: "关闭"
        },
        toasts: {
            loadFailedDetail: "无法加载 OIDC 提供方",
            updatedDetail: "提供方已更新",
            createdDetail: "提供方已创建",
            saveFailedFallback: "无法保存提供方",
            revertedDetail: "已移除自定义配置，提供方已恢复使用环境变量中的默认设置。",
            deletedDetail: "提供方已删除",
            deleteFailedFallback: "无法删除提供方",
            connectionSuccessSummary: "连接成功",
            connectionSuccessDetail: "提供方连接测试通过",
            connectionFailedSummary: "连接失败",
            connectionFailedDetail: "无法连接到提供方",
            testErrorDetail: "无法测试提供方连接",
            statusEnabledDetail: "提供方已启用",
            statusDisabledDetail: "提供方已停用",
            updateStatusFailedFallback: "无法更新提供方状态"
        }
    }
}
