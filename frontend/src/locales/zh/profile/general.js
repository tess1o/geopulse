export default {
    title: "常规",
    description: "管理个人资料和常用显示偏好。",
    profile: {
        heading: "个人资料",
        description: "设置在 GeoPulse 中显示的姓名和头像。",
        fullName: {
            title: "姓名",
            description: "在你的账户上显示的姓名。",
            placeholder: "输入你的姓名"
        },
        image: {
            heading: "头像",
            optional: "可选",
            description: "自定义地图标记使用的头像。",
            upload: "上传自定义头像",
            formats: "支持 PNG、JPEG 或 WEBP，上传前会自动调整尺寸。",
            selected: "已选择 {name}",
            chooseAria: "选择头像 {index}"
        }
    },
    regional: {
        heading: "区域偏好",
        description: "设置时区、显示格式和计量单位。",
        timezone: {
            title: "时区",
            description: "用于显示日期、时间和统计数据。",
            placeholder: "选择你的时区"
        },
        dateFormat: {
            title: "日期格式",
            description: "设置数字日期的显示方式。",
            details: "URL 中的日期参数仍使用固定的 ISO 格式。",
            placeholder: "选择日期格式"
        },
        timeFormat: {
            title: "时间格式",
            description: "选择 12 小时制或 24 小时制。",
            placeholder: "选择时间格式"
        },
        timeDisplayMode: {
            title: "时间线时区",
            description: "时间线和 GPS 数据可以按个人设置的时区显示，也可以按到访地点的当地时间显示。",
            details: "日期仍按个人设置的时区分组。当地时区根据最近的城市确定，时区交界处的时间可能有偏差。将鼠标悬停在时间上可查看时区来源。",
            unavailable: "此服务器未加载 GeoNames 城市数据，暂时无法显示当地时间。时间均按个人设置的时区显示。"
        },
        distanceUnit: {
            title: "距离单位",
            description: "设置距离和速度的显示单位。",
            placeholder: "选择距离单位"
        },
        temperatureUnit: {
            title: "温度单位",
            description: "设置天气视图中的温度单位。",
            placeholder: "选择温度单位"
        }
    },
    navigation: {
        heading: "导航",
        description: "设置登录 GeoPulse 后首先打开的页面。",
        homePage: {
            title: "默认首页",
            description: "选择登录后显示的第一个页面。",
            details: "清除选择后将打开 GeoPulse 的默认首页。自定义地址必须是以 / 开头的站内路径。",
            placeholder: "选择默认首页",
            customOption: "自定义 URL…",
            customLabel: "自定义站内路径",
            customPlaceholder: "/app/your-custom-page"
        }
    },
    dateFormatOptions: {
        dmy: "DD/MM/YYYY（欧洲）",
        dmyDot: "DD.MM.YYYY（德国）",
        mdy: "MM/DD/YYYY（美国）",
        ymd: "YYYY-MM-DD（ISO）"
    },
    timeFormatOptions: {
        h24: "24 小时制（13:45）",
        h12: "12 小时制（下午 1:45）"
    },
    timeDisplayModeOptions: {
        profile: "个人设置的时区",
        location: "到访地点的当地时间"
    },
    distanceUnitOptions: {
        kilometers: "千米（km、m）",
        miles: "英里（mi、ft）"
    },
    temperatureUnitOptions: {
        celsius: "摄氏度（°C）",
        fahrenheit: "华氏度（°F）"
    },
    actions: {
        reset: "重置",
        save: "保存更改"
    },
    errors: {
        fullNameRequired: "请输入姓名",
        fullNameTooShort: "姓名至少需要 2 个字符",
        customUrlRequired: "请输入自定义 URL",
        customUrlInternal: "URL 必须是以 / 开头的站内路径",
        customUrlInvalid: "URL 格式无效",
        customUrlTooLong: "URL 过长，最多 1000 个字符",
        imageLoad: "无法加载图片",
        imageTooLarge: "压缩后的图片仍然过大，请换一张图片。",
        imageFormat: "不支持此图片格式，请使用 PNG、JPEG 或 WEBP。",
        imageUnsupported: "此浏览器不支持图片处理",
        imageProcess: "无法处理所选图片",
        avatarTooLarge: "压缩后的头像仍然过大"
    }
}
