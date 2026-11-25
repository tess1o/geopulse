export default {
    controls: {
        hideFavorites: "隐藏收藏地点",
        showFavorites: "显示收藏地点",
        hideTimeline: "隐藏时间线",
        showTimeline: "显示时间线",
        hidePath: "隐藏轨迹",
        showPath: "显示轨迹",
        heatmapTitle: "热力图",
        stays: "停留",
        trips: "行程",
        turnOff: "关闭",
        hidePhotos: "隐藏照片",
        showPhotos: "显示照片",
        hideNotes: "隐藏笔记",
        showNotes: "显示笔记",
        hideWeather: "隐藏天气",
        showWeather: "显示天气",
        hide3dBuildings: "隐藏 3D 建筑",
        show3dBuildings: "显示 3D 建筑",
        moreControls: "更多地图选项",
        zoomToData: "缩放至数据范围",
        heatmapSelectDateRange: "选择日期范围以启用热力图",
        heatmapEnabledTitle: "热力图已启用",
        heatmapShow: "显示热力图",
        routeDisplayRaw: "正在显示原始 GPS 轨迹。点击对比匹配后的路线和原始 GPS 轨迹。",
        routeDisplayComparison: "正在对比匹配后的路线和原始 GPS 轨迹。点击显示匹配后的路线。",
        routeDisplayMatched: "正在显示匹配后的路线。点击显示原始 GPS 轨迹。",
        rawGpsLoading: "正在加载原始 GPS 定位点",
        rawGpsHide: "隐藏原始 GPS 定位点",
        rawGpsShow: "显示原始 GPS 定位点",
        panoramaxUnsupported: "显示 Panoramax 覆盖范围需要 MapLibre 矢量地图",
        panoramaxHide: "隐藏 Panoramax 覆盖范围",
        panoramaxShow: "显示 Panoramax 覆盖范围",
        heatmapStaysMenu: "停留热力图",
        heatmapTripsMenu: "行程热力图",
        heatmapTurnOffMenu: "关闭热力图",
        dayColorsShow: "按日期为轨迹着色",
        dayColorsHide: "以单一颜色显示轨迹"
    },
    tripReplay: {
        pause: "暂停回放",
        play: "播放行程",
        stop: "停止回放",
        setSpeed: "设为 {speed} 倍速",
        followCamera: "跟随视角",
        follow: "跟随",
        enable3d: "启用 3D 视角",
        hideControls: "隐藏回放控件",
        showControls: "显示回放控件",
        replay: "回放"
    },
    messages: {
        engineFatal: "地图引擎初始化失败，已切换到栅格地图。",
        genericWarning: "地图警告"
    },
    appearanceMenu: {
        title: "地图配色",
        buttonLabel: "更改地图配色",
        asSharedBy: "使用 {name} 的配色",
        asShared: "使用分享者的配色",
        mine: "使用我的设置",
        remembered: "此浏览器会记住你的选择。"
    },
    legend: {
        speed: {
            title: "速度",
            ariaLabel: "速度颜色图例",
            slow: "低于 {speed}",
            medium: "{from}–{to}",
            fast: "高于 {speed}",
            unknown: "未知"
        },
        heatmap: {
            ariaLabel: "热力图颜色图例",
            low: "少",
            high: "多"
        },
        days: {
            ariaLabel: "日期颜色图例",
            gradientRange: "从 {from} 到 {to}"
        }
    },
    timelineMap: {
        contextMenu: {
            wasIHere: "我来过这里吗？",
            planToVisitHere: "计划前往这里",
            addToFavorites: "添加至收藏",
            addAreaToFavorites: "收藏一个区域",
            viewAllVisits: "查看所有到访记录",
            edit: "编辑",
            delete: "删除",
            editPlannedItem: "编辑计划项目",
            deletePlannedItem: "删除计划项目"
        },
        addFavoriteDialog: {
            pointHeader: "收藏地点",
            areaHeader: "收藏区域"
        },
        clearTripSelection: "取消选择行程",
        tripSummaryHoverHint: "将鼠标悬停在路线上可查看各点的时间和速度",
        zoomControl: {
            currentLocation: "缩放至当前位置",
            data: "缩放至数据范围"
        },
        immichError: {
            genericTitle: "Immich 照片错误",
            genericDetail: "无法从 Immich 加载照片",
            fetchTitle: "照片加载失败",
            fetchDetailFallback: "无法从你的 Immich 服务器获取照片，请检查配置。",
            refreshTitle: "刷新失败",
            refreshDetailFallback: "无法刷新 Immich 照片，请重试。",
            configTitle: "配置错误",
            configDetailFallback: "Immich 配置无效，请检查服务器设置。"
        },
        notesError: {
            genericTitle: "笔记错误",
            genericDetail: "笔记加载失败",
            fetchTitle: "笔记加载失败",
            fetchDetailFallback: "无法获取此日期范围内的笔记。",
            refreshTitle: "刷新失败",
            refreshDetailFallback: "无法刷新笔记，请重试。"
        },
        addFavoriteFailed: "缺少位置数据，无法添加至收藏。请重试。",
        rawGpsLimited: {
            summary: "仅显示部分原始 GPS 定位点",
            detail: "共 {totalCount} 个定位点，当前显示 {returnedCount} 个。缩小日期范围可查看详情。"
        },
        rawGpsLoadFailed: {
            summary: "原始 GPS 定位点",
            detail: "无法加载此日期范围内的原始 GPS 定位点。"
        }
    },
    popups: {
        common: {
            unknown: "未知",
            unknownTime: "时间未知",
            unknownLocation: "位置未知",
            notAvailable: "无数据",
            value: "数值",
            telemetry: "遥测数据",
            duration: "时长",
            distance: "距离",
            status: "状态",
            battery: "电量",
            speed: "速度",
            accuracy: "精度"
        },
        favorite: {
            pendingName: "待保存的收藏地点",
            name: "收藏地点",
            pendingArea: "待保存的区域",
            pendingPoint: "待保存的地点",
            areaFavorite: "收藏区域",
            favoritePoint: "收藏地点",
            category: "类别",
            description: "描述",
            address: "地址"
        },
        friend: {
            defaultName: "好友",
            lastSeen: "最后在线",
            location: "位置",
            activity: "活动",
            atCurrentPositionFor: "已在当前位置停留 {duration}",
            movingFor: "已移动 {duration}",
            avatarAlt: "{name}的头像",
            openInGoogleMaps: "在 Google 地图中打开"
        },
        location: {
            lastKnown: "GeoPulse 记录的最后已知位置",
            yourLocation: "你的位置",
            lastRecorded: "最后记录于 {time}",
            updated: "更新于 {time}",
            aboutMeters: "约 {value} 米",
            sharedLocation: "共享位置",
            visits: "到访次数",
            places: "地点",
            lastVisit: "最近到访",
            openCityDetails: "打开城市详情",
            openPlaceDetails: "打开地点详情"
        },
        timeline: {
            trip: "行程（{movementType}）",
            dataGap: "数据缺失",
            timelineItem: "时间线事件",
            hoverHint: "将鼠标悬停在高亮路线上，可查看你经过各点的时间和速度。",
            start: "开始",
            end: "结束",
            averageSpeed: "平均速度",
            movementTrip: "{movementType}行程",
            unknownMovement: "移动",
            tripStart: "行程起点",
            tripEnd: "行程终点",
            time: "时间",
            mode: "方式",
            panoramaxCoverage: "Panoramax 覆盖范围",
            zoomingIn: "正在放大查看详情…",
            photos: "照片",
            panoramicPhotos: "360° 全景照片",
            flatPhotos: "普通照片",
            user: "用户",
            stay: "停留",
            tripFallback: "行程",
            eventsAtLocation: "此处有 {count} 条事件记录",
            itemsAtLocation: "此处有 {count} 项记录",
            crossType: {
                note: "笔记",
                photoCount: "{count} 张照片",
                photo: "照片",
                sections: {
                    timeline: "停留和行程",
                    notes: "笔记",
                    photos: "照片"
                }
            },
            hoverTooltip: {
                speedPrefix: "速度：{speed}",
                exactGpsPoint: "GPS 实测定位点",
                estimatedBetweenPoints: "根据相邻定位点估算",
                fromTripStart: "距行程开始：{duration}"
            }
        },
        tripPlan: {
            plannedStop: "计划停留点",
            noDaySet: "未安排日期",
            notVisitedManual: "未到访（手动标记）",
            visitedWithConfidence: "已到访 · 置信度 {confidence}%",
            visited: "已到访",
            notVisitedYet: "尚未到访",
            priority: "优先级",
            must: "必去",
            optional: "可选"
        },
        weather: {
            observed: "观测时间",
            temperature: "气温",
            precipitation: "降水",
            wind: "风",
            samplesHere: "天气：{count} 条观测记录"
        },
        notes: {
            loadFailed: "笔记加载失败"
        },
        placesMap: {
            defaultTitle: "地点位置",
            defaultMarkerName: "所选地点"
        },
        viewerLocation: {
            findingLocation: "正在获取你的位置",
            centerOnLocation: "将你的位置居中",
            showLocation: "显示你的位置",
            hideLocation: "隐藏你的位置"
        },
        sharedLocation: {
            avatarAlt: "头像"
        },
        rawGps: {
            coordinates: "坐标",
            altitude: "海拔",
            locationUnavailable: "位置不可用",
            findingLocation: "正在查找位置…",
            pointsHere: "此处有 {count} 个 GPS 定位点",
            rawGpsPoint: "原始 GPS 定位点",
            favoriteSource: "收藏地点",
            geocodingSource: "地理编码",
            pointDetails: "定位点详情",
            showMore: "显示更多",
            showingPoints: "共 {total} 个定位点，显示前 {shown} 个",
            notAvailable: "无数据"
        }
    }
}
