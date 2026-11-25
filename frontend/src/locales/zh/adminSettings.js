export default {
    metadata: {
        system: {
            logging: {
                "application-level": {
                    label: "应用日志级别",
                    description: "可选 ERROR、WARN、INFO 或 DEBUG。重置后使用 GEOPULSE_LOG_LEVEL 或应用默认值。生产环境不支持 TRACE。"
                }
            },
            user: {
                "default-distance-unit": {
                    label: "默认距离单位",
                    description: "为新用户分配的距离单位"
                },
                "default-temperature-unit": {
                    label: "默认温度单位",
                    description: "为新用户分配的温度单位"
                }
            },
            timeline: {
                view: {
                    "item-limit": {
                        label: "时间线单次查询条数上限",
                        description: "每次查询时间线视图时最多返回的记录条数"
                    }
                },
                processing: {
                    "thread-pool-size": {
                        label: "时间线处理线程数",
                        description: "用于处理时间线的工作线程数量"
                    }
                }
            },
            "version-check": {
                "github-api-url": {
                    label: "版本发布 API URL",
                    description: "用于检查 GeoPulse 更新的 GitHub 兼容 API URL"
                },
                "release-url": {
                    label: "发布页面 URL",
                    description: "显示更新信息时使用的备用发布页面 URL"
                },
                "cache-ttl-minutes": {
                    label: "更新检查缓存有效期",
                    description: "缓存版本更新检查结果的分钟数"
                },
                "connect-timeout-seconds": {
                    label: "更新检查连接超时",
                    description: "连接更新 API 时等待的秒数"
                },
                "read-timeout-seconds": {
                    label: "更新检查读取超时",
                    description: "等待更新 API 响应的秒数"
                }
            },
            "water-dataset": {
                url: {
                    label: "水域数据集 URL",
                    description: "用于设置船舶行程检测的水域数据集压缩包 URL"
                },
                sha256: {
                    label: "水域数据集校验和",
                    description: "水域数据集压缩包的预期 SHA-256 校验和（可选）"
                },
                "auto-import": {
                    label: "自动导入水域数据",
                    description: "配置船舶行程检测需要水域数据时自动导入数据集"
                },
                "connect-timeout-seconds": {
                    label: "水域数据下载连接超时",
                    description: "连接水域数据集下载地址时等待的秒数"
                },
                "download-timeout-hours": {
                    label: "水域数据下载超时",
                    description: "水域数据集下载的最长耗时"
                },
                "download-stall-timeout-seconds": {
                    label: "水域数据下载停滞超时",
                    description: "连续多少秒未收到数据后判定水域数据下载失败"
                },
                "setup-start-timeout-minutes": {
                    label: "船舶行程检测启动超时",
                    description: "排队的船舶行程检测设置任务超过多少分钟未启动时判定失败"
                }
            },
            notifications: {
                apprise: {
                    enabled: {
                        label: "启用 Apprise 通知",
                        description: "将地理围栏提醒发送到 Apprise 目标"
                    },
                    "api-url": {
                        label: "Apprise API URL",
                        description: "Apprise API 服务的基础 URL，例如 http://apprise-api:8000"
                    },
                    "auth-token": {
                        label: "Apprise API 令牌",
                        description: "用于 Apprise 身份验证的 API 令牌或密钥（可选，加密存储）"
                    },
                    "timeout-ms": {
                        label: "Apprise 超时（毫秒）",
                        description: "向 Apprise 发送通知时的 HTTP 请求超时"
                    },
                    "verify-tls": {
                        label: "验证 TLS 证书",
                        description: "为 Apprise 请求启用 HTTPS 证书验证"
                    }
                },
                "geofence-events": {
                    cleanup: {
                        enabled: {
                            label: "启用地理围栏事件清理",
                            description: "自动清理旧的地理围栏通知事件"
                        }
                    },
                    "retention-days": {
                        label: "保留天数",
                        description: "删除超过此天数的地理围栏通知事件"
                    }
                }
            }
        },
        auth: {
            registration: {
                enabled: {
                    label: "启用注册",
                    description: "允许新用户注册的总开关"
                }
            },
            "password-registration": {
                enabled: {
                    label: "密码注册",
                    description: "允许使用邮箱和密码注册"
                }
            },
            oidc: {
                registration: {
                    enabled: {
                        label: "OIDC 注册",
                        description: "允许通过 OIDC 提供方注册"
                    }
                },
                "auto-link-accounts": {
                    label: "自动关联 OIDC 账户",
                    description: "按电子邮箱自动关联 OIDC 账户（存在安全风险）"
                },
                "callback-base-url": {
                    label: "OIDC 回调基础 URL",
                    description: "用于生成 OIDC 回调跳转地址的公开基础 URL（可选）"
                },
                "rp-logout": {
                    enabled: {
                        label: "同时退出 OIDC 提供方",
                        description: "退出 GeoPulse 时，如果 OIDC 提供方支持，也一并退出该提供方"
                    }
                },
                "jwks-cache": {
                    "ttl-hours": {
                        label: "JWKS 缓存有效期",
                        description: "缓存 OIDC 提供方签名密钥的小时数"
                    }
                },
                cleanup: {
                    "session-states": {
                        enabled: {
                            label: "OIDC 会话清理",
                            description: "清理过期的 OIDC 登录会话状态记录"
                        }
                    }
                },
                login: {
                    enabled: {
                        label: "OIDC 登录",
                        description: "允许通过 OIDC 提供方登录"
                    }
                },
                "auto-launch": {
                    enabled: {
                        label: "自动发起 OIDC 登录",
                        description: "停用密码登录且只有一个 OIDC 提供方时，打开登录页会直接跳转至该提供方。打开 /login?autoLaunch=0 可以查看登录页。"
                    }
                }
            },
            login: {
                enabled: {
                    label: "启用登录",
                    description: "允许用户登录的总开关"
                }
            },
            "password-login": {
                enabled: {
                    label: "密码登录",
                    description: "允许使用邮箱和密码登录"
                }
            },
            "admin-login-bypass": {
                enabled: {
                    label: "管理员登录豁免",
                    description: "允许管理员跳过登录限制，避免无法登录"
                }
            },
            "guest-root-redirect-to-login": {
                enabled: {
                    label: "将访客从根路径转至登录页",
                    description: "未登录用户访问「/」时直接跳转到「/login」"
                }
            }
        },
        ai: {
            logging: {
                enabled: {
                    label: "启用 AI 请求和响应日志",
                    description: "记录详细的 AI 请求和响应以便调试，修改立即生效"
                }
            },
            "chat-memory": {
                "max-messages": {
                    label: "聊天记录容量",
                    description: "为每位用户保留的最大历史消息数"
                }
            },
            "tool-result": {
                "max-length": {
                    label: "工具结果长度上限",
                    description: "限制工具结果的字符数，避免超出令牌上限（12000 个字符约为 3000 个令牌）"
                }
            }
        },
        geocoding: {
            "primary-provider": {
                label: "首选提供方",
                description: "新查询优先使用此地理编码服务，已有缓存记录会在执行校正时更新"
            },
            "fallback-provider": {
                label: "备用提供方",
                description: "仅在新的查询中作为备用，已有缓存记录会继续保留"
            },
            "delay-ms": {
                label: "请求间隔",
                description: "两次地理编码请求之间的间隔（毫秒）"
            },
            nominatim: {
                enabled: {
                    label: "Nominatim",
                    description: "启用 Nominatim 地理编码服务"
                },
                "public-host-forward-search-enabled": {
                    label: "Nominatim 正向搜索",
                    description: "允许通过公共服务 nominatim.openstreetmap.org 搜索及自动补全。自行托管的 Nominatim 始终可用。"
                },
                url: {
                    label: "Nominatim URL",
                    description: "自定义 Nominatim 服务器 URL（可选）"
                },
                language: {
                    label: "Nominatim 语言",
                    description: "语言偏好（BCP 47，例如 en-US、de、uk、ja）"
                }
            },
            photon: {
                enabled: {
                    label: "Photon",
                    description: "启用 Photon 地理编码服务"
                },
                url: {
                    label: "Photon URL",
                    description: "自定义 Photon 服务器 URL（可选）"
                },
                language: {
                    label: "Photon 语言",
                    description: "Photon 语言代码。允许 de、pl、el、en、es、fa、fr、it、ja、ko。留空则使用服务提供方的默认设置。"
                }
            },
            googlemaps: {
                enabled: {
                    label: "Google Maps",
                    description: "启用 Google Maps 地理编码服务"
                },
                "api-key": {
                    label: "Google Maps API 密钥",
                    description: "Google Maps 的 API 密钥（加密存储，输入新值即可更新）"
                },
                language: {
                    label: "Google Maps 语言",
                    description: "支持的语言见 https://developers.google.com/maps/faq#languagesupport"
                }
            },
            mapbox: {
                enabled: {
                    label: "Mapbox",
                    description: "启用 Mapbox 地理编码服务"
                },
                "access-token": {
                    label: "Mapbox 访问令牌",
                    description: "Mapbox 的访问令牌（加密存储，输入新值即可更新）"
                }
            },
            geoapify: {
                enabled: {
                    label: "Geoapify",
                    description: "启用 Geoapify 地理编码服务"
                },
                "api-key": {
                    label: "Geoapify API 密钥",
                    description: "Geoapify 的 API 密钥（加密存储，输入新值即可更新）"
                },
                language: {
                    label: "Geoapify 语言",
                    description: "Geoapify 返回结果的语言偏好（可选）"
                },
                "delay-ms": {
                    label: "Geoapify 请求间隔",
                    description: "两次 Geoapify 请求之间的间隔（毫秒）"
                }
            },
            chibigeo: {
                enabled: {
                    label: "ChibiGeo",
                    description: "启用 ChibiGeo 地理编码服务"
                },
                url: {
                    label: "ChibiGeo URL",
                    description: "与 Photon 兼容的 ChibiGeo URL"
                },
                "api-key": {
                    label: "ChibiGeo API 密钥",
                    description: "ChibiGeo 的 API 密钥（加密存储，输入新值即可更新）"
                },
                language: {
                    label: "ChibiGeo 语言",
                    description: "与 Photon 兼容的语言代码。允许 de、pl、el、en、es、fa、fr、it、ja、ko。留空则使用服务提供方的默认设置。"
                },
                "delay-ms": {
                    label: "ChibiGeo 请求间隔",
                    description: "两次 ChibiGeo 请求之间的间隔（毫秒）"
                }
            },
            cache: {
                "max-bbox-area-km2": {
                    label: "缓存边界框面积上限",
                    description: "匹配地理编码缓存时，服务提供方返回的边界框允许的最大面积"
                }
            },
            reconcile: {
                item: {
                    "max-attempts": {
                        label: "校正尝试次数",
                        description: "单条地理编码缓存记录允许尝试校正的最多次数"
                    }
                },
                "circuit-open-wait-ms": {
                    label: "熔断等待时间",
                    description: "校正期间服务提供方的熔断器打开时，等待的毫秒数"
                },
                "inter-item-delay-ms": {
                    label: "校正间隔",
                    description: "依次校正地理编码记录时，两条记录之间的间隔（毫秒）"
                }
            }
        },
        weather: {
            enabled: {
                label: "启用天气",
                description: "为时间线中的停留和行程提供天气观测记录"
            },
            "primary-provider": {
                label: "首选提供方",
                description: "获取新天气观测记录时优先使用的服务提供方"
            },
            "secondary-provider": {
                label: "备用提供方",
                description: "首选提供方无法返回天气观测记录时使用的备用服务（可选）"
            },
            "open-meteo": {
                enabled: {
                    label: "启用 Open-Meteo",
                    description: "将 Open-Meteo 加入可选天气服务"
                },
                "forecast-url": {
                    label: "天气预报 URL",
                    description: "Open-Meteo 天气预报 API 基础 URL"
                },
                "archive-url": {
                    label: "历史数据 URL",
                    description: "Open-Meteo 历史天气 API 基础 URL"
                },
                "api-key": {
                    label: "Open-Meteo API 密钥",
                    description: "Open-Meteo API 密钥（可选，加密存储）"
                },
                "connect-timeout-seconds": {
                    label: "Open-Meteo 连接超时",
                    description: "连接 Open-Meteo 时等待的秒数"
                },
                "read-timeout-seconds": {
                    label: "Open-Meteo 读取超时",
                    description: "等待 Open-Meteo 响应的秒数"
                }
            },
            pirate: {
                enabled: {
                    label: "启用 Pirate Weather",
                    description: "将 Pirate Weather 加入可选天气服务"
                },
                "base-url": {
                    label: "Pirate Weather 预报 URL",
                    description: "Pirate Weather 天气预报 API 基础 URL"
                },
                "time-machine-url": {
                    label: "Pirate Weather Time Machine URL",
                    description: "Pirate Weather 的 Time Machine 历史天气 API 基础 URL"
                },
                "api-key": {
                    label: "Pirate Weather API 密钥",
                    description: "Pirate Weather API 密钥（加密存储）"
                },
                "connect-timeout-seconds": {
                    label: "Pirate Weather 连接超时",
                    description: "连接 Pirate Weather 时等待的秒数"
                },
                "read-timeout-seconds": {
                    label: "Pirate Weather 读取超时",
                    description: "等待 Pirate Weather 响应的秒数"
                }
            },
            ongoing: {
                enabled: {
                    label: "持续收集天气",
                    description: "为最新且仍在进行的停留和行程创建天气查询目标"
                },
                "interval-minutes": {
                    label: "持续收集间隔",
                    description: "两次天气观测之间的最短间隔，至少 30 分钟"
                }
            },
            backfill: {
                enabled: {
                    label: "补录历史天气",
                    description: "自动查找需要补录的历史天气查询目标"
                }
            },
            quota: {
                "daily-request-limit": {
                    label: "每日请求上限",
                    description: "每个 UTC 自然日允许向天气服务提供方发送的最大请求数"
                },
                "ongoing-reserve": {
                    label: "持续收集预留额度",
                    description: "每天为持续收集天气预留的请求数"
                }
            },
            "coordinate-precision": {
                label: "坐标精度",
                description: "对天气查询位置进行分组时使用的坐标小数位数"
            },
            "failed-target-retry": {
                enabled: {
                    label: "重试失败的查询",
                    description: "经过冷却期后重试仍未完成的天气查询"
                },
                "cooldown-hours": {
                    label: "失败重试冷却期",
                    description: "天气查询失败后，可以重试前需要等待的小时数"
                }
            },
            targets: {
                "completed-retention-days": {
                    label: "已完成记录保留天数",
                    description: "保留已完成的天气查询记录的天数"
                },
                "failed-retention-days": {
                    label: "失败记录保留天数",
                    description: "保留失败的天气查询记录的天数"
                },
                "in-progress-timeout-minutes": {
                    label: "进行中查询超时",
                    description: "天气查询进行超过多少分钟后视为超时"
                }
            }
        },
        "map-matching": {
            enabled: {
                label: "启用地图匹配",
                description: "用户选择启用地图匹配前，必须先打开此全局开关"
            },
            automatic: {
                enabled: {
                    label: "自动匹配后续行程",
                    description: "等待期结束后，为所有用户自动匹配已稳定的新行程"
                },
                "quiet-period-minutes": {
                    label: "等待期",
                    description: "行程结束后，自动匹配开始前等待的分钟数"
                }
            },
            backfill: {
                enabled: {
                    label: "补录历史匹配结果",
                    description: "在后台查找所有用户的历史行程，可以从上次进度继续"
                }
            },
            provider: {
                label: "服务提供方",
                description: "用于优化行程路线的地图匹配引擎"
            },
            valhalla: {
                "base-url": {
                    label: "Valhalla 基础 URL",
                    description: "自行托管的 Valhalla 服务的基础 URL"
                },
                "connect-timeout-seconds": {
                    label: "连接超时",
                    description: "连接 Valhalla 时等待的秒数"
                },
                "read-timeout-seconds": {
                    label: "读取超时",
                    description: "等待 Valhalla 地图匹配响应的秒数"
                }
            },
            "max-input-points": {
                label: "输入点数上限",
                description: "每次行程最多发送给 Valhalla 的 GPS 点数"
            },
            "max-trip-duration-hours": {
                label: "最长行程时长",
                description: "超过此小时数的行程将被跳过"
            },
            worker: {
                "batch-size": {
                    label: "后台批次大小",
                    description: "后台任务每次定时运行时处理的地图匹配目标数"
                }
            },
            "max-attempts": {
                label: "最大尝试次数",
                description: "每个地图匹配目标可重试的最多次数"
            },
            quality: {
                "min-raw-distance-meters": {
                    label: "质量检查距离阈值",
                    description: "原始轨迹片段达到此距离后，进行部分匹配质量检查"
                },
                "min-distance-coverage-percent": {
                    label: "最低匹配覆盖率",
                    description: "匹配距离占原始轨迹片段距离的最低百分比"
                },
                "max-discontinuity-percent": {
                    label: "最大缺口百分比",
                    description: "匹配片段之间的未匹配缺口距离，占原始轨迹片段距离的最大百分比"
                },
                "max-short-discontinuity-meters": {
                    label: "短缺口容许距离",
                    description: "匹配片段之间允许存在的绝对缺口距离下限"
                }
            }
        },
        panoramax: {
            enabled: {
                label: "启用 Panoramax 覆盖范围",
                description: "在时间线地图上显示 Panoramax 街景影像的覆盖范围"
            },
            endpoint: {
                label: "Panoramax STAC 端点",
                description: "用于查询覆盖范围和影像的公共 Panoramax STAC API 端点"
            }
        },
        poi: {
            enabled: {
                label: "启用地点发现",
                description: "规划行程时推荐值得到访的地点，并展示照片"
            },
            "user-agent": {
                label: "User-Agent",
                description: "发送给 Wikidata 和 Commons。请填写可标识客户端的信息，匿名客户端容易被限流或屏蔽。"
            },
            language: {
                label: "首选语言",
                description: "地点名称和描述使用的语言，例如 en、de、uk"
            },
            attribution: {
                enabled: {
                    label: "显示署名",
                    description: "显示照片署名和数据来源，满足 Wikimedia 许可证要求"
                }
            },
            wikidata: {
                endpoint: {
                    label: "Wikidata 端点",
                    description: "Wikidata Query Service 基础 URL。可使用自托管实例以规避公共服务的请求频率限制。"
                }
            },
            commons: {
                endpoint: {
                    label: "Commons 端点",
                    description: "用于查询照片署名的 Wikimedia Commons API 基础 URL"
                },
                "thumb-width": {
                    label: "照片缩略图宽度",
                    description: "请求的缩略图宽度（像素）。缩放由 Commons 执行，宽度越大，耗时越长。"
                }
            },
            "max-results": {
                label: "每个区域的结果上限",
                description: "从 Wikidata 获取单个区域的最大地点数"
            },
            cache: {
                "ttl-days": {
                    label: "地点缓存有效期（天）",
                    description: "再次获取地点数据前，可以复用缓存的天数。延长有效期可减少对共享 API 的请求。"
                },
                "image-ttl-days": {
                    label: "照片缓存有效期（天）",
                    description: "重新获取照片文件前，可以复用缓存的天数"
                }
            }
        },
        import: {
            "bulk-insert-batch-size": {
                label: "批量插入批次大小",
                description: "每批写入数据库的 GPS 点数"
            },
            "merge-batch-size": {
                label: "合并批次大小",
                description: "合并数据并检测重复点时每批处理的条数"
            },
            "large-file-threshold-mb": {
                label: "大文件阈值（MB）",
                description: "超过此大小的文件将保存为临时文件，避免占用内存"
            },
            "temp-file-retention-hours": {
                label: "临时文件保留时间（小时）",
                description: "导入临时文件在清理前保留的时间"
            },
            "drop-folder": {
                enabled: {
                    label: "启用投递目录",
                    description: "允许从投递目录自动导入"
                },
                path: {
                    label: "投递目录路径",
                    description: "自动导入所用目录的文件系统路径"
                },
                "poll-interval-seconds": {
                    label: "投递目录扫描间隔（秒）",
                    description: "两次扫描投递目录之间的间隔"
                },
                "stable-age-seconds": {
                    label: "投递文件最短等待时间（秒）",
                    description: "文件放入目录后，开始导入前至少等待的时间"
                },
                "geopulse-max-size-mb": {
                    label: "投递目录 GeoPulse 文件大小上限（MB）",
                    description: "投递目录允许导入的 GeoPulse ZIP 文件大小上限"
                },
                "runtime-identity": {
                    label: "投递目录进程身份",
                    description: "运行后端进程的有效用户和组（只读）"
                }
            },
            "chunk-size-mb": {
                label: "分块大小（MB）",
                description: "上传大文件时每个分块的大小"
            },
            "max-file-size-gb": {
                label: "文件大小上限（GB）",
                description: "允许导入的文件大小上限"
            },
            "upload-timeout-hours": {
                label: "上传超时（小时）",
                description: "上传会话的有效时长"
            },
            "transaction-timeout-minutes": {
                label: "导入事务超时",
                description: "单次导入任务的最长事务时长"
            },
            "upload-cleanup-minutes": {
                label: "上传清理间隔",
                description: "清理过期分块上传会话的频率"
            },
            geonames: {
                cities: {
                    enabled: {
                        label: "启用城市数据导入",
                        description: "允许导入 GeoNames 城市数据集"
                    },
                    url: {
                        label: "城市数据集 URL",
                        description: "GeoNames 城市数据集 ZIP 压缩包的 URL"
                    },
                    "batch-size": {
                        label: "城市数据批次大小",
                        description: "导入 GeoNames 城市数据时每批处理的行数"
                    },
                    "min-row-threshold": {
                        label: "城市数据行数阈值",
                        description: "替换数据前，暂存的 GeoNames 城市数据至少需要达到的行数"
                    },
                    "force-refresh": {
                        label: "强制刷新城市数据",
                        description: "即使现有城市数据已达到阈值，也重新导入"
                    },
                    "connect-timeout-seconds": {
                        label: "城市数据下载连接超时",
                        description: "连接 GeoNames 城市数据下载地址时等待的秒数"
                    },
                    "read-timeout-seconds": {
                        label: "城市数据下载读取超时",
                        description: "读取 GeoNames 城市数据下载内容时等待的秒数"
                    }
                },
                countries: {
                    enabled: {
                        label: "启用国家数据导入",
                        description: "允许导入 GeoNames 国家数据集"
                    },
                    url: {
                        label: "国家数据集 URL",
                        description: "GeoNames 国家数据集的 URL"
                    },
                    "batch-size": {
                        label: "国家数据批次大小",
                        description: "导入 GeoNames 国家数据时每批处理的行数"
                    },
                    "min-row-threshold": {
                        label: "国家数据行数阈值",
                        description: "替换数据前，暂存的 GeoNames 国家数据至少需要达到的行数"
                    },
                    "force-refresh": {
                        label: "强制刷新国家数据",
                        description: "即使现有国家数据已达到阈值，也重新导入"
                    },
                    "connect-timeout-seconds": {
                        label: "国家数据下载连接超时",
                        description: "连接 GeoNames 国家数据下载地址时等待的秒数"
                    },
                    "read-timeout-seconds": {
                        label: "国家数据下载读取超时",
                        description: "读取 GeoNames 国家数据下载内容时等待的秒数"
                    }
                }
            },
            "geojson-streaming-batch-size": {
                label: "GeoJSON 批次大小",
                description: "流式解析 GeoJSON 时每批处理的条数"
            },
            "googletimeline-streaming-batch-size": {
                label: "Google 时间线批次大小",
                description: "流式解析 Google 时间线时每批处理的条数"
            },
            "gpx-streaming-batch-size": {
                label: "GPX 批次大小",
                description: "流式解析 GPX 时每批处理的条数"
            },
            "csv-streaming-batch-size": {
                label: "CSV 批次大小",
                description: "流式解析 CSV 时每批处理的条数"
            },
            "owntracks-streaming-batch-size": {
                label: "OwnTracks 批次大小",
                description: "流式解析 OwnTracks 时每批处理的条数"
            }
        },
        export: {
            "max-jobs-per-user": {
                label: "每位用户的任务上限",
                description: "一位用户同时可拥有的最大导出任务数"
            },
            "job-expiry-hours": {
                label: "任务保留时间（小时）",
                description: "导出任务完成后，到自动删除前保留的小时数"
            },
            "concurrent-jobs-limit": {
                label: "并发任务上限",
                description: "可同时处理的最大导出任务数"
            },
            "batch-size": {
                label: "批次大小",
                description: "导出时每批处理的记录数"
            },
            "trip-point-limit": {
                label: "单次行程点数上限",
                description: "导出单次行程时允许的最大 GPS 点数"
            },
            "temp-file-retention-hours": {
                label: "临时文件保留时间（小时）",
                description: "导出临时文件在清理前保留的时间"
            }
        },
        backup: {
            scheduled: {
                enabled: {
                    label: "计划备份",
                    description: "启用自动完整备份，并将文件写入本地备份目录"
                },
                cron: {
                    label: "备份计划 Cron 表达式",
                    description: "用于安排自动完整备份的 Cron 表达式"
                }
            },
            local: {
                path: {
                    label: "备份目录路径",
                    description: "后端容器中用来保存本地完整备份 ZIP 文件的目录"
                }
            },
            retention: {
                count: {
                    label: "备份保留数量",
                    description: "保留的本地完整备份文件数量"
                }
            },
            operation: {
                "timeout-minutes": {
                    label: "备份超时（分钟）",
                    description: "完整备份和恢复操作允许的最长时长"
                }
            }
        }
    },
    unitOptions: {
        distanceKilometers: "公里（km、m）",
        distanceMiles: "英里（mi、ft）",
        temperatureCelsius: "摄氏度（°C）",
        temperatureFahrenheit: "华氏度（°F）"
    },
    shell: {
        readOnly: "只读",
        default: "默认",
        reset: "重置",
        plannedFeatures: "计划中的功能",
        comingSoon: "即将推出"
    },
    gpsProcessingTab: {
        title: "GPS 处理设置",
        description: "配置 GPS 数据的默认处理方式",
        features: {
            stayDetection: {
                name: "停留检测算法",
                description: "根据 GPS 点检测停留的默认算法"
            },
            accuracyFiltering: {
                name: "定位误差过滤",
                description: "处理数据所需的最低定位精度"
            },
            batchSize: {
                name: "批次大小",
                description: "每批处理的 GPS 点数"
            },
            distanceThresholds: {
                name: "距离阈值",
                description: "配置停留和行程的距离参数"
            },
            timeWindows: {
                name: "时间窗口",
                description: "检测停留所需的最短和最长时间"
            }
        }
    },
    panoramaxTab: {
        testEndpoint: "测试端点",
        testSuccess: "已找到 Panoramax STAC 矢量瓦片",
        testFailedFallback: "端点测试失败"
    },
    authenticationTab: {
        title: "身份验证设置",
        oidcAdvanced: "OIDC 高级设置"
    },
    exportTab: {
        jobManagement: "任务管理",
        batchProcessing: "批处理",
        tempFileStorage: "临时文件存储"
    },
    poiTab: {
        testEndpoints: "测试端点",
        advancedSettings: "高级设置",
        endpointsLimitsCaching: "端点、限制和缓存",
        advancedHint: "端点可以指向自行托管的实例。延长缓存有效期可减少对公共服务的请求。",
        wikidataOk: "Wikidata 连接正常",
        wikidataFailed: "Wikidata 连接失败：{detail}",
        commonsOk: "Commons 连接正常",
        commonsFailed: "Commons 连接失败：{detail}",
        testFailedFallback: "端点测试失败"
    },
    systemTab: {
        selectDefaultUnit: "选择默认单位",
        observability: "可观测性",
        applicationLogging: "应用日志",
        useEnvironmentDefault: "使用环境变量或默认值",
        selectLogLevel: "选择日志级别",
        configuredLabel: "配置值",
        effectiveLabel: "生效值",
        sourceLabel: "来源",
        debugLoggingWarning: "DEBUG 日志内容较多，生产环境中只应临时启用。",
        updateCheck: "检查更新",
        releaseMetadata: "发布元数据",
        waterDataset: "水域数据集",
        datasetSource: "数据集来源",
        noSettingsTitle: "暂无常规系统设置",
        noSettingsDescription: "通知设置已移至「通知」选项卡。"
    },
    backupTab: {
        tabs: {
            ariaLabel: "备份选项卡",
            fullDatabaseBackup: "完整数据库备份",
            adminSettings: "管理员设置"
        },
        exportSection: {
            title: "导出管理员设置（JSON）",
            subtitle: "备份管理员管理的设置和服务提供方凭据。该文件不包含数据库、用户或 GPS 历史记录。",
            panelTitle: "导出配置",
            panelDescription: "下载包含管理员设置及服务提供方凭据的 JSON 文件，便于迁移。",
            button: "导出设置"
        },
        importSection: {
            panelTitle: "导入设置",
            panelDescription: "从 JSON 备份中恢复并替换当前管理员设置。",
            chooseFile: "选择备份文件",
            button: "导入设置"
        },
        warnMessage: "导出的设置文件包含明文 API 密钥、OIDC 客户端密钥、令牌和自定义提供方的请求头。",
        infoMessage: "请使用完整数据库备份来备份用户、GPS 历史记录和数据库内容。此 JSON 文件只包含管理员设置。",
        importDialog: {
            header: "替换管理员设置？",
            message: "导入此文件将替换当前全局设置、OIDC 提供方和自定义地理编码提供方。",
            note: "此操作不影响部署基础设施和用户数据导出。",
            import: "导入"
        },
        toasts: {
            exportStarted: "已开始导出",
            exportStartedDetail: "配置文件已准备就绪，浏览器已开始下载。",
            exportFailed: "导出失败",
            exportFailedFallback: "无法导出管理员设置备份",
            importComplete: "导入完成",
            importCompleteDetail: "已恢复 {settings} 项设置、{oidc} 个 OIDC 提供方和 {custom} 个自定义地理编码提供方。",
            importFailed: "导入失败",
            importFailedFallback: "无法导入管理员设置备份"
        }
    },
    fullBackupSection: {
        title: "完整数据库备份",
        subtitle: "使用密码加密的 PostgreSQL 备份，包含数据库内容和机密信息。",
        passwordSection: {
            title: "备份加密密码",
            description: "用于加密每份新建的完整备份。下载或创建备份前必须设置密码。",
            configured: "已设置备份密码。",
            notConfigured: "尚未设置备份密码。请先设置密码，再创建备份。",
            setButton: "设置密码",
            changeButton: "更改密码",
            changeNote: "请在 GeoPulse 外妥善保管密码，丢失后无法找回。更改密码只影响新备份。"
        },
        passwordDialog: {
            titleSet: "设置备份密码",
            titleChange: "更改备份密码",
            description: "此密码用于加密新的完整备份。请在 GeoPulse 外妥善保管，丢失后无法找回。",
            passwordLabel: "新密码",
            confirmLabel: "确认新密码",
            lengthHint: "请输入 12 至 1024 个字符。",
            save: "保存密码",
            saving: "保存中",
            mismatch: "两次输入的备份密码不一致。",
            lengthError: "备份密码必须包含 12 至 1024 个字符。",
            required: "请输入备份密码。"
        },
        downloadPanel: {
            title: "下载完整备份",
            description: "生成临时加密压缩包供下载。传输完成后会删除该文件，本地备份列表中也不会保留。",
            button: "下载完整备份"
        },
        runNowPanel: {
            title: "创建本地备份",
            description: "在指定的服务器目录中保存加密备份。该文件会列在本地备份中，并计入保留数量。",
            button: "创建本地备份"
        },
        warnMessage: "完整备份使用备份密码加密。请在 GeoPulse 外妥善保管密码，丢失密码后无法恢复备份。请只恢复来源可信的备份。准备恢复时服务仍可使用，启用恢复结果时会短暂停止 GeoPulse，并自动重启后端。",
        restorePreparationFailedTitle: "准备恢复失败",
        restorePreparationFailedFallback: "准备恢复失败。",
        originalDatabaseActive: "原始数据库仍在运行，尚未应用恢复数据。",
        backupFileLabel: "备份文件：{fileName}",
        activationRetryableFallback: "未能启用恢复结果。原始数据库仍在运行，已准备好的恢复数据已保留。",
        retryActivation: "重试启用",
        discardPreparedRestore: "丢弃已准备的恢复数据",
        progress: {
            restoreInProgress: "正在恢复",
            restorePreparationFailed: "准备恢复失败",
            backupInProgress: "正在备份",
            lastBackupCompleted: "上次备份已完成",
            lastBackupFailed: "上次备份失败",
            backupStatus: "备份状态",
            backupPhaseSnapshot: "正在创建一致的数据库快照…",
            backupPhaseDumping: "正在转储数据库数据…",
            backupPhaseFinalizing: "正在加密并生成备份文件…",
            elapsed: "已用时：{duration}",
            bytesWritten: "已写入 {size}",
            downloadInProgress: "正在下载备份",
            downloadKeepPageOpen: "请保持此页面打开。如果浏览器询问是否保留文件，请选择「保留」。",
            downloadTransferred: "已传输 {transferred}，共 {size}",
            restorePreparingMessage: "正在后台准备恢复。启用恢复结果前，GeoPulse 仍可正常使用。",
            waitingForStatus: "正在等待状态"
        },
        scheduledSection: {
            title: "本地备份计划",
            description: "配置自动备份，将文件写入已挂载的备份目录。",
            passwordRequiredHint: "计划备份使用上方设置的备份加密密码。",
            schedule: "计划",
            enabled: "已启用",
            cron: "Cron 表达式",
            storage: "存储",
            folderPath: "文件夹路径",
            backupsToKeep: "保留备份数",
            timeoutMinutes: "超时时间（分钟）",
            healthAlerts: "备份状态提醒",
            warnAfterDays: "超过指定天数后提醒",
            healthWarningHint: "设为 0 可关闭备份过期提醒和通知。启用后，状态为活动的管理员会收到应用内通知。",
            sendExternalAlert: "发送外部提醒",
            appriseRouting: "Apprise 发送方式",
            appriseRoutingOptions: {
                destinationUrls: "目标 URL",
                configKeyAndTag: "配置键和标签"
            },
            configKey: "配置键",
            destinationUrls: "目标 URL",
            tagOptional: "标签（可选）",
            saveButton: "保存备份设置"
        },
        localBackups: {
            title: "本地备份",
            description: "已配置的服务器备份目录中的文件。",
            refreshAriaLabel: "刷新本地备份",
            columnFile: "文件",
            columnSize: "大小",
            columnModified: "修改时间",
            columnActions: "操作",
            downloadAriaLabel: "下载备份",
            restoreAriaLabel: "恢复备份",
            deleteAriaLabel: "删除备份"
        },
        restoreUploadSection: {
            title: "上传并恢复完整备份",
            description: "上传加密的 .gpb 备份。GeoPulse 会在后台准备恢复，期间仍可使用。启用恢复结果后会替换较新的数据，并重启后端。",
            chooseFile: "选择完整备份",
            button: "恢复已上传的备份"
        },
        restoreDialog: {
            header: "恢复完整备份？",
            warning: "恢复完整备份可能会替换用户、应用设置、GPS 数据、好友关系和权限。",
            note: "准备恢复期间，GeoPulse 仍可使用。启用恢复结果时会暂时暂停应用操作，替换较新的数据，并自动重启后端。",
            sourcePasswordLabel: "原备份密码",
            restore: "恢复",
            restoring: "恢复中"
        },
        deleteDialog: {
            header: "删除备份？",
            message: "要从已配置的本地备份目录中删除 {fileName} 吗？",
            note: "此操作只会删除服务器上的备份文件。",
            delete: "删除"
        },
        toasts: {
            loadFilesFailed: "加载失败",
            loadFilesFailedFallback: "无法加载本地备份",
            loadConfigFailedFallback: "无法加载备份设置",
            downloadStarted: "已开始下载",
            downloadStartedDetail: "加密压缩包已准备就绪，浏览器已开始下载。",
            downloadComplete: "下载完成",
            downloadCompleteDetail: "备份压缩包已下载。如果浏览器询问是否保留文件，请选择「保留」。",
            downloadCompleteBanner: "已下载 {fileName}。如果浏览器询问是否保留文件，请选择「保留」。",
            downloadInterrupted: "下载中断",
            downloadInterruptedDetail: "文件未下载完整。请重试，并保持此页面打开直至下载完成。",
            downloadInterruptedBanner: "下载中断，文件未下载完整。请重试，并保持此页面打开直至下载完成。",
            downloadFailed: "下载失败",
            downloadFailedFallback: "无法下载完整备份",
            downloadNotStarted: "下载未开始",
            downloadNotStartedDetail: "服务器未收到备份请求。请检查登录状态后重试。",
            downloadConflict: "已有备份任务在运行",
            downloadConflictDetail: "请等待当前备份或恢复操作结束后重试。",
            localDownloadStartedDetail: "浏览器正在下载 {fileName}。",
            passwordSaved: "密码已保存",
            passwordSavedDetail: "备份加密密码已更新，仅对新备份生效。",
            passwordSaveFailedFallback: "无法保存备份密码",
            backupComplete: "备份完成",
            backupCompleteDetail: "已创建 {fileName}",
            backupFailed: "备份失败",
            backupFailedFallback: "无法执行备份",
            saveFailed: "保存失败",
            passwordsDoNotMatch: "两次输入的备份密码不一致",
            passwordLengthError: "新备份密码必须包含 12 至 1024 个字符",
            saveConfigFailedFallback: "无法保存备份设置",
            settingsSaved: "设置已保存",
            settingsSavedDetail: "备份设置已更新。",
            downloadLocalFailedFallback: "无法下载本地备份",
            backupDeleted: "备份已删除",
            backupDeletedDetail: "已删除 {fileName}",
            deleteFailed: "删除失败",
            deleteFailedFallback: "无法删除备份",
            restoreFailed: "恢复失败",
            restorePasswordLengthError: "恢复密码必须包含 1 至 1024 个字符",
            restoreFailedFallback: "无法恢复完整备份",
            activationRetryFailed: "重试启用失败",
            activationRetryFailedFallback: "再次尝试启用恢复结果时失败",
            discardFailed: "丢弃失败",
            discardFailedFallback: "无法丢弃已准备的恢复数据"
        }
    },
    composable: {
        loadFailedSummary: "错误",
        loadFailedDetail: "无法加载{category}设置",
        validationErrorSummary: "验证错误",
        updateSuccessSummary: "成功",
        settingUpdatedDetail: "已更新{label}",
        updateErrorSummary: "错误",
        updateFailedFallback: "无法更新设置",
        resetSuccessDetail: "{label}已恢复为默认值",
        resetFailedDetail: "无法重置设置"
    }
}
