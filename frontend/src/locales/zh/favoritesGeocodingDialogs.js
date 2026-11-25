export default {
    areaDrawing: {
        done: "完成",
        adjustInstruction: "拖动顶点可移动它，拖动边可添加顶点并调整形状。选择「完成」应用更改，或选择「取消」放弃更改。",
        adjust: "调整顶点",
        corner: "顶点 {number}。拖动或使用方向键移动。按 + 可拆分下一条边，按住 Shift 可加快移动。",
        title: "绘制收藏区域",
        rectangle: "矩形",
        polygon: "多边形",
        undo: "撤销上一个顶点",
        finish: "完成绘制",
        rectangleInstruction: "拖动以绘制矩形。",
        polygonInstruction: "点击或轻触以添加顶点。拖动地图可平移，拖动顶点可调整位置。选择第一个或最后一个顶点，或选择「完成绘制」，即可闭合图形。按 Esc 取消。",
        errors: {
            vertices: "请使用 3 至 500 个顶点。",
            coordinates: "请使用有效的经纬度坐标。",
            closed: "图形必须闭合。",
            crossing: "边线不得相交或重叠，图形面积必须大于零。",
            antimeridian: "暂不支持跨越 180° 经线的图形。"
        }
    },
    addFavorite: {
        locationNamePlaceholder: "地点名称",
        save: "保存"
    },
    editFavorite: {
        nameLabel: "名称",
        namePlaceholder: "输入地点名称",
        cityLabel: "城市",
        cityPlaceholder: "输入城市（可选）",
        countryLabel: "国家",
        countryPlaceholder: "输入国家（可选）",
        areaBoundariesTitle: "区域边界",
        drawing: "正在绘制…",
        redrawArea: "重新绘制区域",
        drawInstruction: "在地图上点击并拖动，绘制一个新的矩形区域",
        currentBoundsLabel: "当前边界：",
        neLabel: "东北角：",
        swLabel: "西南角：",
        save: "保存"
    },
    reconcile: {
        selectProvider: "选择服务提供商",
        providerPlaceholder: "选择反向地理编码服务提供商",
        primaryTag: "主要",
        fallbackTag: "备用",
        providerHint: "将使用此服务提供商获取新的反向地理编码数据",
        detailsTitle: "重新查询详情",
        filteredBadge: "（已筛选）",
        allBadge: "（全部）",
        activeFiltersLabel: "当前筛选条件：",
        searchFilter: "搜索：「{search}」",
        actionLabel: "操作：",
        importantLabel: "重要提示：",
        originalKeptOnFailure: "重新查询失败时会保留原数据",
        cannotBeUndone: "此操作无法撤销",
        progressTitle: "重新查询进度",
        processedLabel: "已处理：",
        successfulLabel: "成功：",
        errorsLabel: "错误：",
        completedSuccessfully: "重新查询完成！",
        completedWithErrors: "重新查询完成，有 {count} 项出错。",
        itemsReconciledSuccessfully: "已成功重新查询 {count} 项。",
        jobFailed: "重新查询任务失败",
        unexpectedError: "发生意外错误",
        reconcileButton: "重新查询",
        favoritesToReconcileLabel: "待重新查询的收藏地点：",
        typeFilter: "类型：{type}",
        favoriteActionValue: "根据服务提供商返回的结果更新城市和国家",
        favoriteWarnings: {
            cityCountryOnly: "此操作只会更新城市和国家字段",
            nameUnchanged: "地点名称和坐标保持不变",
            areaCenterPoint: "收藏区域将使用其中心点进行反向地理编码"
        },
        favorite: {
            dialogTitleFiltered: "重新查询筛选后的 {count} 个收藏地点",
            dialogTitleAll: "重新查询全部 {count} 个收藏地点",
            dialogTitleSingle: "重新查询收藏地点",
            dialogTitleMultiple: "重新查询所选的 {count} 个收藏地点"
        },
        geocoding: {
            providerSwitchHint: "更换服务提供商后，可用「重新查询所选项」或「重新查询全部」通过所选服务提供商刷新旧记录。",
            resultsToReconcileLabel: "待重新查询的结果：",
            providerFilter: "服务提供商：{provider}",
            actionValue: "从所选服务提供商获取最新数据",
            warnings: {
                displayNameCityCountry: "此操作会更新显示名称、城市和国家字段",
                syncedAcrossStays: "更改将同步至时间线中的所有相关停留记录",
                cachedEntriesNotAuto: "在设置中更换服务提供商后，已有缓存记录需要手动刷新"
            },
            dialogTitleFiltered: "重新查询筛选后的 {count} 条结果",
            dialogTitleAll: "重新查询全部 {count} 条结果",
            dialogTitleSingle: "重新查询反向地理编码结果",
            dialogTitleMultiple: "重新查询所选的 {count} 条结果"
        }
    },
    geocodingEdit: {
        header: "编辑反向地理编码结果",
        mapLabel: "位置地图",
        mapHint: "此地图为只读，显示反向地理编码对应的位置",
        displayNameLabel: "显示名称",
        displayNamePlaceholder: "输入地点的显示名称",
        displayNameHint: "此地点在界面中显示的主要名称",
        cityLabel: "城市",
        cityPlaceholder: "输入城市名称",
        cityHint: "城市名称（可选）",
        countryLabel: "国家",
        countryPlaceholder: "输入国家名称",
        countryHint: "国家名称（可选）",
        providerLabel: "服务提供商：",
        coordinatesLabel: "坐标：",
        noteLabel: "说明：",
        syncNoteMessage: "更改将同步至所有使用此反向地理编码结果的时间线停留记录。",
        saveChanges: "保存更改"
    }
}
