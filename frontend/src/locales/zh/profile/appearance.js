export default {
    title: "外观与无障碍",
    description: "选择容易辨认的地图颜色和线条样式，支持色觉辅助配色。",
    colorVision: {
        heading: "色觉辅助",
        description: "选择预设，同时设置轨迹、速度、热力图和图表中的行程类型颜色。你也可以在下方单独调整颜色。",
        customized: "已自定义",
        schemes: {
            DEFAULT: {
                name: "默认",
                description: "GeoPulse 的原始配色。"
            },
            RED_GREEN_SAFE: {
                name: "红绿色觉辅助",
                description: "适合绿色盲和红色盲，选用蓝色和黄色以便区分。"
            },
            BLUE_YELLOW_SAFE: {
                name: "蓝黄色觉辅助",
                description: "适合蓝色盲，选用红色和蓝绿色以便区分。"
            },
            HIGH_CONTRAST: {
                name: "高对比度",
                description: "采用亮度和色相差异明显的颜色，适用于各种色觉。"
            }
        }
    },
    preview: {
        heading: "预览",
        description: "预览当前设置下，轨迹在浅色、深色和卫星地图上的效果。",
        ariaLabel: "浅色、深色和卫星底图上的轨迹预览",
        backgrounds: {
            light: "浅色地图",
            dark: "深色地图",
            satellite: "卫星地图"
        },
        simulate: {
            label: "模拟色觉",
            none: "正常色觉",
            deuteranopia: "绿色盲",
            protanopia: "红色盲",
            tritanopia: "蓝色盲"
        }
    },
    paths: {
        heading: "轨迹",
        description: "设置地图上行程轨迹的颜色和线条样式。",
        followScheme: "跟随配色方案",
        resetColor: "使用方案颜色",
        defaultPathColor: {
            title: "轨迹颜色",
            description: "未选中行程的轨迹颜色。",
            details: "留空时跟随配色方案。开启按日期着色时，多日轨迹使用各日期的颜色。"
        },
        activePathColor: {
            title: "选中行程的颜色",
            description: "选中行程时用于高亮轨迹的颜色。",
            details: "留空时跟随配色方案。开启速度配色后，驾驶行程会按速度着色。"
        },
        pathWidth: {
            title: "线宽",
            description: "行程轨迹的粗细。选中的行程会略微加粗。",
            details: "较粗的线条在细节密集的地图或卫星地图上更容易看清。"
        },
        outline: {
            title: "轨迹描边",
            description: "为行程轨迹添加对比明显的描边。",
            details: "描边让各种颜色的轨迹在线条与底图颜色相近时也容易看清。"
        },
        widthLabels: {
            thin: "细",
            default: "默认",
            thick: "粗"
        }
    },
    speedBands: {
        heading: "驾驶速度配色",
        description: "选中的驾驶行程按速度着色，方便辨认较快和较慢的路段。",
        palette: {
            title: "速度配色",
            description: "设置驾驶行程中低速、中速和高速路段的颜色。",
            details: "低速低于 {slow} {unit}，中速为 {slow}–{medium} {unit}，高速高于 {medium} {unit}。关闭后，驾驶轨迹统一使用选中行程的颜色。",
            followScheme: "跟随配色方案（{name}）"
        },
        options: {
            DEFAULT: "红色 / 琥珀色 / 绿色",
            RED_GREEN_SAFE: "紫罗兰色 / 天蓝色 / 黄色",
            BLUE_YELLOW_SAFE: "红色 / 鲑红色 / 蓝绿色",
            HIGH_CONTRAST: "蓝色 / 玫瑰色 / 金色",
            OFF: "关闭（单色）"
        }
    },
    days: {
        heading: "多日范围",
        description: "查看多日记录时，每天的轨迹都可以用不同颜色显示。",
        pathColorMode: {
            title: "按日期设置轨迹颜色",
            description: "为多日范围内的每一天分配轨迹颜色，与时间线日期标题旁的圆点颜色一致。",
            details: "也可以使用地图上的日历按钮切换此设置。日期范围较短时每天使用不同颜色，较长时按日期从早到晚使用热力图渐变，并跳过与选中行程颜色过于接近的颜色。"
        },
        dayPalette: {
            title: "日期配色",
            description: "设置多日范围内各日期使用的颜色。",
            details: "Okabe-Ito、柔和和明暗交替配色适用于常见色觉。明暗交替配色通过交替使用不同亮度，让相邻日期在灰度下也容易区分。",
            followScheme: "跟随配色方案（{name}）"
        },
        options: {
            CLASSIC: "经典（7 色）",
            OKABE_ITO: "Okabe-Ito（7 色）",
            TOL_MUTED: "柔和（7 色）",
            CONTRAST: "明暗交替（7 色）"
        }
    },
    heatmap: {
        heading: "热力图",
        description: "设置时间线地图和回顾中的热力图配色。",
        gradient: {
            title: "热力图配色",
            description: "颜色按到访次数从少到多渐变。",
            details: "Viridis 和 Cividis 渐变在各种色觉和灰度下均易于辨认。",
            followScheme: "跟随配色方案（{name}）"
        },
        options: {
            CLASSIC: "经典（蓝色到红色）",
            VIRIDIS: "Viridis（紫色到黄色）",
            CIVIDIS: "Cividis（深蓝色到黄色）"
        }
    },
    map: {
        heading: "地图",
        description: "设置所有地图上显示的附加信息。",
        scaleBar: {
            title: "比例尺",
            description: "在地图左下角显示比例尺。",
            details: "按照距离单位设置使用千米或英里。"
        }
    },
    resetToDefaults: "恢复默认设置",
    saveChanges: "保存更改"
}
