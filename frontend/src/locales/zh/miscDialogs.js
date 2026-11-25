export default {
    timelineRegeneration: {
        header: "重新生成时间线",
        completedMessage: "时间线生成成功！",
        gpsPointsLoaded: "已加载 {loaded} / {total} 个 GPS 定位点",
        pointsProcessed: "已处理 {processed} / {total} 个定位点",
        locationsGeocoded: "已对 {resolved} / {total} 个地点完成反向地理编码",
        fromFavorites: "来自收藏地点：{count}",
        fromCache: "来自缓存：{count}",
        fromExternalApi: "来自外部 API：{count}",
        pending: "待处理：{count}",
        viewDetailedProgress: "查看详细进度",
        fallbackNote: "根据数据量，此过程可能需要 5–15 秒或更长时间。生成期间暂时无法查看时间线。",
        pleaseWait: "请稍候…",
        titles: {
            favorite: "正在添加收藏地点并重新生成时间线",
            favoriteDelete: "正在删除收藏地点并重新生成时间线",
            preferences: "正在应用设置并重新生成时间线",
            classification: "正在更新行程分类",
            reconstruction: "正在补充时间线缺失数据",
            general: "正在重新生成时间线"
        },
        messages: {
            favorite: "正在添加收藏地点并重新生成整个时间线，使时间线反映此更改。",
            favoriteDelete: "正在移除收藏地点并重新生成整个时间线，使时间线反映此更改。",
            preferences: "正在应用新设置，并按新设置重新生成整个时间线。",
            classification: "正在按新的速度阈值重新判定现有行程的出行方式。更新后会保留原有的时间线结构。",
            reconstruction: "正在应用根据停留和行程生成的 GPS 定位点，然后更新时间线中受影响的时段。现有时间线数据会保留。",
            general: "正在使用 GPS 数据重新生成整个时间线，使其反映所有可用的位置信息。"
        }
    },
    stayDetails: {
        titleWithLocation: "停留详情 · {location}",
        unknownLocation: "位置未知",
        mapSectionTitle: "位置地图",
        timingTitle: "时间",
        start: "开始：",
        end: "结束：",
        duration: "时长：",
        coordinatesTitle: "坐标",
        latitude: "纬度：",
        longitude: "经度：",
        coordinates: "坐标：",
        notAvailable: "无数据",
        close: "关闭",
        toasts: {
            copiedSummary: "已复制",
            copiedDetail: "坐标已复制到剪贴板",
            copyFailedSummary: "复制失败",
            copyFailedDetail: "无法复制坐标"
        }
    },
    locationLookup: {
        header: "此处附近的到访记录",
        withinRadius: "{radius} 米范围内",
        checkingStays: "正在查询你的停留记录…",
        tryAgain: "重试",
        hasVisits: "你曾到访此地点。",
        recordedStayFallback: "停留记录",
        placeDetails: "地点详情",
        visitCount: "{count} 次到访",
        firstVisit: "首次到访：{date}",
        lastVisit: "最近到访：{date}",
        openVisitDayTooltip: "在时间线中打开到访当天",
        savedPlacesHeading: "此处已保存的地点",
        unnamedFavorite: "未命名的收藏地点",
        noVisitFound: "此处 {radius} 米范围内没有到访记录。",
        closestStaysHeading: "最近的停留地点",
        unknownLocation: "位置未知",
        noRecordedStays: "未找到停留记录。",
        close: "关闭"
    },
    timelineLabelForm: {
        nameLabel: "标签名称 *",
        namePlaceholder: "例如：西班牙度假、纽约出差",
        dateRangeLabel: "日期范围 *",
        dateRangePlaceholder: "选择开始和结束日期",
        colorLabel: "颜色",
        colorPreview: "预览",
        randomButton: "随机",
        showAsPresetLabel: "显示为日期预设",
        showAsPresetHint: "启用后，此标签会显示在日期选择器的预设下拉列表中。",
        nameRequired: "请输入标签名称",
        dateRangeRequired: "请选择日期范围",
        cancel: "取消"
    },
    createTimelineLabel: {
        header: "创建时间线标签",
        createButton: "创建",
        overlap: {
            message: "此标签和以下标签的时段重叠：{names}。仍要创建吗？",
            header: "标签时段重叠",
            acceptLabel: "仍然创建",
            rejectLabel: "取消"
        },
        toasts: {
            checkOverlapsFailedFallback: "无法检查标签时段是否重叠",
            createdSummary: "已创建",
            createdDetail: "时间线标签已创建",
            createFailedFallback: "无法创建时间线标签"
        }
    },
    editTimelineLabel: {
        header: "编辑时间线标签",
        readOnlyHeader: "查看时间线标签（只读）",
        activeOwnTracksTitle: "OwnTracks 标签生效中",
        activeOwnTracksMessage: "此标签当前由 OwnTracks 管理，生效期间无法编辑。可在 OwnTracks 中切换标签，结束当前标签后再编辑。",
        updateButton: "更新",
        toasts: {
            updatedSummary: "已更新",
            updatedDetail: "时间线标签已更新",
            updateFailedFallback: "无法更新时间线标签"
        }
    },
    bulkEdit: {
        header: "批量编辑 {count} 个{itemType}",
        editingPrefix: "正在编辑",
        fieldsHeading: "选择要更新的字段",
        updateCity: "更新城市",
        updateCountry: "更新国家",
        cityPlaceholder: "输入城市名称",
        countryPlaceholder: "输入国家名称",
        cityRequired: "请输入城市",
        countryRequired: "请输入国家",
        selectAtLeastOneField: "请至少选择一个要更新的字段",
        typoWarning: {
            header: "可能有拼写错误",
            notFoundMessage: "在现有数据中找不到以下值：",
            didYouMean: "你想输入的是：",
            continuePrompt: "要使用这些值继续吗？",
            continueAnyway: "仍然继续"
        },
        fields: {
            city: "城市",
            country: "国家"
        },
        updating: "正在更新…",
        updateButton: "更新 {count} 项",
        toasts: {
            queuedDetail: "已将 {count} 项更改加入待处理列表。选择「保存全部」即可应用。",
            completeSummary: "批量更新完成",
            partialDetail: "共 {total} 项，更新了 {success} 项（{failed} 项失败）",
            successDetail: "已成功更新 {count} 个{itemType}",
            failedSummary: "更新失败",
            failedFallback: "无法更新{itemType}"
        }
    },
    bulkSaveConfirm: {
        updatesItem: "将编辑 {count} 个收藏地点",
        deletesItem: "将删除 {count} 个收藏地点",
        header: "确认批量保存",
        summaryTitle: "应用 {count} 项待处理的更改：",
        pointsItem: "将添加 {count} 个收藏点",
        areasItem: "将添加 {count} 个收藏区域",
        infoMessage: "所有更改将一并应用，随后重新生成一次时间线。",
        confirm: "确认"
    }
}
