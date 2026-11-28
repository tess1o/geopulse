export default {
    edit: {
        previewChanged: "时间线已更新。请查看最新预览后再保存。",
        dismissTransitionHint: "移除此过渡标记，同时保留两段停留。你可以在「时间线更改」中撤销。",
        reviewChanges: "查看更改",
        reviewFirst: "第 1 步（共 2 步）· 修改后继续查看预览。",
        reviewReady: "第 2 步（共 2 步）· 检查下方结果，然后保存更改。",
        uncertainTime: "拨钟时此时间会被跳过或重复出现。请选择一个明确的时间。",
        validTimesRequired: "请输入完整、有效的日期和时间。",
        placeRequired: "请选择地点，再查看预览。",
        placeNameRequired: "请为此地点命名后再保存。",
        unchanged: "请修改一项内容后再保存。",
        allVisits: "更改此地点的所有到访记录",
        changeCount: "{count} 项更改",
        yourChanges: "你的更改",
        previousChanges: "以往的更改",
        retry: "重试",
        previousUnavailable: "无法获取原值",
        noLabel: "无到访标签",
        relatedChange: "影响此时段的相关更改",
        undoHint: "撤销会移除此更改，并根据剩余更改重建时间线。已被替换的更改需要手动恢复。",
        previousEvent: "上一事件",
        nextEvent: "下一事件",
        removeLabel: "移除标签",
        tripStart: "行程从哪里开始？",
        tripEnd: "行程在哪里结束？",
        before: "更改前",
        after: "更改后（请在保存前确认）",
        viewChange: "查看更改",
        saveChanges: "保存更改",
        discardChanges: "要放弃未保存的更改吗？",
        labelRequired: "请输入到访标签，或选择「移除标签」。",
        invalidTimes: "结束时间必须晚于开始时间。",
        chooseResolution: "请选择如何填补移除后留下的时段。",
        endpointsRequired: "请补全缺失的起点和终点，再查看预览。",
        undone: "已撤销更改",
        redone: "已恢复更改",
        updating: "正在更新时间线…",
        updateFailed: "更改已保存，但时间线更新失败。打开时间线任务可查看失败原因。",
        resultHint: "受影响的时段已更新。移除或合并的事件仍可在更改历史中查看。",
        visit: "编辑停留…",
        trip: "编辑行程…",
        gap: "填补数据缺失…",
        history: "时间线更改",
        editedCount: "{count} 项时间线更改",
        details: "详情",
        renameUnlinked: "此地点尚未关联收藏地点或地理编码记录。可在「地点」中为本次停留命名，也可关联地点以统一重命名。",
        moreChanges: "更多更改",
        visitLabel: "到访标签",
        visitLabelPlaceholder: "例如：和小李吃晚饭",
        visitLabelHint: "此标签仅用于本次到访。其他地方仍会显示地点名称「{location}」。",
        locationHint: "本次到访发生的地点。到访记录按地点分组和统计。",
        gapStayQuestion: "这段记录缺失期间，你在哪里？",
        splitStayQuestion: "此时间之后，你在哪里？",
        splitStayHint: "此次到访将拆分为两次。前半段仍在 {location}。",
        place: {
            useLinkedPlace: "使用关联地点：{name}",
            customName: "地点名称",
            customNamePlaceholder: "为此地点命名，例如「停车场」",
            current: "当前地点",
            selected: "所选地点",
            unnamed: "未命名地点",
            suggestions: "建议",
            beforeGap: "记录缺失前的地点",
            afterGap: "记录缺失后的地点",
            search: "搜索你的地点",
            searchPlaceholder: "收藏过或到访过的地点",
            pickOnMap: "在地图上选择",
            hideMap: "隐藏地图",
            mapHint: "点击地图或拖动标记，选择当时所在的位置。",
            newPlace: "新地点",
            newPlaceName: "新地点名称",
            newPlacePlaceholder: "例如：健身房",
            useNewPlace: "使用这个新地点",
            usingNewPlace: "已选中此新地点",
            referenceCaption: "当前地点",
            splitReferenceCaption: "当前到访地点",
            resolving: "正在查找附近的地点…",
            lookupFailed: "无法查找附近的地点。",
            searchFailed: "无法搜索你的地点。",
            source: {
                favorite: "收藏地点",
                known: "到访过的地点",
                custom: "新地点"
            }
        },
        eventType: {
            STAY: "停留",
            TRIP: "行程",
            DATA_GAP: "数据缺失"
        },
        movementType: "出行方式",
        start: "开始",
        end: "结束",
        splitAt: "拆分时间",
        splitAtRange: "请选择 {start} 之后、{end} 之前的停留时间。",
        sharedBoundaryHint: "调整起止时间也会改变相邻事件的时间，以保持时间线连续。",
        mergeDirection: "合并对象",
        removeResolution: "填补移除后的时段",
        preview: "预览",
        apply: "应用",
        merge: {
            trip: {
                explanation: "如果一次短暂停留把一段行程拆成了两段，可用此操作合并两段出行方式相同的行程。",
                previous: "与上一段行程合并",
                next: "与下一段行程合并",
                removes: "将移除两段行程之间在 {name} 持续 {duration} 的停留。"
            },
            stay: {
                explanation: "如果一次短途行程把一次到访拆成了两次，可用此操作合并在同一地点的两次到访。",
                previous: "与上一次停留合并",
                next: "与下一次停留合并",
                removes: "将移除两次停留之间持续 {duration} 的{name}行程。"
            },
            adjacent: "两段事件相邻，可直接合并。"
        },
        loadingHistory: "正在加载时间线更改…",
        noHistory: "暂无时间线更改。",
        historyFailed: "无法加载时间线更改。",
        revert: "撤销",
        restore: "重做",
        replace: "替换",
        undo: "撤销",
        undoDependents: {
            header: "也要撤销后续更改吗？",
            message: "有 {count} 项后续更改依赖此更改，将一并撤销。"
        },
        undoFailed: "无法撤销此时间线更改。",
        previewFailed: "无法预览此修正。",
        saveFailed: "无法保存此修正。",
        saved: "更改已保存",
        savedDetail: "时间线已更新。",
        header: {
            stay: "编辑停留",
            trip: "编辑行程",
            dataGap: "填补数据缺失"
        },
        action: {
            location: "地点",
            visitLabel: "到访标签",
            renameLocation: "重命名地点及其所有记录",
            times: "时间",
            movement: "出行方式",
            splitStay: "拆分停留",
            splitTrip: "添加停留点",
            merge: "合并",
            remove: "移除",
            gapToStay: "转为停留",
            gapToTrip: "转为行程",
            rebuildInterval: "重建此时段",
            editRoute: "编辑行程路线"
        },
        actionHint: {
            dismissTransition: "移除此标记，同时保留两段停留",
            rebuildInterval: "描述此时段内发生的活动，保留其余时段",
            editRoute: "拖动路线或添加定位点，保留行程的起止时间",
            location: "本次到访发生的地点",
            visitLabel: "本次到访专用的标题，例如「和小李吃晚饭」",
            renameLocation: "更新此地点在所有到访记录中的名称",
            splitTrip: "在路线图上选择停留点",
            mergeTrip: "合并被短暂停留分开的行程",
            mergeStay: "合并被短途行程分开的到访",
            times: "活动的开始和结束时间",
            movement: "此次行程的出行方式",
            splitStay: "你在停留期间去了别处",
            remove: "删除这项未发生的活动，并选择如何填补其时段",
            gapToStay: "记录缺失期间，你一直在同一个地方",
            gapToTrip: "记录缺失期间，你正在出行"
        },
        resolution: {
            merge: "合并兼容的相邻事件",
            previous: "延长上一事件",
            next: "延长下一事件",
            gap: "保留数据缺失",
            dismiss: "移除未记录的过渡"
        },
        state: {
            inEffect: "已生效",
            undone: "已撤销",
            replaced: "已替换"
        },
        summary: {
            placeStay: "已将 {period} 填为在 {place} 的停留",
            placeTrip: "已将 {period} 填为{type}行程",
            placeTripPlaces: "已将 {period} 填为从 {from} 前往 {to} 的{type}行程",
            clear: "已移除 {period} 的{event}，并按以下方式处理：{resolution}",
            resize: "已将时间改为 {period}",
            changed: "已更改 {period} 的时间线",
            setPlace: "已将地点改为 {place}",
            setTripType: "已将出行方式改为{type}",
            setLabel: "已将到访标签设为「{label}」",
            clearLabel: "已清除到访标签",
            setRoute: "已更改 {period} 的行程路线",
            merge: "已将 {period} 内的事件合并为一条"
        },
        eventName: {
            stay: "停留",
            trip: "行程",
            gap: "数据缺失"
        },
        now: "现在",
        replacedBy: "已被后续更改替换。",
        dormant: "此更改未生效，因为对应事件已被后续修正移除。"
    },
    locationEdit: {
        editFavoriteHeader: "编辑收藏地点",
        renameStayHeader: "重命名停留地点",
        renameStayMessage: "重命名「{name}」会更新此处的所有停留记录。继续吗？"
    },
    notes: {
        openSingle: "打开笔记",
        openMultiple: "打开 {count} 条笔记"
    },
    photos: {
        openSingle: "打开照片",
        openMultiple: "打开 {count} 张照片",
        fallbackAlt: "照片"
    },
    dataGap: {
        subtitle: "数据缺失 · 活动未知",
        endTimeLabel: "结束时间：",
        durationLabel: "时长：",
        totalDuration: "总时长：",
        onThisDay: "当日时长：",
        convertTooltip: "将这段数据缺失转为停留",
        convertButton: "转为停留"
    },
    overnight: {
        continuedFrom: "从 {time} 持续至今"
    },
    localTime: {
        resolved: "本地时间采用 {zone} 时区，参考最近的城市 {city}（{country}，相距 {distance} 公里）",
        beyondMaxDistance: "无法确定本地时区。最近的城市 {city}（{country}）相距 {distance} 公里。当前显示个人设置中的时区。",
        noGeonamesData: "服务器尚未加载 GeoNames 城市数据，无法确定本地时区。当前显示个人设置中的时区。",
        invalidTimezone: "最近的城市 {city}（{country}）的时区无法识别。当前显示个人设置中的时区。",
        notProvided: "此事件的本地时区不可用。当前显示个人设置中的时区。"
    },
    card: {
        showGpsPoints: "显示 GPS 定位点",
        viewAllVisits: "查看此地点的所有到访记录",
        renamePlace: "重命名地点…",
        manageNoteSingle: "管理笔记…",
        manageNotesMultiple: "管理笔记（{count} 条）…",
        viewNoteSingle: "查看笔记…",
        viewNotesMultiple: "查看笔记（{count} 条）…",
        addNote: "添加笔记…",
        viewCityDetails: "查看 {city} 的详情",
        viewCountryDetails: "查看 {country} 的详情",
        exportGpx: "导出为 GPX",
        renameStayAria: "重命名停留地点",
        renameDisabledDemo: "演示模式下无法重命名",
        resetDisabledDemo: "演示模式下无法重置"
    },
    stay: {
        stayedAt: "停留于",
        manualIndicator: "（手动）",
        forDuration: "持续"
    },
    trip: {
        unrecordedTransition: "未记录的过渡",
        unrecordedHint: "在两次停留之间，你的位置发生了变化，但没有行程记录。出行时长未知。",
        unknownDuration: "未知",
        estimatedDistanceLabel: "估算距离：",
        edited: "已编辑",
        transitionTo: "前往",
        transitionToNewPlace: "前往新地点",
        durationLabel: "时长：",
        distanceLabel: "距离：",
        movementLabel: "出行方式：",
        editMovementType: "编辑出行方式",
        movementEditDisabledDemo: "演示模式下无法编辑出行方式",
        setMovementType: "设置出行方式",
        unrecognizedHint: "算法未能识别此行程的出行方式。",
        changeMovementType: "更改出行方式…",
        whyThisClassification: "为何归为此类？",
        splitTripWithStay: "插入停留以拆分行程…",
        mapMatchingDetails: "地图匹配详情…"
    },
    explanation: {
        trigger: {
            stay: "为什么判定为停留？查看 GPS 定位点和判定依据",
            trip: "为什么判定为行程？查看 GPS 定位点和判定依据",
            dataGap: "为什么显示数据缺失？查看前后的 GPS 定位点和判定依据"
        },
        toggle: {
            stay: "为何判为停留？",
            trip: "为何判为行程？",
            dataGap: "为何显示数据缺失？"
        },
        start: {
            FIRST_POINT: "从第一个 GPS 定位点开始。",
            AFTER_DATA_GAP: "从数据缺失后的第一个 GPS 定位点开始。",
            ARRIVAL: "从你停止移动的位置开始：{stopSeconds} 内有 {points} 个定位点的速度低于 {stopSpeedKmh} 公里/小时。",
            SPARSE_STOP: "从你停止移动的位置开始：下一个 GPS 定位点在 {gapSeconds} 后记录，仅相距 {distanceMeters}。",
            LEFT_STAY: "记录到距停留地点 {distanceMeters} 的定位点时开始，停留检测半径为 {radiusMeters}。",
            SHORT_STOP: "开头有一段持续 {stopSeconds} 的短暂停顿。停留至少需要 {minMinutes} 分钟，因此这段停顿计入行程。",
            WATER_TRANSITION_TO_WATER: "从你由陆地进入水域的位置开始。",
            WATER_TRANSITION_TO_LAND: "从你由水域回到陆地的位置开始。",
            INFERRED_DURING_GAP: "此处有 {gapSeconds} 没有 GPS 定位点，但前后两个定位点仅相距 {distanceMeters}，所以你可能一直停留在这里。",
            INFERRED_TRIP_DURING_GAP: "此处有 {gapSeconds} 没有 GPS 定位点，但前后两个定位点相距 {distanceMeters}，因此推断你在此期间出行。",
            CONTINUITY: "前后两次停留相距 {distanceMeters}，期间未检测到行程，因此添加此行程将它们连接起来。",
            NO_POINTS: "有 {gapSeconds} 未记录 GPS 定位点。中断超过 {thresholdSeconds} 时会显示为数据缺失。",
            NO_POINTS_SINCE_LAST: "最后一个 GPS 定位点之后暂无记录。中断超过 {thresholdSeconds} 时会显示为数据缺失，直到收到新的定位点。",
            STOPPED_BEFORE_GAP: "从你在数据缺失前停止移动的位置开始。",
            MANUAL_SPLIT: "你拆分了一段行程，添加了此次停留。",
            MANUAL_RECONSTRUCTION: "根据你在重建时输入的确切时段创建。",
            MANUAL_GAP_STAY: "你将一段数据缺失转换成了此次停留。"
        },
        note: {
            CONFIRMED: "符合停留条件：定位点至少有 {minMinutes} 分钟处于 {radiusMeters} 的半径内。",
            FAVORITE_AREA: "超出停留检测半径的定位点仍在同一个收藏区域内，因此也计入此次停留。",
            CONTINUED_OVER_GAP: "此次停留跨越了 {gapSeconds} 的数据缺失，因为前后的定位点在同一地点。",
            MERGED: "同一地点的 {stays} 次停留已合并为此次停留。",
            JOINED_SEGMENTS: "{segments} 段移动记录已合并为此行程。",
            SHORT_STOP: "包含持续 {stopSeconds} 的短暂停顿，未达到 {minMinutes} 分钟的最短停留时长。",
            MANUAL_SPLIT: "你手动拆分行程时更改了此记录。",
            MANUAL_RECONSTRUCTION: "根据你在重建时输入的确切时段创建。",
            MANUAL_GAP_STAY: "你手动转换数据缺失时延长了此次停留。"
        },
        end: {
            MOVED_AWAY: "记录到距停留地点 {distanceMeters} 的定位点时结束，停留检测半径为 {radiusMeters}。",
            ARRIVAL: "在你停止移动的位置结束：{stopSeconds} 内有 {points} 个定位点的速度低于 {stopSpeedKmh} 公里/小时。",
            SPARSE_STOP: "在你停止移动的位置结束：下一个 GPS 定位点在 {gapSeconds} 后记录，仅相距 {distanceMeters}。",
            DATA_GAP: "在一段持续 {gapSeconds} 的数据缺失处结束。",
            LAST_POINT: "在最后一个 GPS 定位点处结束，此活动可能仍在继续。",
            WATER_TRANSITION_TO_WATER: "在你由陆地进入水域的位置结束。",
            WATER_TRANSITION_TO_LAND: "在你由水域回到陆地的位置结束。",
            STOPPED_BEFORE_GAP: "在你于数据缺失前停止移动的位置结束。"
        }
    },
    explanationDialog: {
        header: {
            stay: "为何判为停留？",
            trip: "为何判为行程？",
            dataGap: "为何显示数据缺失？"
        },
        loading: "正在加载 GPS 定位点…",
        loadFailed: "无法加载判定依据。",
        period: "{start} 至 {end}（{duration}）",
        sections: {
            map: "GPS 定位点",
            steps: "判定依据",
            filtered: "已过滤的定位点",
            ingestion: "未保存的定位点"
        },
        legend: {
            used: "已采用",
            filtered: "已过滤（精度不足）",
            context: "前后定位点",
            deciding: "关键定位点",
            radius: "停留检测半径",
            anchor: "已保存的地点"
        },
        mapHint: "选择一个步骤，在地图上查看对应的定位点。选择定位点可查看详情。",
        noPoints: "此时段没有已保存的 GPS 定位点。",
        truncated: "此事件有 {total} 个 GPS 定位点，地图上显示其中 {shown} 个，包含所有已过滤的定位点和关键定位点。",
        steps: {
            any: {
                start: {
                    FIRST_POINT: "从 {time} 的第一个 GPS 定位点开始。",
                    AFTER_DATA_GAP: "从 {time} 开始，此时记录了数据中断后的第一个 GPS 定位点。",
                    ARRIVAL: "从你在 {time} 停止移动的位置开始。",
                    SPARSE_STOP: "从你在 {time} 停止移动的位置开始。停留期间没有 GPS 定位点，下一个点在 {gapSeconds} 后记录，仅相距 {distanceMeters}。",
                    LEFT_STAY: "从 {time} 开始，此时你距停留地点 {distanceMeters}，已超出 {radiusMeters} 的停留检测半径。",
                    SHORT_STOP: "从 {time} 到 {endTime} 有短暂停顿（{stopSeconds}）。停留至少需要 {minMinutes}，因此这段停顿计入行程。",
                    WATER_TRANSITION_TO_WATER: "从 {time} 开始，此时你由陆地进入水域。",
                    WATER_TRANSITION_TO_LAND: "从 {time} 开始，此时你由水域回到陆地。",
                    INFERRED_DURING_GAP: "有 {gapSeconds} 缺少 GPS 数据，但中断前后的定位点仅相距 {distanceMeters}，因此 GeoPulse 推断你一直停留在这里。",
                    INFERRED_TRIP_DURING_GAP: "有 {gapSeconds} 缺少 GPS 数据，中断前后的定位点相距 {distanceMeters}，因此推断你在此期间出行。",
                    CONTINUITY: "两次停留之间没有移动记录，但它们相距 {distanceMeters}，因此 GeoPulse 添加此行程将它们连接起来。此行程没有单独的 GPS 定位点。",
                    STOPPED_BEFORE_GAP: "你在 {time} 停在这里，随后 GPS 数据中断。恢复记录时你仍在此处。",
                    MANUAL_SPLIT: "你拆分行程时添加了此记录。",
                    MANUAL_RECONSTRUCTION: "根据你在重建时输入的确切时段创建。",
                    MANUAL_GAP_STAY: "你将一段数据缺失转换成了此次停留。",
                    NO_POINTS: "{time} 到 {endTime} 无 GPS 定位点（{gapSeconds}）。中断超过 {thresholdSeconds} 时会显示为数据缺失。",
                    NO_POINTS_SINCE_LAST: "从 {time} 起没有 GPS 定位点。中断超过 {thresholdSeconds} 时会显示为数据缺失，直到收到新的定位点。"
                },
                note: {
                    CONFIRMED: "从 {time} 起计为停留：当时你已在此地点 {radiusMeters} 的半径内停留了 {minMinutes}。",
                    FAVORITE_AREA: "有 {count} 个定位点超出停留检测半径，但它们位于同一收藏区域内，因此也计入此次停留。",
                    CONTINUED_OVER_GAP: "{time} 到 {endTime} 缺少 GPS 数据（{gapSeconds}），但中断前后你在同一地点，因此此次停留延续到中断之后。",
                    MERGED: "此地点有 {stays} 次停留，时间和位置接近，已合并为此次停留。",
                    JOINED_SEGMENTS: "{segments} 段连续行程已合并为此行程。",
                    SHORT_STOP: "包含持续 {stopSeconds} 的短暂停顿，未达到最短停留时长 {minMinutes}。",
                    MANUAL_SPLIT: "你手动拆分行程时更改了此记录。",
                    MANUAL_RECONSTRUCTION: "根据你在重建时输入的确切时段创建。",
                    MANUAL_GAP_STAY: "你手动转换数据缺失时延长了此次停留。"
                },
                end: {
                    MOVED_AWAY: "于 {time} 结束，此时你距离中心点 {distanceMeters}，超出 {radiusMeters} 的停留检测半径。",
                    ARRIVAL: "在你于 {time} 停止移动时结束。",
                    SPARSE_STOP: "在你于 {time} 停止移动的位置结束。下一个 GPS 定位点在 {gapSeconds} 后记录，仅相距 {distanceMeters}。",
                    DATA_GAP: "于 {time} 结束，此时 GPS 数据中断了 {gapSeconds}。",
                    LAST_POINT: "最新的 GPS 定位点记录于 {time}，此活动可能仍在继续。",
                    WATER_TRANSITION_TO_WATER: "于 {time} 结束，此时你由陆地进入水域。",
                    WATER_TRANSITION_TO_LAND: "于 {time} 结束，此时你由水域回到陆地。",
                    STOPPED_BEFORE_GAP: "于 {time} 结束，此时你停止移动，随后 GPS 数据中断。"
                }
            },
            stay: {
                start: {
                    ARRIVAL: "你在 {time} 停止移动并到达此处。",
                    MANUAL_SPLIT: "你拆分了一段行程，添加了此次停留。"
                }
            }
        },
        callouts: {
            gapFiltered: "此时段记录了 {count} 个 GPS 定位点，误差均大于 {thresholdMeters}（{setting}），因此这些点已被过滤，此时段显示为数据缺失。",
            settingChanged: "生成时的{setting}为 {builtWith}，当前值为 {current}。重新生成时间线以应用新值。"
        },
        filtered: {
            none: "此事件的所有 GPS 定位点均已采用。",
            some: "{total} 个 GPS 定位点中有 {filtered} 个因误差大于 {thresholdMeters}（{setting}）而被过滤。",
            worst: "精度最低的定位点误差为 ±{accuracyMeters}。",
            context: "此事件前后另有 {count} 个定位点也被过滤。",
            off: "{setting}已关闭，GPS 定位点未按精度过滤。"
        },
        ingestion: {
            intro: "位置来源会在保存前过滤部分 GPS 定位点，以下规则过滤掉的点无法在此显示：",
            source: "{source}：{rules}",
            accuracy: "误差大于 {value} 的定位点",
            speed: "速度高于 {value} 的定位点",
            duplicates: "在 {value} 内重复定位到同一地点",
            link: "位置来源"
        },
        point: {
            used: "用于判定此事件。",
            before: "在此事件之前。",
            after: "在此事件之后。",
            filtered: "已过滤：误差为 ±{accuracyMeters}，超出 GPS 精度阈值 {thresholdMeters}。",
            outsideRadius: "距中心点 {distanceMeters}，超出 {radiusMeters} 的半径。",
            accuracy: "精度 ±{value}",
            noAccuracy: "未记录精度",
            speed: "速度 {value}",
            reason: {
                FIRST_POINT: "你的第一个 GPS 定位点。",
                AFTER_DATA_GAP: "GPS 数据中断后的第一个定位点。",
                ARRIVAL: "属于你到达后停止移动的那段记录。",
                SPARSE_STOP: "你停止移动的位置。下一个定位点隔了较久才出现，且距离很近。",
                LEFT_STAY: "超出停留检测半径，此行程从这里开始。",
                SHORT_STOP: "属于一段未达到停留时长的短暂停顿。",
                WATER_TRANSITION: "你在陆地和水域之间转换的位置。",
                INFERRED_DURING_GAP: "位于数据中断旁，用于推断中断期间的停留。",
                INFERRED_TRIP_DURING_GAP: "位于数据中断旁，用于推断中断期间的行程。",
                STOPPED_BEFORE_GAP: "你在数据中断前停止移动的位置。",
                NO_POINTS: "位于 GPS 数据中断前后。",
                NO_POINTS_SINCE_LAST: "中断前的最后一个定位点。",
                CONFIRMED: "从此处开始计为停留。",
                FAVORITE_AREA: "超出停留检测半径，但仍在收藏区域内。",
                CONTINUED_OVER_GAP: "位于数据中断旁，停留在中断前后持续。",
                MOVED_AWAY: "超出停留检测半径，停留在此结束。",
                DATA_GAP: "位于 GPS 数据中断前后。",
                LAST_POINT: "你的最新 GPS 定位点。"
            }
        },
        settingsUsed: "设置",
        changeSettings: "时间线设置",
        whyClassification: "为何判为此出行方式？",
        close: "关闭",
        on: "开启",
        off: "关闭",
        minutes: "{value} 分钟"
    },
    container: {
        unknownActivityHidden: "此时段没有停留或行程记录。未知活动已隐藏。",
        displaySettings: "时间线显示设置",
        noData: "所选日期范围内没有时间线记录。",
        showingItems: "共 {total} 条记录，已显示 {shown} 条。",
        loadMore: "加载更多",
        viewAllInReports: "也可在时间线报告中查看全部记录",
        viewPeriodAria: "查看 {name} 时段",
        showOnlyDayOnMap: "地图上仅显示此日",
        showAllDaysOnMap: "地图上显示所有日期",
        exportStartedTitle: "已开始导出",
        tripExportingDetail: "正在将行程导出为 GPX",
        stayExportingDetail: "正在将停留导出为 GPX",
        exportFailedTitle: "导出失败",
        tripExportFailedDetail: "无法导出行程",
        stayExportFailedDetail: "无法导出停留"
    },
    focusedDay: {
        showing: "正在显示 {day}",
        showAll: "地图上显示所有日期"
    },
    splitLayout: {
        defaultCollapsedLabel: "时间线",
        defaultExpandedLabel: "活动时间线",
        dayNavigationAriaLabel: "时间线日期导航",
        previousDay: "上一天",
        nextDay: "下一天",
        show: "显示 {label}",
        collapse: "收起 {label}"
    },
    largeDataset: {
        title: "所选日期范围较大",
        itemsCount: "此范围包含 {count} 条记录",
        breakdown: "（{stays} 次停留、{trips} 段行程）",
        breakdownWithGaps: "（{stays} 次停留、{trips} 段行程、{dataGaps} 段数据缺失）",
        recommendation: "时间线适合按日或按周浏览。若要分析更长时段，可使用时间线报告。",
        openReports: "打开时间线报告",
        orViewRecent: "也可以查看最近的数据：",
        last7Days: "最近 7 天",
        last30Days: "最近 30 天",
        loadAnyway: "仍要加载（可能影响性能）",
        continueWithRange: "继续使用所选范围"
    },
    notesViewer: {
        header: "笔记",
        empty: "此事件没有笔记。",
        editNote: "编辑笔记",
        deleteNote: "删除笔记",
        openInMemos: "在 Memos 中打开",
        truncatedNotice: "此 Memos 笔记较长，GeoPulse 仅显示部分内容。",
        deleteConfirm: "要删除这条 GeoPulse 笔记吗？",
        cancel: "取消",
        delete: "删除",
        close: "关闭",
        locationSources: {
            explicit: "地理位置标记",
            derivedStay: "停留地点",
            derivedTripGps: "行程 GPS 定位点",
            derivedTripInterpolated: "行程估算位置"
        },
        toasts: {
            deletedTitle: "已删除笔记",
            deletedDetail: "GeoPulse 笔记已删除",
            deleteFailedTitle: "删除失败",
            deleteFailedDetail: "无法删除笔记"
        }
    },
    noteEditor: {
        titleLabel: "标题",
        titlePlaceholder: "可选标题",
        saveToLabel: "保存至",
        visibilityLabel: "Memos 可见范围",
        noteLabel: "笔记",
        contentPlaceholder: "写下笔记…",
        contentRequired: "请输入笔记内容",
        cancel: "取消",
        editHeader: "编辑笔记",
        addHeader: "添加笔记",
        update: "更新",
        save: "保存",
        destinations: {
            geopulse: "GeoPulse",
            memos: "Memos"
        },
        visibilityOptions: {
            private: "私有",
            protected: "受保护",
            public: "公开"
        },
        toasts: {
            updatedTitle: "笔记已更新",
            savedTitle: "笔记已保存",
            updatedDetail: "GeoPulse 笔记已更新",
            savedInMemos: "笔记已在 Memos 中创建",
            savedInGeoPulse: "笔记已保存到 GeoPulse",
            saveFailedTitle: "保存失败",
            saveFailedDetail: "无法保存笔记"
        }
    },
    dataGapHelp: {
        toggleAriaLabel: "展开或收起帮助信息",
        explanation: "GPS 追踪中断时会出现数据缺失。可在时间线设置中调整 GeoPulse 处理缺失记录的方式。",
        recommendationsHeader: "根据此缺失时段的时长（{duration}）：",
        openSettings: "打开时间线设置",
        toggleShort: "如何处理数据缺失？",
        toggleLong: "为什么会出现数据缺失？",
        tips: {
            lowerThreshold: "降低缺失检测阈值可识别更短的中断。",
            enableStayInference: "如果你始终在同一个地点，可启用「推断缺失时段内的停留」。",
            enableTripInference: "如果你移动了很长一段距离，可启用「推断缺失时段内的行程」。"
        }
    },
    layout: {
        collapsedLabel: "时间线",
        expandedLabel: "活动时间线"
    },
    labels: {
        page: {
            title: "时间线标签",
            subtitleLabelsCount: "{count} 个标签",
            subtitleDaysTagged: "已标记 {count} 天",
            subtitleFallback: "高级时间线标签（用于时间线筛选和导入）"
        },
        actions: {
            create: "创建标签",
            timeline: "查看时间线",
            more: "更多"
        },
        help: "可为时间线上的一段时期添加标签，例如一次旅行或一场活动。这样可以快速筛选这段时期，也可以将它转为旅行计划。",
        activeBanner: {
            kicker: "当前生效的标签",
            since: "自 {date} 起"
        },
        sourceNames: {
            manual: "手动",
            ownTracks: "OwnTracks"
        },
        filters: {
            search: "搜索标签…",
            source: {
                all: "所有来源"
            },
            linkState: {
                all: "全部",
                linked: "已关联",
                unlinked: "未关联"
            }
        },
        bulkDelete: "删除（{count}）",
        badges: {
            active: "生效中",
            hidden: "已隐藏",
            visible: "可见"
        },
        now: "现在",
        table: {
            pageReport: "共 {totalRecords} 个标签，显示第 {first} 至 {last} 个",
            columns: {
                label: "标签",
                period: "时段",
                tripPlan: "旅行计划",
                preset: "预设",
                actions: "操作"
            },
            notLinked: "未关联",
            empty: "找不到时间线标签",
            actionsAria: "{name}的更多操作",
            selectAria: "选择 {name}"
        },
        cards: {
            period: "时段",
            duration: "时长",
            datePreset: "预设",
            linkedTripPlan: "关联的旅行计划",
            showingCount: "显示 {count} 个标签"
        },
        linkedDelete: {
            header: "删除已关联的标签",
            linkedToLead: "此时间线标签已关联旅行计划",
            choose: "你想如何继续？",
            cancel: "取消",
            onlyLabel: "仅删除标签",
            labelAndTripPlan: "删除标签和旅行计划"
        }
    },
    page: {
        workspaceBanner: {
            lead: "所选日期范围与此旅行计划一致：",
            openPlanner: "打开规划工具"
        },
        map: {
            noData: "所选日期范围内没有位置数据。"
        },
        favorite: {
            deleteConfirm: {
                header: "删除收藏地点",
                message: "确定要删除此收藏地点吗？"
            },
            deleteSuccess: "「{name}」已删除",
            deleteFailed: "无法删除收藏地点"
        },
        toasts: {
            noLocationData: "所选日期范围内没有位置数据。",
            locationFetchFailed: "无法获取位置数据",
            noTimelineData: "所选日期范围内没有时间线数据。",
            timelineFetchFailed: "无法获取时间线数据",
            timelineLoadFailed: "无法加载时间线数据",
            viewingTag: "正在查看 {name}",
            tagPeriod: "正在显示「{name}」的完整时段"
        },
        resetFailedSummary: "重置失败",
        correctionQueuedSummary: "时间线更新已排队",
        correctionQueuedDetail: "修正已保存。当前重建任务完成后，时间线将自动更新。",
        reconstruction: {
            updatedSummary: "时间线已更新",
            updatedDetail: "已使用重建后的数据更新时间线。",
            failedSummary: "重建失败",
            failedDetail: "无法重建时间线数据。",
            trackingFailedSummary: "追踪失败"
        }
    },
    preferences: {
        gpsPathSimplification: {
            title: "GPS 轨迹简化设置",
            description: "配置如何简化 GPS 轨迹，在保留路线精度的同时减少数据量",
            enabled: {
                title: "启用轨迹简化",
                description: "为时间线中的行程启用 GPS 轨迹简化",
                details: "启用后，系统会使用 Douglas-Peucker 算法简化行程轨迹，减少 GPS 定位点数量并保留路线精度"
            },
            tolerance: {
                title: "简化容差",
                description: "GPS 轨迹简化的基础容差，单位为米",
                detailsLowerLabel: "较低值",
                detailsLowerValue: "保留更多细节，压缩程度更低",
                detailsHigherLabel: "较高值",
                detailsHigherValue: "压缩程度更高，保留较少细节",
                labelLow: "1 米（细节丰富）",
                labelMid: "15 米（均衡）",
                labelHigh: "50 米（高压缩）",
                suffix: " 米"
            },
            maxPoints: {
                title: "定位点数上限",
                description: "简化后的轨迹最多可保留的 GPS 定位点数",
                details: "如果简化后的轨迹仍超过此上限，系统将自动增大容差，直到满足上限要求。设为 0 表示无限制",
                labelLow: "0（无限制）",
                labelMid: "100（均衡）",
                labelHigh: "500（高上限）",
                suffix: " 个"
            },
            adaptive: {
                title: "自适应简化",
                description: "根据行程特征自动调整简化容差",
                details: "启用后，较长的行程使用较高的容差以提高压缩率，较短的行程使用较低的容差以保留更高精度"
            }
        },
        stayMerging: {
            title: "停留点合并设置",
            description: "配置如何将邻近的停留点合并为单个地点",
            enabled: {
                title: "启用停留点合并",
                description: "合并时间和距离相近的停留点",
                details: "将同一地点附近的多组 GPS 定位点合并为单个停留点"
            },
            maxDistance: {
                title: "合并距离上限",
                description: "可合并的停留点之间的最大距离",
                detailsLowerLabel: "较低值",
                detailsLowerValue: "仅合并距离很近的停留点",
                detailsHigherLabel: "较高值",
                detailsHigherValue: "允许合并距离较远的停留点",
                labelLow: "20 米（精确）",
                labelMid: "150 米（均衡）",
                labelHigh: "500 米（宽松）",
                suffix: " 米"
            },
            maxTimeGap: {
                title: "合并时间间隔上限",
                description: "可合并的停留点之间的最长时间间隔",
                detailsLowerLabel: "较低值",
                detailsLowerValue: "仅合并连续的停留",
                detailsHigherLabel: "较高值",
                detailsHigherValue: "允许合并间隔较长的停留",
                labelLow: "1 分钟（严格）",
                labelMid: "10 分钟（均衡）",
                labelHigh: "60 分钟（宽松）",
                suffix: " 分钟"
            }
        },
        gpsGaps: {
            title: "GPS 数据缺失检测设置",
            description: "配置如何检测 GPS 数据缺失并记录到时间线",
            threshold: {
                title: "数据缺失阈值",
                description: "相邻 GPS 定位点的时间间隔超过此值即视为 GPS 数据缺失，单位为秒",
                details: "当相邻两个 GPS 定位点的时间间隔超过此阈值时，系统会添加 GPS 数据缺失记录，防止当前停留或行程因 GPS 数据中断而被延长。",
                labelLow: "5 分钟（灵敏）",
                labelMid: "3 小时（均衡）",
                labelHigh: "6 小时（宽松）",
                suffix: " 秒"
            },
            minDuration: {
                title: "最短缺失时长",
                description: "要记录为 GPS 数据缺失，中断至少要持续的秒数",
                details: "短于此时长的中断会被忽略，避免短暂的连接问题产生多余的缺失记录。",
                labelLow: "5 分钟（严格）",
                labelMid: "30 分钟（均衡）",
                labelHigh: "2 小时（宽松）",
                suffix: " 秒"
            },
            stayInferenceEnabled: {
                title: "推断缺失时段内的停留",
                description: "当 GPS 数据中断前后位于同一地点时，推断期间一直在此处停留",
                detailsWhenLabel: "启用后",
                detailsWhenValue: "如果在同一地点中断并恢复 GPS 数据记录，系统会推断你一直停留在这里",
                detailsUseCaseLabel: "适用场景",
                detailsUseCaseValue: "在家过夜时的 GPS 数据中断会显示为一次停留"
            },
            stayInferenceMaxGap: {
                title: "停留推断允许的最长缺失时长",
                description: "可用于推断停留的 GPS 数据缺失时长上限",
                detailsLowerLabel: "较低值",
                detailsLowerValue: "只在中断较短时推断停留，例如手机短暂关机",
                detailsHigherLabel: "较高值",
                detailsHigherValue: "允许在较长的中断期间推断停留，例如一整夜或一整天",
                labelLow: "1 小时（严格）",
                labelMid: "24 小时（默认）",
                labelHigh: "72 小时（宽松）",
                suffix: " 小时"
            },
            tripInferenceEnabled: {
                title: "推断缺失时段内的行程",
                description: "在 GPS 数据缺失前后发生远距离移动时推断行程",
                detailsWhenLabel: "启用后",
                detailsWhenValue: "如果 GPS 数据在远处恢复，系统会推断期间发生了行程",
                detailsUseCaseLabel: "适用场景",
                detailsUseCaseValue: "在夜间航班或长途驾车期间关闭手机，系统会在缺失时段显示推断出的行程",
                detailsClassificationLabel: "行程分类",
                detailsClassificationValue: "系统根据距离、时长和速度自动判定出行方式，例如飞机、汽车或火车"
            },
            tripInferenceMinDistance: {
                title: "行程推断所需的最短距离",
                description: "要在缺失时段推断行程，前后两个 GPS 定位点之间必须达到的最短距离",
                detailsLowerLabel: "较低值",
                detailsLowerValue: "可推断距离较短的行程，例如 10 公里的市内行程",
                detailsHigherLabel: "较高值",
                detailsHigherValue: "仅推断距离较长的行程，例如超过 100 公里的城际行程",
                labelLow: "10 公里（短途）",
                labelMid: "100 公里（默认）",
                labelHigh: "500 公里（长途）",
                suffix: " 米"
            },
            tripInferenceMinGap: {
                title: "行程推断所需的最短缺失时长",
                description: "可用于推断行程的最短缺失时长",
                detailsLowerLabel: "较低值",
                detailsLowerValue: "可根据短暂的数据中断推断行程，例如 30 分钟",
                detailsHigherLabel: "较高值",
                detailsHigherValue: "只根据较长的数据中断推断行程，例如数小时",
                labelLow: "0 小时（任意时长）",
                labelMid: "1 小时（默认）",
                labelHigh: "12 小时（长间隔）",
                suffix: " 小时"
            },
            tripInferenceMaxGap: {
                title: "行程推断允许的最长缺失时长",
                description: "可用于推断行程的最长缺失时长，超过此时长将显示为数据缺失",
                detailsLowerLabel: "较低值",
                detailsLowerValue: "只根据较短的数据中断推断行程，例如 12 小时",
                detailsHigherLabel: "较高值",
                detailsHigherValue: "可根据较长的数据中断推断行程，例如多日行程",
                labelLow: "1 小时（严格）",
                labelMid: "24 小时（默认）",
                labelHigh: "168 小时（一周）",
                suffix: " 小时"
            }
        },
        stayPointDetection: {
            title: "停留点检测设置",
            description: "配置如何分析 GPS 数据以识别你停留过的地点",
            radius: {
                title: "停留检测半径",
                description: "将 GPS 定位点归到同一停留地点的距离阈值，也用于确定两次停留之间生成行程所需的最短距离",
                detailsLowerLabel: "较低值",
                detailsLowerValue: "对停留检测更灵敏，可区分彼此相近的地点",
                detailsHigherLabel: "较高值",
                detailsHigherValue: "灵敏度较低，相邻地点可能合并为一次停留",
                labelLow: "10 米（灵敏）",
                labelMid: "50 米（均衡）",
                labelHigh: "500 米（保守）",
                suffix: " 米"
            },
            minDuration: {
                title: "最短停留时长",
                description: "确认一次停留所需的最短时长，单位为分钟",
                detailsLowerLabel: "较低值",
                detailsLowerValue: "很短的暂停也可能被误判为停留",
                detailsHigherLabel: "较高值",
                detailsHigherValue: "停留时间越长，判定越可靠",
                labelLow: "1 分钟（灵敏）",
                labelMid: "7 分钟（均衡）",
                labelHigh: "60 分钟（保守）",
                suffix: " 分钟"
            },
            enhancedFiltering: {
                title: "增强过滤",
                description: "使用速度和定位精度数据来改善停留点检测",
                details: "过滤质量较差的 GPS 定位点，提高时间线精度"
            },
            velocityThreshold: {
                title: "速度阈值",
                description: "将定位点视为静止时允许的最高速度",
                detailsLowerLabel: "较低值",
                detailsLowerValue: "过滤条件更严格",
                detailsHigherLabel: "较高值",
                detailsHigherValue: "允许停留期间有更多移动",
                labelLow: "1 公里/小时（严格）",
                labelMid: "9 公里/小时（均衡）",
                labelHigh: "20 公里/小时（宽松）",
                suffix: " 公里/小时"
            },
            accuracyThreshold: {
                title: "GPS 精度阈值",
                description: "采用 GPS 定位点所需的最低精度",
                detailsLowerLabel: "较低值",
                detailsLowerValue: "要求更高的 GPS 精度",
                detailsHigherLabel: "较高值",
                detailsHigherValue: "接受精度较低的 GPS 定位点",
                labelLow: "5 米（高精度）",
                labelMid: "60 米（均衡）",
                labelHigh: "200 米（低精度）",
                suffix: " 米"
            }
        },
        tripClassification: {
            title: "行程分类设置",
            description: "配置行程的检测方式，以及如何根据出行方式分类",
            priorityBanner: {
                title: "分类优先顺序",
                steps: {
                    flight: "飞机",
                    boat: "船舶",
                    train: "火车",
                    bicycle: "自行车",
                    running: "跑步",
                    motorVehicle: "机动车",
                    walk: "步行",
                    unknown: "未知"
                },
                description: "从上到下按优先顺序匹配行程类型，找到匹配类型后即停止。此顺序用于妥善处理水域和速度数据。例如，飞越水域的行程仍归为飞机。在陆地上的速度范围重叠时，优先判定为自行车，其次是跑步和机动车。"
            },
            algorithm: {
                title: "行程检测算法",
                description: "用于识别停留点之间行程的算法",
                detailsSingleLabel: "单段",
                detailsSingleValue: "两次停留之间始终只有一段行程",
                detailsMultipleLabel: "多段",
                detailsMultipleValue: "根据速度识别两次停留之间的一段或多段行程，例如从汽车切换为步行，或从步行切换为汽车",
                placeholder: "选择算法",
                optionSingle: "单段行程",
                optionMultiple: "多段行程"
            },
            walk: {
                title: "步行",
                subtitle: "始终启用的出行方式",
                description: "检测低速步行，典型速度为 0–8 公里/小时",
                maxAvgSpeed: {
                    label: "平均速度上限",
                    description: "平均速度超过此值的行程将归为其他类别",
                    labelLow: "3.0 公里/小时（慢速）",
                    labelMid: "5.5 公里/小时（正常）",
                    labelHigh: "10.0 公里/小时（快速）"
                },
                maxMaxSpeed: {
                    label: "峰值速度上限",
                    description: "如果短时速度超过此值，将重新判定行程分类",
                    labelLow: "5.0 公里/小时（保守）",
                    labelMid: "8.0 公里/小时（正常）",
                    labelHigh: "15.0 公里/小时（宽松）"
                }
            },
            bicycle: {
                title: "自行车",
                subtitle: "可选出行方式",
                description: "识别骑行行程，典型速度为 8–25 公里/小时。分类优先顺序可处理与跑步和汽车速度区间重叠的情况。",
                minAvgSpeed: {
                    label: "平均速度下限",
                    description: "低于此速度的行程将判为跑步或步行",
                    labelLow: "5.0 公里/小时（慢速）",
                    labelMid: "8.0 公里/小时（默认）",
                    labelHigh: "15.0 公里/小时（快速）"
                },
                maxAvgSpeed: {
                    label: "平均速度上限",
                    description: "高于此速度的行程将判为机动车行程",
                    labelLow: "15.0 公里/小时（慢速）",
                    labelMid: "25.0 公里/小时（默认）",
                    labelHigh: "35.0 公里/小时（电动自行车）"
                },
                maxMaxSpeed: {
                    label: "峰值速度上限",
                    description: "允许下坡、公路自行车或电动自行车达到较高速度",
                    labelLow: "20.0 公里/小时（市区）",
                    labelMid: "35.0 公里/小时（默认）",
                    labelHigh: "80.0 公里/小时（公路自行车）"
                }
            },
            running: {
                title: "跑步",
                subtitle: "可选出行方式",
                description: "识别跑步或慢跑行程，典型速度为 7–14 公里/小时。保守的阈值可区分快走和慢速骑行。关闭后，符合跑步速度的行程会归为自行车（如果启用）、汽车或未知。",
                minAvgSpeed: {
                    label: "平均速度下限",
                    description: "低于此速度的行程将判为步行",
                    labelLow: "5.0 公里/小时（慢速）",
                    labelMid: "7.0 公里/小时（默认）",
                    labelHigh: "10.0 公里/小时（快速）"
                },
                maxAvgSpeed: {
                    label: "平均速度上限",
                    description: "高于此速度的行程将判为骑行或机动车行程",
                    labelLow: "10.0 公里/小时（慢速）",
                    labelMid: "14.0 公里/小时（默认）",
                    labelHigh: "18.0 公里/小时（快速）"
                },
                maxMaxSpeed: {
                    label: "峰值速度上限",
                    description: "允许短时冲刺，同时保持在骑行速度以下",
                    labelLow: "12.0 公里/小时（慢速）",
                    labelMid: "18.0 公里/小时（默认）",
                    labelHigh: "25.0 公里/小时（冲刺）"
                }
            },
            car: {
                title: "机动车",
                subtitle: "汽车、摩托车或公共交通标签",
                description: "使用统一的机动车速度阈值识别机动车行程。公共交通标签用于标记这些行程，系统不会专门识别公交车。",
                carLabel: {
                    title: "汽车标签",
                    description: "允许将识别出的机动车行程标记为汽车。",
                    ariaEnable: "启用汽车标签"
                },
                motorcycleLabel: {
                    title: "摩托车标签",
                    description: "允许将识别出的机动车行程标记为摩托车。",
                    ariaEnable: "启用摩托车标签"
                },
                publicTransportLabel: {
                    title: "公共交通标签",
                    description: "允许将识别出的机动车行程标记为公共交通。",
                    ariaEnable: "启用公共交通标签"
                },
                preferredLabel: {
                    label: "首选机动车标签",
                    description: "启用多个标签时，识别出的机动车行程默认使用此标签。",
                    placeholder: "选择首选标签",
                    optionCar: "汽车",
                    optionMotorcycle: "摩托车",
                    optionPublicTransport: "公共交通"
                },
                minAvgSpeed: {
                    label: "平均速度下限",
                    description: "平均速度低于此值时，会优先尝试判定为步行、跑步或自行车",
                    labelLow: "5.0 公里/小时（灵敏）",
                    labelMid: "10.0 公里/小时（默认）",
                    labelHigh: "25.0 公里/小时（保守）"
                },
                minMaxSpeed: {
                    label: "峰值速度下限",
                    description: "行程的峰值速度未达到此值时，只有平均速度符合条件才会判定为机动车行程",
                    labelLow: "10.0 公里/小时（市区）",
                    labelMid: "15.0 公里/小时（默认）",
                    labelHigh: "50.0 公里/小时（高速公路）"
                }
            },
            train: {
                title: "火车",
                subtitle: "可选出行方式",
                description: "根据较高且稳定的速度识别火车行程，典型速度为 30–150 公里/小时。通过速度方差区分火车和汽车。",
                minAvgSpeed: {
                    label: "平均速度下限",
                    description: "与拥堵路段中的汽车行程区分开来",
                    labelLow: "20.0 公里/小时（市区）",
                    labelMid: "30.0 公里/小时（默认）",
                    labelHigh: "50.0 公里/小时（快速）"
                },
                maxAvgSpeed: {
                    label: "平均速度上限",
                    description: "覆盖区域列车及城际列车",
                    labelLow: "80.0 公里/小时（区域列车）",
                    labelMid: "150.0 公里/小时（默认）",
                    labelHigh: "400.0 公里/小时（高速列车）"
                },
                minMaxSpeed: {
                    label: "峰值速度下限（过滤候车）",
                    description: "过滤只有车站候车时间的行程",
                    labelLow: "60.0 公里/小时（宽松）",
                    labelMid: "80.0 公里/小时（默认）",
                    labelHigh: "120.0 公里/小时（严格）"
                },
                maxMaxSpeed: {
                    label: "峰值速度上限",
                    description: "火车速度的上限",
                    labelLow: "100.0 公里/小时（区域列车）",
                    labelMid: "180.0 公里/小时（默认）",
                    labelHigh: "500.0 公里/小时（高速列车）"
                },
                maxSpeedVariance: {
                    label: "速度方差上限（关键判定条件）",
                    description: "火车的速度方差较低（低于 15），汽车的速度方差较高（高于 25）。这是区分火车和汽车的关键条件。",
                    labelLow: "5.0（严格）",
                    labelMid: "15.0（默认）",
                    labelHigh: "30.0（宽松）"
                }
            },
            flight: {
                title: "飞机",
                subtitle: "可选出行方式",
                description: "以高速度识别飞机行程：平均速度达到 400 公里/小时或峰值速度达到 500 公里/小时，满足其中一项即可。这可兼顾地面滑行或等候时间较长的航班。系统会自动过滤超过 1200 公里/小时的 GPS 异常读数。",
                minAvgSpeed: {
                    label: "平均速度下限",
                    description: "适用于常规航班的保守默认值，计算时包含滑行、起飞和降落",
                    labelLow: "100.0 公里/小时（支线航班）",
                    labelMid: "400.0 公里/小时（默认）",
                    labelHigh: "600.0 公里/小时（长途航班）"
                },
                minMaxSpeed: {
                    label: "峰值速度下限",
                    description: "平均或峰值速度满足其一即可识别航班，也适用于地面滑行或等候时间较长的航班",
                    labelLow: "150.0 公里/小时（轻型飞机）",
                    labelMid: "500.0 公里/小时（默认）",
                    labelHigh: "900.0 公里/小时（喷气式飞机）"
                },
                minDistance: {
                    label: "最短距离",
                    description: "短于此距离的行程不会判为飞机行程，可避免短途行程因 GPS 定位跳变而误判。0 表示不限制最短距离。",
                    labelLow: "0 公里（默认）",
                    labelMid: "250 公里",
                    labelHigh: "500 公里（严格）"
                },
                altitudeDetection: {
                    title: "按高度识别",
                    description: "如果行程高度在一段时间内明显高于起点和终点，并且出现明显爬升或下降，也可判为飞机行程。适用于速度较低、无法满足速度条件的滑翔机、热气球和轻型飞机。需要 GPS 海拔数据。滑雪或缆车环线可能被误判为飞机行程。",
                    ariaEnable: "启用按高度识别飞机行程"
                },
                altitudeMinHeight: {
                    label: "高出起终点的最小高度",
                    description: "以起终点中较高的海拔为基准，行程至少有四分之一的时间需高出基准此高度。较低的值可识别更短的飞行，但可能误判丘陵或山区的驾车和骑行。",
                    labelLow: "50 米（宽松）",
                    labelMid: "300 米（默认）",
                    labelHigh: "5000 米（严格）"
                },
                altitudeMinVerticalSpeed: {
                    label: "最低垂直速度",
                    description: "行程必须达到的爬升或下降速度。以一分钟为窗口计算第 90 百分位数，以排除瞬时变化。滑翔机和热气球常以 1–5 米/秒爬升或下降，数值过低时可能匹配到缆车或滑雪索道。",
                    labelLow: "0.3 米/秒（宽松）",
                    labelMid: "1.2 米/秒（默认）",
                    labelHigh: "10.0 米/秒（严格）"
                }
            },
            boat: {
                title: "船舶",
                subtitle: "可选出行方式",
                description: "根据持续的水上移动识别船舶行程。速度仅用于排除不合理的结果，判定船舶行程需要水域数据。",
                setup: {
                    requiredTitle: "需要设置船舶识别",
                    readyTitle: "船舶识别已就绪",
                    failedTitle: "船舶识别设置失败",
                    inProgressTitle: "正在设置船舶识别",
                    readyPhaseText: "已缓存的水域判定数据可用。",
                    docsLink: "离线设置文档",
                    retry: "重试",
                    phases: {
                        BOAT_SETUP_IS_READY: "船舶识别已就绪",
                        GPS_WATER_EVIDENCE_NEEDS_ENRICHMENT: "需要补充 GPS 定位点的水域判定数据",
                        WATER_DATASET_IS_NOT_IMPORTED: "尚未导入水域数据集",
                        STARTING_BOAT_SETUP: "正在启动船舶识别设置",
                        BOAT_SETUP_COMPLETED: "船舶识别设置完成",
                        RECLASSIFYING_EXISTING_TRIPS: "正在重新判定现有行程的分类",
                        WATER_DATASET_READY: "水域数据集已准备就绪",
                        PREPARING_WATER_DATASET_ARTIFACT: "正在准备水域数据集文件",
                        IMPORTING_WATER_POLYGONS: "正在导入水域多边形",
                        USING_LOCAL_WATER_DATASET_FILE: "正在使用本地水域数据集文件",
                        DOWNLOADING_WATER_DATASET: "正在下载水域数据集",
                        GPS_WATER_EVIDENCE_READY: "GPS 定位点的水域判定数据已就绪",
                        CLASSIFYING_GPS_WATER_EVIDENCE: "正在判定 GPS 定位点是否位于水域",
                        WAITING_FOR_WATER_DATASET_IMPORT: "正在等待水域数据集导入",
                        WAITING_FOR_GPS_WATER_EVIDENCE: "正在等待 GPS 定位点的水域判定结果",
                        BOAT_SETUP_WORKER_DID_NOT_START: "船舶识别的后台设置任务未启动",
                        WATER_DATASET_SETUP_FAILED: "水域数据集设置失败",
                        BOAT_SETUP_FAILED: "船舶识别设置失败",
                        fallback: "正在准备船舶识别…"
                    },
                    errorFallback: "水域数据集设置失败。请查看离线设置说明。"
                },
                minWaterRatio: {
                    label: "最低水域比例",
                    description: "行程的有效距离中，水上路段所需达到的最低占比",
                    labelLow: "10%（宽松）",
                    labelMid: "60%（默认）",
                    labelHigh: "100%（严格）"
                },
                minWaterDistance: {
                    label: "最短水上距离",
                    description: "判为船舶行程所需的最短水上总距离",
                    labelLow: "100 米（宽松）",
                    labelMid: "2000 米（默认）",
                    labelHigh: "10000 米（严格）"
                },
                minContinuousWaterDistance: {
                    label: "最短连续水上距离",
                    description: "过滤经过桥梁、隧道和岸线时的短暂跨水路段",
                    labelLow: "100 米（宽松）",
                    labelMid: "1000 米（默认）",
                    labelHigh: "10000 米（严格）"
                },
                maxPlausibleSpeed: {
                    label: "最高合理速度",
                    description: "用于排除不合理的高速。低速移动仍可能判为船舶行程",
                    labelLow: "20 公里/小时（严格）",
                    labelMid: "120 公里/小时（默认）",
                    labelHigh: "300 公里/小时（宽松）"
                }
            },
            shortDistance: {
                title: "短途行程距离阈值",
                description: "短于此距离的行程采用较宽松的步行速度判定条件",
                details: "短途行程的步行速度判定条件会略放宽，以弥补 GPS 定位误差",
                labelLow: "0.1 公里（严格）",
                labelMid: "1.0 公里（正常）",
                labelHigh: "3.0 公里（宽松）"
            },
            arrivalDetection: {
                title: "到达判定时长",
                description: "判定到达目的地所需的最短时长，此期间 GPS 定位点集中且速度较低",
                detailsLowerLabel: "较低值",
                detailsLowerValue: "更灵敏，可能检测到短暂停顿",
                detailsHigherLabel: "较高值",
                detailsHigherValue: "更保守，仅在持续停留时判定为到达",
                labelLow: "10 秒（灵敏）",
                labelMid: "90 秒（正常）",
                labelHigh: "300 秒（保守）"
            },
            sustainedStop: {
                title: "持续停止时长",
                description: "判定停止所需的最短连续低速时长，用于过滤等红灯等情况",
                detailsLowerLabel: "较低值",
                detailsLowerValue: "可检测到较短的停顿，也可能包括交通延误",
                detailsHigherLabel: "较高值",
                detailsHigherValue: "只检测较长的停顿，能更好地过滤交通延误",
                labelLow: "10 秒（灵敏）",
                labelMid: "60 秒（正常）",
                labelHigh: "600 秒（保守）"
            },
            arrivalMinPoints: {
                title: "到达判定所需的最少静止定位点数",
                description: "判定到达目的地所需的最少 GPS 定位点数",
                detailsLowerLabel: "较低值（2）",
                detailsLowerValue: "检测更快，适用于 GPS 定位不频繁的情况（间隔 10–15 分钟）",
                detailsHigherLabel: "较高值（3–4）",
                detailsHigherValue: "更可靠，适用于 GPS 定位频繁的情况（间隔 30–60 秒）",
                labelLow: "2 个定位点（快速）",
                labelMid: "3 个定位点（均衡）",
                labelHigh: "5 个定位点（保守）"
            }
        }
    },
    reports: {
        page: {
            title: "时间线报告",
            subtitle: "查看停留、行程和数据缺失的详细列表"
        },
        demoNotice: "演示模式下无法导出数据。",
        exportAll: "全部导出",
        exportAllTooltip: "将停留、行程和数据缺失导出为 CSV",
        demoExportDisabled: "演示模式下无法导出",
        demoExportDisabledToast: "演示模式下无法导出数据。",
        sections: {
            stays: "停留",
            trips: "行程",
            dataGaps: "数据缺失"
        },
        noDateRange: "尚未选择日期范围",
        noData: {
            title: "未找到数据",
            message: "所选日期范围内没有时间线数据。",
            suggestion: "请尝试选择其他日期范围，或检查你的 GPS 来源。"
        },
        fetchFailed: "无法获取时间线数据",
        export: {
            successSummary: "导出成功",
            failedSummary: "导出失败",
            staysDone: "停留记录已导出",
            staysFailed: "无法导出停留记录",
            tripsDone: "行程记录已导出",
            tripsFailed: "无法导出行程记录",
            dataGapsDone: "数据缺失记录已导出",
            dataGapsFailed: "无法导出数据缺失记录",
            allDone: "全部数据已导出",
            failedGeneric: "无法导出数据"
        }
    }
}
