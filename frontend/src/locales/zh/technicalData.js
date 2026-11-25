export default {
    page: {
        title: "GPS 数据",
        subtitle: "查看 GPS 轨迹数据的技术详情",
        actions: {
            selected: "已选 {count} 个",
            deleteSelected: "删除所选数据",
            deleteAllData: "删除全部数据",
            exportCsv: "导出 CSV",
            exportCsvSelected: "导出 CSV（已选 {count} 个点）",
            exportCsvZero: "导出 CSV（0 个点）",
            exportCsvFiltered: "导出 CSV（{count} 个点）"
        },
        stats: {
            totalPoints: "GPS 点总数",
            pointsToday: "今日 GPS 点数",
            firstPoint: "最早的 GPS 点",
            lastPoint: "最新的 GPS 点"
        },
        filters: {
            title: "筛选",
            activeCount: "已启用 {count} 项",
            clearAll: "清除全部",
            hideAdvanced: "收起高级筛选",
            showAdvanced: "展开高级筛选",
            today: "今天",
            yesterday: "昨天",
            last7Days: "最近 7 天",
            last30Days: "最近 30 天",
            from: "开始",
            to: "结束",
            startPlaceholder: "开始日期和时间",
            endPlaceholder: "结束日期和时间",
            apply: "应用",
            clear: "清除",
            fromBeforeTo: "开始时间必须早于结束时间。",
            sourceTypesLabel: "来源类型",
            allSources: "全部来源",
            accuracyLabel: "定位误差（米）",
            speedLabel: "速度（公里/小时）",
            min: "最小值",
            max: "最大值",
            rangeTo: "至",
            dateChip: "日期：{range}",
            accuracyChip: "定位误差：{min} 至 {max} 米",
            speedChip: "速度：{min} 至 {max} 公里/小时",
            sourcesChip: "来源：已选 {count} 项",
            sourceOptions: {
                manualReconstruction: "手动补录"
            },
            noPointsForPeriod: "所选时段没有 GPS 点",
            showingOfTotal: "显示 {filtered} 个点，共 {total} 个",
            showingCount: "显示 {count} 个点"
        },
        telemetry: {
            title: "遥测字段映射（可选）",
            hint: "按来源类型全局设置，适用于 GPS 数据页和时间线弹窗。",
            hideMapping: "收起映射设置",
            showMapping: "展开映射设置",
            sourceTypeLabel: "来源类型",
            resetToDefaults: "恢复默认设置",
            addRow: "添加一行",
            saveMapping: "保存映射",
            loading: "正在加载遥测字段映射…",
            empty: "尚未配置遥测字段映射。",
            keyPlaceholder: "ext 字段名",
            labelPlaceholder: "标签",
            unitPlaceholder: "单位",
            trueValuesPlaceholder: "真值（如 1,true,on）",
            falseValuesPlaceholder: "假值（如 0,false,off）",
            booleanDefaultLabel: "布尔值 {index}",
            columns: {
                key: "字段名",
                label: "标签",
                type: "类型",
                unit: "单位",
                gpsData: "GPS 数据",
                currentPopup: "当前位置弹窗",
                order: "顺序"
            },
            typeOptions: {
                boolean: "布尔值",
                number: "数值",
                string: "字符串"
            }
        },
        table: {
            title: "GPS 点",
            showing: "显示第 {start} 至 {end} 个点，共 {total} 个",
            filteredFrom: "筛选前共 {total} 个",
            noPointsFound: "找不到 GPS 点",
            rowsPerPage: "每页行数",
            rowsShort: "行数",
            pageOf: "第 {current} 页，共 {total} 页",
            totalCount: "共 {count} 个",
            emptyTitle: "找不到 GPS 点",
            emptyDescription: "没有符合筛选条件的 GPS 轨迹数据。",
            editTooltip: "编辑 GPS 点",
            deleteTooltip: "删除 GPS 点",
            columns: {
                date: "日期",
                delta: "时间间隔",
                location: "位置",
                speed: "速度",
                accuracy: "定位误差",
                altitude: "海拔",
                battery: "电量",
                telemetry: "遥测数据",
                source: "来源",
                actions: "操作"
            }
        },
        mobile: {
            selectPage: "全选本页",
            clearPage: "取消选择本页",
            selectAllAriaLabel: "选择本页的所有 GPS 点",
            sort: "排序",
            ascending: "升序",
            descending: "降序",
            sortAscendingAriaLabel: "按升序排列",
            sortDescendingAriaLabel: "按降序排列",
            loading: "正在加载 GPS 点…",
            actionsTooltip: "GPS 点操作",
            selectPointAriaLabel: "选择 {date} {time} 的 GPS 点",
            actionsAriaLabel: "操作 {date} {time} 的 GPS 点"
        },
        pendingEdits: {
            message: "有 {count} 项 GPS 点修改尚未应用。可以一次应用这些修改并重建时间线。",
            revertAll: "撤销全部修改",
            apply: "应用修改",
            revertTooltip: "撤销修改",
            leaveConfirm: {
                header: "有未应用的修改",
                message: "有 {count} 项 GPS 点修改尚未应用。离开此页后，这些修改将丢失。",
                accept: "放弃修改并离开",
                reject: "留在此页"
            }
        },
        dialogs: {
            delete: {
                header: "删除 GPS 点",
                message: "确定要删除此 GPS 点吗？此操作无法撤销。",
                confirm: "删除"
            },
            bulkDelete: {
                header: "批量删除 GPS 点",
                message: "确定要删除这 {count} 个 GPS 点吗？此操作无法撤销。",
                confirm: "删除所选数据"
            },
            deleteAll: {
                header: "删除全部 GPS 数据",
                warningTitle: "以下数据将被永久删除",
                allPoints: "全部 {count} 个 GPS 点",
                timelineData: "时间线中的全部停留、行程和数据缺口",
                note: "你的设置和收藏地点{not}受影响。此操作无法撤销。",
                notWord: "不",
                confirm: "删除全部数据"
            }
        },
        toasts: {
            telemetryLoadFailedSummary: "遥测数据加载失败",
            telemetryLoadFailedFallback: "无法加载遥测字段映射",
            telemetrySavedSummary: "遥测设置已保存",
            telemetrySavedDetail: "遥测字段映射已更新",
            saveFailedSummary: "保存失败",
            saveTelemetryFailedFallback: "无法保存遥测字段映射",
            telemetryResetSummary: "遥测设置已重置",
            telemetryResetDetail: "遥测字段映射已恢复为默认设置",
            resetFailedSummary: "重置失败",
            resetTelemetryFailedFallback: "无法重置遥测字段映射",
            summaryLoadFailedDetail: "无法加载 GPS 数据摘要",
            pointsLoadFailedDetail: "无法加载 GPS 点",
            exportStartedSummary: "导出已开始",
            exportSelectedDetail: "正在导出所选的 {count} 个 GPS 点，下载即将开始。",
            exportAllDetail: "正在导出 {count} 个 GPS 点，下载即将开始。",
            exportFailedSummary: "导出失败",
            exportFailedDetail: "无法导出 GPS 数据",
            pointDeletedSummary: "GPS 点已删除",
            pointDeletedDetail: "已成功删除 GPS 点",
            timelineRegenStarted: "已开始重建时间线。",
            timelineRegenScheduled: "当前时间线任务完成后，将开始重建时间线。",
            coverageRebuildScheduled: "已安排重新计算足迹覆盖范围。",
            deleteFailedSummary: "删除失败",
            deleteFailedFallback: "无法删除 GPS 点",
            pointsUpdatedSummary: "GPS 点已更新",
            pointsUpdatedDetail: "已更新 {count} 个 GPS 点。",
            updateFailedSummary: "更新失败",
            updateFailedFallback: "无法更新 GPS 点",
            pointsDeletedSummary: "GPS 点已删除",
            bulkDeleteSuccessDetail: "已删除 {count} 个 GPS 点",
            bulkDeleteFailedSummary: "批量删除失败",
            bulkDeleteFailedFallback: "无法删除所选 GPS 点",
            allDataDeletedSummary: "数据已全部删除",
            allDataDeletedDetail: "已删除所有 GPS 点和时间线数据",
            deleteAllFailedFallback: "无法删除全部 GPS 数据"
        }
    }
}
