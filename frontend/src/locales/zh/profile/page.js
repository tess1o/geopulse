export default {
    page: {
        title: "个人设置",
        description: "管理账户、偏好设置和已连接的应用",
        signedInAs: "当前登录账户为 {email}"
    },
    searchPlaceholder: "搜索个人设置…",
    sectionsAria: "个人设置分类",
    mobileSectionLabel: "设置分类",
    demoReadOnly: "演示模式下，个人资料、安全、显示、AI、Immich 和 Memos 设置均为只读，无法保存更改。",
    demoReadOnlyToast: "演示模式下无法更改个人设置。",
    groups: {
        personal: "个人",
        experience: "使用体验",
        connectedApps: "已连接的应用"
    },
    tabs: {
        general: "常规",
        security: "安全",
        timeline: "时间线与地图",
        appearance: "外观",
        notifications: "通知",
        connectedApps: "已连接的应用"
    },
    jump: {
        notVisibleTitle: "设置未显示",
        notVisibleDetail: "此设置目前未显示。请启用相关选项后再编辑。"
    },
    unsaved: {
        header: "有未保存的更改",
        message: "离开此页面将丢失尚未保存的个人设置更改。",
        leave: "不保存并离开",
        stay: "留在此页"
    },
    save: {
        updateFailed: "更新失败",
        saveFailed: "保存失败",
        notificationsSaved: "通知偏好已保存",
        profileUpdated: {
            title: "个人资料已更新",
            detail: "已保存你的个人资料"
        },
        displayUpdated: {
            title: "显示设置已更新",
            detail: "时间线显示设置已保存，即刻生效。"
        },
        passwordChanged: {
            title: "密码已更改",
            detail: "已成功更改你的密码"
        },
        passwordSet: {
            title: "密码已设置",
            detail: "已成功设置你的密码"
        },
        passwordChangeFailed: "更改密码失败",
        passwordSetFailed: "设置密码失败",
        aiSaved: {
            title: "保存成功",
            detail: "AI 设置已保存"
        },
        aiError: "错误",
        immichUpdated: {
            title: "Immich 设置已更新",
            detail: "Immich 集成设置已保存"
        },
        memosUpdated: {
            title: "Memos 设置已更新",
            detail: "Memos 集成设置已保存"
        }
    },
    errors: {
        incorrectPassword: "当前密码不正确",
        checkInformation: "请检查填写的信息后重试"
    },
    cached: {
        title: "正在使用缓存数据",
        detail: "无法获取最新个人资料，当前显示的是缓存内容。"
    }
}
