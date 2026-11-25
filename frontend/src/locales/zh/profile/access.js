export default {
    oidc: {
        heading: "关联账户",
        description: "管理社交或企业账户的 OIDC 登录方式。",
        linkedHeading: "已关联的账户",
        providerIconAlt: "{provider} 图标",
        unlink: "解除关联",
        tooltips: {
            demoDisabled: "演示模式下不可用",
            unlinkBlocked: "尚未设置密码，无法解除唯一登录方式的关联。",
            unlink: "解除此账户的关联"
        },
        linkHeading: "关联其他账户",
        link: "关联",
        noPasswordWarning: "你尚未设置密码。解除唯一关联账户前，请先添加其他登录方式。",
        confirm: {
            header: "确认解除关联",
            message: "确定要解除与 {provider} 账户的关联吗？此操作无法撤销。"
        },
        toasts: {
            error: "错误",
            loadFailed: "无法加载已关联的账户信息。",
            linkFailed: {
                title: "关联失败",
                detail: "无法发起 {provider} 账户关联"
            },
            unlinked: {
                title: "已解除账户关联",
                detail: "已解除与 {provider} 账户的关联。"
            },
            unlinkFailed: {
                title: "解除关联失败",
                detail: "无法解除账户关联"
            }
        }
    },
    apiTokens: {
        heading: "API 令牌",
        description: "为机器人、MCP 客户端和自动化任务创建有名称的令牌。",
        create: "创建令牌",
        columns: {
            name: "名称",
            status: "状态",
            expires: "到期时间",
            lastUsed: "上次使用",
            actions: "操作",
            access: "权限"
        },
        access: {
            label: "权限",
            fixedHint: "如需调整权限，请创建新令牌并撤销当前令牌。",
            presets: {
                full: {
                    title: "完整权限",
                    description: "拥有你账户的全部权限，包括管理 API 令牌。管理员也可以访问管理后台。"
                },
                readOnly: {
                    title: "只读",
                    description: "允许查看数据和使用 MCP 客户端。"
                },
                homeAssistant: {
                    title: "Home Assistant",
                    description: "允许从 Home Assistant 读取数据和发送位置。"
                },
                backup: {
                    title: "备份",
                    description: "允许读取数据、创建和下载导出文件。"
                },
                custom: {
                    title: "自定义",
                    description: "逐项选择权限。"
                }
            },
            scopes: {
                read: {
                    title: "读取",
                    description: "查看时间线、地点、统计数据、好友和设置。"
                },
                write: {
                    title: "写入",
                    description: "更改或删除数据和设置。"
                },
                gpsIngest: {
                    title: "发送位置",
                    description: "使用此令牌发送 GPS 位置点。"
                },
                export: {
                    title: "导出",
                    description: "创建并下载导出文件。"
                },
                import: {
                    title: "导入",
                    description: "导入文件以添加数据。替换现有数据还需要写入权限。"
                }
            }
        },
        never: "—",
        status: {
            active: "有效",
            expired: "已过期",
            revoked: "已撤销"
        },
        tooltips: {
            edit: "编辑令牌",
            revoke: "撤销令牌"
        },
        empty: "尚未创建 API 令牌。",
        cancel: "取消",
        editDialog: {
            editHeader: "编辑 API 令牌",
            createHeader: "创建 API 令牌",
            nameLabel: "名称",
            namePlaceholder: "自动化令牌",
            expirationLabel: "到期时间",
            expirationPlaceholder: "永不过期",
            expirationHint: "留空则永不过期。",
            save: "保存",
            create: "创建"
        },
        createdDialog: {
            header: "API 令牌已创建",
            message: "此令牌仅显示一次，请在关闭此对话框前妥善保存。",
            confirm: "我已保存令牌",
            homeAssistantNext: "复制令牌后，为它添加一个 Home Assistant 位置来源，即可在 GeoPulse 中查看 Home Assistant 发送的位置。此令牌仅显示一次。",
            homeAssistantLink: "添加 Home Assistant 位置来源"
        },
        revokeDialog: {
            header: "撤销 API 令牌",
            confirmPrefix: "确定要撤销令牌“",
            confirmSuffix: "”吗？使用此令牌的自动化任务会立即停止。",
            confirm: "撤销"
        },
        validation: {
            nameRequired: "请输入令牌名称",
            scopeRequired: "请至少选择一项权限"
        },
        toasts: {
            error: "错误",
            loadFailed: "无法加载 API 令牌",
            saveFailed: "无法保存 API 令牌",
            revokeFailed: "无法撤销 API 令牌",
            saved: {
                title: "已保存",
                detail: "API 令牌已更新"
            },
            created: {
                title: "已创建",
                detail: "API 令牌已创建"
            },
            revoked: {
                title: "已撤销",
                detail: "API 令牌已撤销"
            },
            copied: {
                title: "已复制",
                detail: "令牌已复制到剪贴板"
            },
            copyFailed: {
                title: "复制失败",
                detail: "请选择令牌并手动复制"
            }
        }
    }
}
