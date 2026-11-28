export default {
    tables: {
        exportCsv: "导出 CSV",
        exportDisabledDemo: "演示模式下无法导出",
        durationPlaceholder: "时长",
        distancePlaceholder: "距离",
        transportPlaceholder: "行程类型",
        searchLocations: "搜索地点…",
        searchOriginsDestinations: "搜索出发地或目的地…",
        viewDetails: "查看详情",
        details: "详情",
        edit: "编辑",
        why: "原因",
        editMovementType: "编辑出行方式",
        whyClassification: "为什么这样分类？",
        setManually: "手动设置",
        setTransportManually: "手动设置出行方式",
        manual: "手动",
        notAvailable: "暂无",
        start: "开始",
        end: "结束",
        coords: "坐标",
        viewPlaceDetails: "查看地点详情",
        unknownLocation: "未知位置",
        unknownOrigin: "未知出发地",
        unknownDestination: "未知目的地",
        routeTo: "{origin} 至 {destination}",
        dataGaps: {
            sectionTitle: "数据缺口",
            exportTooltip: "将数据缺口导出为 CSV",
            count: "{count} 处缺口",
            startTimeHeader: "开始时间",
            endTimeHeader: "结束时间",
            durationHeader: "时长",
            title: "数据缺口",
            noDataTitle: "未发现数据缺口",
            noDataMessage: "在所选日期范围和筛选条件下，未发现数据缺口。GPS 追踪看起来运行正常。",
            durationOptions: {
                lessThan1Hour: "少于 1 小时",
                oneToTwoHours: "1 至 2 小时",
                twoToEightHours: "2 至 8 小时",
                moreThan8Hours: "超过 8 小时"
            }
        },
        stays: {
            title: "停留",
            exportTooltip: "将停留导出为 CSV",
            count: "{count} 次停留",
            startTimeHeader: "开始时间",
            endTimeHeader: "结束时间",
            durationHeader: "时长",
            locationHeader: "位置",
            coordinatesHeader: "坐标",
            actionsHeader: "操作",
            noDataTitle: "没有找到停留",
            noDataMessage: "在所选日期范围和筛选条件下，没有找到停留记录。",
            durationOptions: {
                lessThan1Hour: "少于 1 小时",
                oneToFourHours: "1 至 4 小时",
                fourToEightHours: "4 至 8 小时",
                moreThan8Hours: "超过 8 小时"
            }
        },
        trips: {
            title: "行程",
            exportTooltip: "将行程导出为 CSV",
            count: "{count} 次行程",
            startTimeHeader: "开始时间",
            endTimeHeader: "结束时间",
            durationHeader: "时长",
            originHeader: "出发地",
            destinationHeader: "目的地",
            distanceHeader: "距离",
            transportHeader: "出行方式",
            actionsHeader: "操作",
            noDataTitle: "没有找到行程",
            noDataMessage: "在所选日期范围和筛选条件下，没有找到行程记录。",
            distanceOptionsKm: {
                lessThan1: "少于 1 公里",
                oneToTen: "1 至 10 公里",
                tenToFifty: "10 至 50 公里",
                moreThanFifty: "超过 50 公里"
            },
            distanceOptionsMiles: {
                lessThan1: "少于 1 英里",
                oneToTen: "1 至 10 英里",
                tenToFifty: "10 至 50 英里",
                moreThanFifty: "超过 50 英里"
            }
        },
        filtersDefault: {
            durationOptions: {
                lessThan1Hour: "少于 1 小时",
                oneToSixHours: "1 至 6 小时",
                sixPlusHours: "6 小时以上"
            },
            distanceOptions: {
                lessThan1Km: "少于 1 公里",
                oneToTenKm: "1 至 10 公里",
                tenPlusKm: "10 公里以上"
            }
        }
    },
    debugExport: {
        pageTitle: "导出调试数据",
        pageDescription: "导出 GPS 数据以排查时间线问题。导出时会偏移坐标以保护隐私。",
        privacyTitle: "隐私保护",
        privacyDescription: "所有 GPS 坐标都会向东或向西偏移相同的经度差。距离、速度和轨迹形状保持一致，可以准确复现时间线。导出文件不会公开你的真实位置坐标。",
        demoDisabledMessage: "公开演示禁止导出调试数据，因此演示模式下此表单为只读。",
        sectionTitle: "导出配置",
        timeRangeLabel: "时间范围",
        startDatePlaceholder: "开始日期",
        endDatePlaceholder: "结束日期",
        dateRangeSeparator: "至",
        timeRangeHelp: "按你的时区导出选定日期内的全天数据（00:00:00 至 23:59:59），以免遗漏数据。",
        coordinateShiftLabel: "坐标偏移（经度）",
        generateNewShift: "重新生成随机偏移量",
        longitudeShiftLabel: "经度偏移量",
        longitudeShiftPlaceholder: "例如 45.678901",
        shiftHelp: "已自动生成随机偏移量，可以重新生成或手动修改。所有坐标向东或向西偏移相同的经度差。纬度保持原值，以免改变距离而无法复现时间线。",
        issueLabel: "遇到了什么问题？",
        issuePlaceholder: "例如，周二开车回家的行程缺失了。我在办公室待到 18:00，18:40 到家。",
        issueTimeLabel: "问题发生时间（选填）",
        issueTimePlaceholder: "问题发生的日期和时间",
        issueHelp: "请描述预期结果和时间线实际显示的内容。描述和发生时间会一并导出，方便开发者排查。",
        exportInProgress: "正在准备导出调试数据…",
        formatLabel: "调试数据导出",
        exportOptionsLabel: "导出选项",
        includeConfiguration: "包含时间线配置",
        includeConfigurationHelp: "包含生成时间线时所用的配置，以便使用相同的参数复现。",
        includeTimeline: "包含我的当前时间线",
        includeTimelineHelp: "包含当前显示的停留、行程和数据缺口，以便与重建后的时间线比较。",
        exportButton: "导出调试数据",
        exportFailedLabel: "导出失败",
        whatWillBeExportedTitle: "导出内容",
        zipDescription: "导出的 ZIP 文件包含以下内容。",
        items: {
            metadata: "导出元数据（日期范围、数量、版本）",
            gpsData: "OwnTracks 格式的所有 GPS 点，已偏移坐标",
            timelineConfig: "完整的时间线配置（如已勾选）",
            favoriteLocations: "已匿名化及偏移坐标的收藏地点",
            favoriteAreas: "已匿名化及偏移边界的收藏区域",
            reverseGeocoding: "停留所匹配的地点，已匿名化及偏移坐标",
            timeline: "当前时间线，地点名称已匿名化（如已勾选）"
        },
        privacyNote: "所有坐标都使用相同的偏移量。地点名称会匿名化，例如「家」替换为「位置 1」，地址替换为「地点 1」。这样既能保留时间线结构，也能保护隐私。导出内容可能向所选日期前后各延伸几小时，以保留更早开始的完整停留。为保护隐私，导出文件不包含坐标偏移量。",
        validation: {
            selectDates: "请选择开始和结束日期",
            startBeforeEnd: "开始日期必须早于结束日期",
            startNotFuture: "开始日期不能晚于今天",
            enterShift: "请生成或输入坐标偏移量"
        },
        demoToast: "演示模式下无法导出调试数据。",
        exportSuccessSummary: "导出成功",
        exportSuccessDetail: "已将调试数据下载为 ZIP 文件",
        exportFailedFallback: "无法导出调试数据"
    },
    debugImport: {
        pageTitle: "导入调试数据",
        pageDescription: "从调试导出的 ZIP 文件中导入 GPS 数据和时间线配置",
        importantTitle: "导入前须知",
        importantDescription: "此功能用于排查问题，将导入已偏移的 GPS 数据、匿名化的收藏地点、匹配的地点、手动时间线修正和时间线配置。默认会在导入前清除所有现有数据，包括收藏地点。请使用单独的账户操作。",
        sectionTitle: "上传调试数据 ZIP 文件",
        uploadPromptText: "点击选择 ZIP 文件，或将文件拖到此处",
        uploadHint: "仅接受通过调试导出生成的 .zip 文件",
        remove: "移除",
        importOptionsLabel: "导入选项",
        clearExistingData: "导入前清除现有数据",
        clearExistingDataHelp: "导入前将删除所有 GPS 点、时间线数据和收藏地点。建议在新账户中使用此选项排查问题。",
        updateTimelineConfig: "更新时间线配置",
        updateTimelineConfigHelp: "使用 ZIP 文件中的配置替换当前时间线设置。复现相同的时间线需要启用此选项。",
        updateTimezone: "使用导出文件中的时区",
        updateTimezoneHelp: "将你的时区设为导出者的时区，使时间线每天的起止时间与对方一致。",
        importInProgress: "正在导入数据并重建时间线…",
        importButton: "导入调试数据",
        importFailedLabel: "导入失败",
        whatWillBeImportedTitle: "导入内容",
        zipDescription: "ZIP 文件应包含调试导出生成的以下内容。",
        items: {
            metadata: "导出元数据和验证信息",
            gpsData: "已偏移坐标的 GPS 点",
            timelineConfig: "时间线生成设置",
            favoriteLocations: "已匿名化的收藏地点",
            favoriteAreas: "已匿名化的收藏区域",
            reverseGeocoding: "停留所匹配的地点，已匿名化",
            timelineOverrides: "手动时间线修正",
            timeline: "用户看到的时间线，用于与重建结果比较"
        },
        importNote: "导入后会使用导入的数据重建时间线，再与用户当时看到的时间线比较。",
        report: {
            title: "导入报告",
            openTimeline: "在时间线中打开",
            summary: {
                match: {
                    title: "已复现时间线",
                    text: "导出范围内的全部 {matching} 条停留、行程和数据缺口记录均与用户看到的一致。"
                },
                differs: {
                    title: "时间线存在差异",
                    text: "与用户看到的时间线相比，有 {matching} 条事件一致、{different} 条不同、{missing} 条缺失、{extra} 条新增。"
                },
                unavailable: {
                    title: "没有可比较的时间线",
                    text: "导出文件未包含用户的时间线，无法与重建后的时间线比较。"
                }
            },
            issueTitle: "用户反馈的问题",
            warningsTitle: "需要检查的事项",
            comparisonTitle: "用户时间线与重建结果对比",
            comparisonWhyHelp: "对于存在差异的事件，两侧分别说明各自服务器生成该事件的原因。",
            comparisonHelp: "显示用户时区下 {from} 至 {to} 的事件。开始时间相同的事件会配对展示。",
            columns: {
                status: "状态",
                event: "事件",
                expected: "用户所见",
                actual: "重建结果"
            },
            status: {
                MATCH: "一致",
                DIFFERENT: "不同",
                MISSING: "缺失",
                EXTRA: "新增"
            },
            eventType: {
                STAY: "停留",
                TRIP: "行程",
                GAP: "数据缺口"
            },
            ongoing: "进行中",
            detailsTitle: "导出详情",
            details: {
                exportVersion: "导出格式",
                appVersions: "GeoPulse 版本（用户端 → 当前服务器）",
                exportDate: "导出时间",
                timezone: "用户时区",
                applied: "已应用",
                notApplied: "未应用",
                requestedRange: "请求范围",
                exportedRange: "实际导出范围",
                imported: "已导入",
                importedValue: "{gpsPoints} 个 GPS 点、{favoriteLocations} 个收藏地点、{favoriteAreas} 个收藏区域和 {geocodingLocations} 个地点",
                timelineSettings: "时间线设置",
                water: "船舶行程检测",
                waterValue: "{count} 个 GPS 点包含用户原有的水域分类"
            },
            timelineStatusTitle: "用户时间线状态",
            timelineStatus: {
                lastBuilt: "上次生成时间",
                pointsAfterBuild: "上次生成后收到的 GPS 点数",
                pendingRebuild: "待重建",
                pendingFrom: "从 {from} 开始，已失败 {attempts} 次",
                none: "无"
            },
            campaigns: {
                reason: "重建原因",
                status: "状态",
                completed: "已完成"
            },
            gpsSourcesTitle: "用户的 GPS 位置来源",
            gpsSourcesHelp: "被这些筛选规则排除的定位点从未保存，因此也不在导出文件中。",
            gpsSources: {
                source: "来源",
                accuracyFilter: "定位误差筛选",
                duplicateDetection: "重复点检测",
                accuracyValue: "已启用。排除定位误差超过 {accuracy} 米或速度超过 {speed} 公里/小时的点",
                duplicateValue: "已启用。检测 {minutes} 分钟内的重复点",
                off: "已关闭",
                inactive: "未激活"
            },
            warnings: {
                TIMELINE_DIFFERS: "重建的时间线与用户看到的不同，有 {different} 条事件存在差异、{missing} 条缺失、{extra} 条新增。",
                TIMELINE_STALE: "用户的时间线上次生成后又收到 {points} 个 GPS 点。用户看到的时间线可能已过时。",
                REBUILD_FAILING: "用户的时间线从 {from} 开始重建时反复失败，错误为 {error}",
                REBUILD_PENDING: "用户的时间线从 {from} 开始的重建尚未执行，因此用户看到的时间线可能已过时。",
                APP_VERSION_DIFFERS: "导出文件来自 GeoPulse {exporterVersion}，当前服务器运行 {importerVersion}。差异可能已在新版本中修复，也可能由新版本引入。",
                GPS_FILTERS_ACTIVE: "用户的位置来源启用了定位点筛选。被排除的点不在导出文件中，这可能导致停留或行程缺失。",
                WATER_DATASET_MISSING: "当前服务器没有加载水域数据，无法像用户的服务器那样检测船舶行程。",
                WATER_FLAGS_NOT_EXPORTED: "用户的服务器没有水域数据，因此也未运行船舶行程检测。",
                OLD_EXPORT_FORMAT: "此文件使用旧版导出格式 {version}，缺少毫秒级时间戳、匹配地点和手动修正，因此可能出现少量差异。",
                TIMELINE_CONFIG_NOT_APPLIED: "导出文件中的时间线配置未应用，当前服务器使用了自己的配置。",
                TIMELINE_CONFIG_NOT_IN_EXPORT: "导出文件未包含用户的时间线配置，当前服务器使用了自己的配置。",
                EXISTING_DATA_KEPT: "保留了现有数据，其他 GPS 点可能改变重建后的时间线。",
                TIMELINE_NOT_IN_EXPORT: "导出文件未包含用户的时间线。请让用户勾选「包含我的当前时间线」后重新导出。"
            }
        },
        invalidFileSummary: "无效的文件",
        invalidFileDetail: "请选择 ZIP 文件",
        importSuccessSummary: "导入成功",
        importSuccessDetail: "调试数据已导入，时间线已重建。详情见下方报告。",
        importFailedFallback: "无法导入调试数据"
    },
    exportImportPage: {
        pageTitle: "数据导入与导出",
        pageDescription: "导出 GeoPulse 数据以备份，或导入先前导出的数据",
        demoDisabledMessage: "为保护共享的演示数据，演示模式下无法导入或导出数据。",
        bannerTitle: "数据安全与隐私",
        bannerDescription: "服务器不会保留导出文件，请在离开此页前下载文件。",
        exportTab: "导出数据",
        importTab: "导入数据"
    },
    dataTypes: {
        rawgps: {
            label: "原始 GPS 数据",
            description: "所有位置点及其时间戳和定位误差"
        },
        favorites: {
            label: "收藏地点",
            description: "你收藏的地点和区域"
        },
        reversegeocodinglocation: {
            label: "反向地理编码数据",
            description: "位置对应的地址和地点名称"
        },
        locationsources: {
            label: "位置来源",
            description: "已配置的 GPS 追踪应用和接口地址"
        },
        userinfo: {
            label: "用户信息",
            description: "你的个人资料和偏好设置，敏感数据除外"
        },
        timelinelabels: {
            label: "时间线标签",
            description: "时间线标签及其日期范围"
        },
        timelineoverrides: {
            label: "时间线修正",
            description: "手动修正的行程类型和数据缺口"
        },
        timelineedits: {
            label: "时间线编辑",
            description: "可撤销的地点、时间、出行方式和时间线结构修改"
        },
        tripworkspace: {
            label: "行程计划",
            description: "行程、计划、笔记和协作者"
        },
        notificationtemplates: {
            label: "通知模板",
            description: "地理围栏通知所用的模板"
        },
        geofencing: {
            label: "地理围栏",
            description: "地理围栏规则和监测对象"
        },
        notes: {
            label: "时间线笔记",
            description: "关联到时间线地点和日期的 GeoPulse 笔记"
        },
        weathersamples: {
            label: "天气观测记录",
            description: "选定日期范围内获取的天气观测记录"
        },
        mapmatching: {
            label: "地图匹配",
            description: "已完成地图匹配的时间线行程路线缓存"
        },
        friends: {
            label: "好友",
            description: "有方向的好友关系"
        },
        friendpermissions: {
            label: "好友权限",
            description: "时间线和实时位置的分享权限"
        },
        statistics: {
            label: "统计信息"
        },
        mapmatchingImportDescription: "恢复已完成地图匹配的时间线行程路线缓存"
    },
    jobStatus: {
        processing: "处理中",
        validating: "验证中",
        completed: "已完成",
        failed: "失败"
    },
    exportTab: {
        formatSectionTitle: "导出格式",
        deselectAll: "取消全选",
        selectAll: "全选",
        selectDataTypesTitle: "选择数据类型",
        timelineDataLabel: "时间线数据",
        timelineDataNote: "导入后将根据 GPS 数据自动重建",
        owntracksOptionsTitle: "OwnTracks 导出选项",
        owntracksOcatLabel: "官方 Recorder 格式",
        owntracksOcatDescription: "包含 count 和 locations 字段的 JSON 对象，兼容 OwnTracks ocat 导出",
        owntracksArrayLabel: "旧版 GeoPulse 数组",
        owntracksArrayDescription: "由位置消息组成的 JSON 数组，兼容旧版 GeoPulse 的 OwnTracks 导出",
        gpxOptionsTitle: "GPX 导出选项",
        gpxSingleLabel: "单个 GPX 文件",
        gpxSingleDescription: "一个 GPX 文件，包含所有轨迹（原始 GPS 轨迹和时间线行程）及航点（时间线中的停留）",
        gpxZipLabel: "ZIP 压缩包",
        gpxZipDescription: "将多个 GPX 文件打包为 ZIP 压缩包",
        gpxZipGroupingTitle: "ZIP 文件分组方式",
        gpxGroupIndividualLabel: "分别保存（每次行程或停留一个文件）",
        gpxGroupIndividualDescription: "为每次行程和停留各创建一个 GPX 文件",
        gpxGroupDailyLabel: "按天分组（每天一个文件）",
        gpxGroupDailyDescription: "每天的所有行程和停留合并为一个 GPX 文件",
        csvDocsTitle: "CSV 导出格式",
        csvInfoText: "CSV 文件包含所选日期范围内的所有 GPS 点，每个点包含以下字段。",
        csvFieldsTitle: "导出字段",
        csvFields: {
            timestamp: "ISO-8601 格式（UTC）",
            latitude: "十进制度数",
            longitude: "十进制度数",
            accuracy: "GPS 定位误差，单位为米（如有）",
            velocity: "速度，单位为公里/小时（如有）",
            altitude: "海拔，单位为米（如有）",
            battery: "电量百分比（如有）",
            deviceId: "设备标识符（如有）",
            sourceType: "数据来源类型"
        },
        csvExampleTitle: "输出示例",
        formats: {
            geopulse: {
                label: "GeoPulse",
                description: "GeoPulse 原生格式，支持全部数据类型"
            },
            owntracks: {
                label: "OwnTracks",
                description: "兼容 OwnTracks 格式，仅支持 GPS 数据"
            },
            geojson: {
                label: "GeoJSON",
                description: "兼容 GIS 工具的标准 GeoJSON 格式，仅支持 GPS 数据"
            },
            gpx: {
                label: "GPX",
                description: "包含轨迹和航点的 GPS 交换格式，兼容 GPXSee、QGIS 和 Garmin"
            },
            csv: {
                label: "CSV",
                description: "兼容 Excel 和数据分析工具的逗号分隔值格式，仅支持 GPS 数据"
            }
        },
        dateRangeTitle: "日期范围",
        startDateLabel: "开始日期",
        startDatePlaceholder: "选择开始日期",
        endDateLabel: "结束日期",
        endDatePlaceholder: "选择结束日期",
        last30Days: "最近 30 天",
        last90Days: "最近 90 天",
        lastYear: "过去一年",
        allTime: "全部时间",
        startExport: "开始导出",
        demoDisabledNote: "演示数据会定期重置，演示模式下无法导出。",
        currentJobTitle: "当前导出任务",
        formatLabel: "格式",
        dataTypesLabel: "数据类型",
        dateRangeLabel: "日期范围",
        progressLabel: "进度",
        download: "下载",
        downloadsDisabledDemo: "演示模式下无法下载",
        downloadExportTooltip: "下载导出文件",
        delete: "删除",
        deleteExportsDisabledDemo: "演示模式下无法删除导出任务",
        deleteExportTooltip: "删除导出任务",
        exportHistoryTitle: "最近的导出",
        refresh: "刷新",
        noExportJobs: "暂无导出任务",
        exportExpired: "导出文件已过期",
        allTimeLabel: "全部时间",
        demoToast: "演示模式下无法导出。",
        exportStartedSummary: "已开始导出",
        exportStartedDetail: "导出任务已创建，正在处理",
        exportFailedSummary: "导出失败",
        exportFailedFallback: "无法启动导出任务",
        downloadStartedSummary: "下载已开始",
        downloadStartedDetail: "导出文件已开始下载",
        downloadFailedSummary: "下载失败",
        downloadFailedFallback: "无法下载导出文件",
        deleteExportConfirmMessage: "确定要删除此导出任务吗？此操作无法撤销。",
        deleteExportConfirmHeader: "删除导出任务",
        exportDeletedSummary: "导出任务已删除",
        exportDeletedDetail: "已成功删除导出任务",
        deleteFailedSummary: "删除失败",
        deleteFailedFallback: "无法删除导出任务",
        refreshFailedSummary: "刷新失败",
        refreshFailedFallback: "无法刷新导出任务",
        exportNotReady: "导出文件尚未准备好，暂时无法下载",
        exportFailedUnknown: "导出失败，原因未知。",
        jobRunningHint: "导出期间可以离开此页。文件准备好后，你会收到通知。",
        availableUntil: "可下载至 {time}",
        downloaded: "已下载",
        sizeLabel: "大小"
    },
    importTab: {
        formatSectionTitle: "导入格式",
        selectFileTitle: "选择要导入的文件",
        dropZonePrompt: "点击选择文件，或将文件拖到此处",
        removeFile: "移除",
        uploadDisabledDemo: "演示模式下无法上传文件。",
        onlyFormatFilesSupported: "仅支持 {label} 文件（{extensions}）",
        csvSpecTitle: "CSV 格式规范",
        downloadCsvTemplate: "下载 CSV 模板",
        requiredFieldsTitle: "必填字段",
        requiredFields: {
            timestamp: "ISO-8601 格式（例如 2024-01-15T10:30:00Z）",
            latitude: "十进制度数，范围为 -90 至 90",
            longitude: "十进制度数，范围为 -180 至 180"
        },
        optionalFieldsTitle: "可选字段",
        optionalFields: {
            accuracy: "GPS 定位误差，单位为米",
            velocity: "速度，单位为公里/小时",
            altitude: "海拔，单位为米",
            battery: "电量百分比（0 至 100）",
            deviceId: "设备标识符（文本）",
            sourceType: "数据来源（CSV、OWNTRACKS、GPX、GEOJSON 等）"
        },
        exampleCsvTitle: "CSV 示例",
        formatRulesTitle: "格式要求",
        formatRules: {
            utf8: "必须使用 UTF-8 编码",
            header: "必须包含表头行，字段名区分大小写",
            emptyOptional: "可选字段无值时留空（例如 ,,）",
            decimalSeparator: "以英文句点（.）作为小数点",
            timestampsUtc: "时间戳必须使用 UTC，以 Z 结尾"
        },
        importOptionsTitle: "导入选项",
        dataTypesToImport: "要导入的数据类型",
        deselectAll: "取消全选",
        selectAll: "全选",
        timelineDataLabel: "时间线数据",
        timelineDataNote: "导入后将根据 GPS 数据自动重建",
        importOnlyWithinRange: "仅导入日期范围内的数据",
        replaceExistingData: "导入前替换现有数据",
        replaceExistingDataDescription: "启用后，导入前会删除导入日期范围内的现有 GPS 数据。收藏地点不受日期范围限制。如果导入内容包含收藏地点，所有现有收藏地点都会被压缩包内的收藏地点替换。随后会完整重建时间线。",
        replaceWarningLabel: "警告",
        replaceWarningText: "此操作将永久删除导入日期范围内的 GPS 和时间线数据。如果导入内容包含收藏地点，还将永久删除所有现有收藏地点。随后会完整重建时间线。请根据需要提前备份。",
        startDateLabel: "开始日期",
        startDatePlaceholder: "选择开始日期",
        endDateLabel: "结束日期",
        endDatePlaceholder: "选择结束日期",
        startImport: "开始导入",
        demoDisabledNote: "上传数据会修改共享的演示数据，因此演示模式下无法导入。",
        importDurationNote: "导入可能需要几分钟，具体取决于文件大小。",
        uploadingFileTitle: "正在上传文件",
        uploading: "上传中",
        fileLabel: "文件",
        unknownFile: "未知文件",
        sizeLabel: "大小",
        uploadProgressLabel: "上传进度",
        largeFileNote: "大文件上传可能需要几分钟，具体取决于网络速度。",
        currentJobTitle: "当前导入任务",
        dataTypesLabel: "数据类型",
        progressLabel: "进度",
        timelineGenerationTitle: "时间线生成",
        initializingTimeline: "正在准备生成时间线…",
        viewDetailedProgress: "查看详细进度",
        importSummaryTitle: "导入摘要",
        summaryGpsPoints: "{count} 个 GPS 点",
        summaryTimelineItems: "{count} 条时间线记录",
        summaryFavoriteLocations: "{count} 个收藏地点",
        summaryLocationSources: "{count} 个位置来源",
        importHistoryTitle: "导入历史",
        refresh: "刷新",
        noImportJobs: "暂无导入任务",
        formats: {
            geopulse: {
                label: "GeoPulse",
                description: "从 GeoPulse 文件导入"
            },
            owntracks: {
                label: "OwnTracks",
                description: "从 OwnTracks 文件导入"
            },
            googleTimeline: {
                label: "Google 时间线",
                description: "从 Google 时间线或 Google Takeout 的 JSON 文件导入"
            },
            gpx: {
                label: "GPX",
                description: "从 GPX 轨迹文件、路线文件或包含多个 GPX 文件的 ZIP 压缩包导入"
            },
            geojson: {
                label: "GeoJSON",
                description: "从 GeoJSON 文件导入 Point 或 LineString 要素"
            },
            csv: {
                label: "CSV",
                description: "从 CSV 文件导入"
            }
        },
        formatInfoMessages: {
            owntracks: "将 OwnTracks 的 GPS 位置数据添加到 GeoPulse 时间线。",
            googleTimeline: "从 Google Takeout 导出的数据中导入 GPS 位置和活动。",
            gpx: "将 GPX 轨迹和路线数据添加到 GeoPulse 时间线。可以导入单个 GPX 文件，或包含多个 GPX 文件的 ZIP 压缩包。",
            geojson: "将 GeoJSON 中 Point 和 LineString 要素的位置数据添加到 GeoPulse 时间线。",
            csv: "将符合格式要求的 CSV 文件中的 GPS 位置数据添加到 GeoPulse 时间线。请使用下方的 CSV 模板准备数据。",
            fallback: "将 {label} 文件中的位置数据添加到 GeoPulse 时间线。"
        },
        uploadPhase: {
            starting: "正在开始上传…",
            uploading: "正在将文件上传到服务器…",
            inProgress: "正在上传…",
            finalizing: "即将完成上传…",
            processing: "正在处理文件…"
        },
        progressPhase: {
            validating: "正在验证文件格式…",
            parsing: "正在解析导入数据…",
            importingBulk: "正在批量导入 GPS 点…",
            importingMerge: "正在导入 GPS 点并与现有数据合并…",
            generatingTimeline: "正在生成时间线…"
        },
        timelineGenerationCompleted: "时间线生成完成",
        gpsPointsLoaded: "已加载 {loaded} / {total} 个 GPS 点",
        locationsGeocoded: "已为 {resolved} / {total} 个位置解析地址{pending}",
        pendingSuffix: "（{count} 个待处理）",
        demoToast: "演示模式下无法导入。",
        importInProgressSummary: "正在导入",
        importStartedSummary: "已开始导入",
        importStartedDetail: "导入任务已创建，正在处理",
        importFailedSummary: "导入失败",
        importFailedFallback: "无法启动导入任务",
        refreshFailedSummary: "刷新失败",
        refreshFailedFallback: "无法刷新导入任务",
        templateDownloadedSummary: "模板已下载",
        templateDownloadedDetail: "CSV 模板已成功下载",
        templateDownloadFailedSummary: "下载失败",
        templateDownloadFailedFallback: "无法下载 CSV 模板",
        invalidFileSummary: "不支持的文件",
        invalidFileDetail: "{name} 不是 {label} 文件。支持的扩展名为 {extensions}",
        importFailedUnknown: "导入失败，原因未知。",
        jobRunningHint: "导入期间可以离开此页。导入完成后，你会收到通知。",
        viewTimeline: "查看时间线"
    }
}
