export default {
    generic: {
        title: "出错了",
        message: "发生意外错误，请重试。"
    },
    unexpected: "发生意外错误",
    network: {
        title: "连接出现问题",
        message: "无法连接 GeoPulse 服务器，请检查网络连接后重试。",
        shortMessage: "无法连接 GeoPulse 服务器，请检查网络连接。"
    },
    timeout: {
        title: "请求超时",
        message: "请求耗时超过预期，请重试。"
    },
    session: {
        title: "会话已过期",
        message: "你的会话已过期，请重新登录。"
    },
    http: {
        "400": {
            title: "请求无效",
            message: "无法处理请求，请检查输入后重试。"
        },
        "401": {
            title: "需要身份验证",
            message: "@:errors.session.message"
        },
        "403": {
            title: "访问被拒绝",
            message: "你没有执行此操作的权限。"
        },
        "404": {
            title: "未找到",
            message: "找不到请求的资源。"
        },
        "409": {
            title: "请求冲突",
            message: "此操作与当前状态冲突，请刷新后重试。"
        },
        "429": {
            title: "请求过于频繁",
            message: "你的请求过于频繁，请稍等片刻后重试。"
        },
        "500": {
            title: "服务器错误",
            message: "服务器内部错误"
        },
        "502": {
            title: "服务不可用",
            message: "GeoPulse 暂时不可用，请在几分钟后重试。"
        },
        "503": {
            title: "服务不可用",
            message: "@:errors.http.502.message"
        },
        "504": {
            title: "服务不可用",
            message: "@:errors.http.502.message"
        },
        unknown: {
            title: "错误 {status}",
            message: "发生错误（{status}），请重试。"
        }
    },
    backendUnavailable: {
        title: "后端服务不可用",
        message: "GeoPulse 服务器暂时不可用，请稍后重试。"
    },
    reference: {
        hint: "在后端日志中查找 ID {id}"
    },
    retry: {
        available: "可以重试",
        hint: "点击重试"
    },
    page: {
        connection: {
            title: "连接出现问题",
            message: "无法连接 GeoPulse 服务器，可能是网络异常或服务器正在维护。"
        },
        server: {
            title: "服务器错误",
            message: "GeoPulse 服务器出现故障，我们的团队已收到通知，正在处理。"
        },
        generic: {
            title: "出错了",
            message: "发生意外错误，请重试。如果问题持续存在，请联系支持人员。"
        },
        actions: {
            checkConnection: "检查连接",
            goHome: "返回首页"
        },
        details: {
            summary: "技术详情",
            requestHeading: "请求信息",
            method: "请求方法",
            url: "请求 URL",
            status: "状态",
            timestamp: "时间戳",
            errorHeading: "错误信息",
            requestId: "请求 ID",
            errorId: "错误 ID"
        },
        tips: {
            heading: "你可以尝试以下操作",
            wait: "服务器可能正在重启，请等几分钟再点击「检查连接」",
            internet: "检查网络连接",
            refresh: "尝试刷新页面或清除浏览器缓存",
            maintenance: "如果问题持续存在，GeoPulse 服务器可能正在维护",
            checkBack: "10 至 15 分钟后再检查"
        },
        status: {
            internetConnection: "网络连接",
            backend: "GeoPulse 后端",
            lastChecked: "上次检查",
            online: "后端在线",
            offline: "后端离线",
            connected: "已连接",
            offlineShort: "离线"
        },
        restored: {
            title: "连接已恢复",
            detail: "GeoPulse 服务器已恢复，正在跳转…"
        },
        stillUnavailable: {
            title: "服务仍不可用",
            detail: "GeoPulse 服务器尚未恢复，请在几分钟后重试。"
        }
    }
}
