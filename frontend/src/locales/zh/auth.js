export default {
    validation: {
        emailRequired: "请输入邮箱地址",
        emailInvalid: "请输入有效的邮箱地址",
        passwordRequired: "请输入密码",
        confirmRequired: "请再次输入密码",
        mismatch: "两次输入的密码不一致"
    },
    toasts: {
        registrationDisabled: "注册已关闭",
        registrationDisabledDetail: "目前暂停新用户注册。已有账户的用户仍可登录。"
    },
    oidc: {
        continueWith: "使用 {provider} 继续",
        dividerDefault: "或使用以下方式继续",
        providerIconAlt: "{provider} 图标"
    },
    callback: {
        processingTitle: "正在完成身份验证…",
        processingDescription: "正在安全登录，请稍候。",
        errorTitle: "身份验证失败",
        returnToLogin: "返回登录页",
        missingParams: "回调请求无效，缺少必填参数。",
        genericError: "身份验证时发生未知错误。",
        toasts: {
            welcomeNew: "欢迎使用 GeoPulse！",
            welcomeBack: "欢迎回来！",
            authenticated: "身份验证成功",
            linked: {
                title: "账户关联成功",
                detail: "账户已关联，你已成功登录。"
            }
        }
    },
    linking: {
        dialogHeader: "关联账户",
        title: "账户已存在",
        description: "邮箱 {email} 已注册账户。请先验证身份，然后关联你的 {provider} 账户。",
        verifyPassword: "使用密码验证",
        linkAndContinue: "关联账户并继续",
        verifyWithLinked: "使用已关联的账户验证",
        or: "或",
        cancel: "取消",
        errors: {
            passwordVerificationFailed: "密码验证失败"
        }
    },
    login: {
        disabledMessage: "登录功能已关闭，请联系管理员。",
        emailDisabledMessage: "邮箱和密码登录已关闭，请使用下方的 OIDC 提供方登录。",
        autoLaunchRedirecting: "正在转到 {provider}…",
        adminAccess: "管理员登录",
        adminBypassNotice: "管理员登录不受常规登录限制",
        demoTitle: "体验 GeoPulse",
        title: "欢迎回来",
        demoSubtitle: "选择一个演示账户",
        subtitle: "登录以继续记录你的行程",
        emailLabel: "邮箱地址",
        emailPlaceholder: "输入邮箱地址",
        passwordLabel: "密码",
        passwordPlaceholder: "输入密码",
        signIn: "登录",
        noAccount: "还没有账户？",
        createAccount: "创建账户",
        copyReferenceAria: "复制错误参考 ID",
        toasts: {
            signedIn: {
                title: "欢迎回来！",
                detail: "你已成功登录"
            },
            demoFailed: "演示账户登录失败",
            loginFailed: "登录失败",
            optionsLoadFailed: "无法加载登录选项",
            optionsLoadFailedDetail: "无法获取外部登录提供方。你仍可使用邮箱和密码登录。",
            oidcInitFailed: "无法通过 {provider} 发起登录，请重试。"
        },
        errors: {
            invalidCredentials: "邮箱或密码不正确，请检查后重试。",
            lockedOut: "账户已锁定或停用，请联系支持人员。",
            tooManyAttempts: "登录尝试次数过多，请等待几分钟后重试。",
            badFormat: "请检查邮箱和密码格式。",
            chooseProfile: "请选择一个演示账户，然后重试。",
            demoDisabled: "演示登录已关闭。",
            profileUnavailable: "此演示账户暂不可用。",
            demoGeneric: "演示登录失败，请重试。"
        }
    },
    register: {
        disabledMessage: "邮箱和密码注册已关闭，请使用 OIDC 注册。",
        disabledMessageShort: "邮箱和密码注册已关闭。",
        title: "创建账户",
        subtitle: "注册 GeoPulse，开始记录和分析你的出行轨迹",
        emailLabel: "邮箱地址",
        fullNameLabel: "姓名",
        passwordLabel: "密码",
        confirmPasswordLabel: "确认密码",
        emailPlaceholder: "输入邮箱地址",
        fullNamePlaceholder: "输入姓名",
        passwordPlaceholder: "设置密码",
        confirmPasswordPlaceholder: "再次输入密码",
        createAccount: "创建账户",
        alreadyHaveAccount: "已有账户？",
        signIn: "登录",
        localisationNote: "已根据浏览器自动识别，你可以稍后在个人设置中更改。",
        timezoneLabel: "时区",
        validation: {
            fullNameRequired: "请输入姓名",
            fullNameTooShort: "姓名至少需要 2 个字符",
            passwordTooShort: "密码至少需要 6 个字符"
        },
        toasts: {
            created: {
                title: "欢迎使用 GeoPulse！",
                detail: "你的账户已创建成功"
            },
            failed: "注册失败",
            optionsLoadFailed: "无法加载注册选项",
            optionsLoadFailedDetail: "无法获取注册配置。",
            oidcInitFailed: "无法通过 {provider} 发起注册，请重试。",
            signInRequired: {
                title: "账户已创建",
                detail: "账户已准备就绪，请登录后继续。"
            }
        },
        errors: {
            emailExists: "此邮箱已注册",
            checkInformation: "请检查所填信息后重试",
            serverError: "服务器出错，请稍后重试",
            failed: "注册失败，请重试。",
            emailTooLong: "邮箱地址过长，最多允许 {max} 个字符",
            fullNameLength: "姓名长度应为 {min} 至 {max} 个字符",
            passwordLength: "密码长度应为 {min} 至 {max} 个字符"
        }
    },
    invitation: {
        brand: "GeoPulse",
        title: "完成注册",
        validating: "正在验证邀请…",
        invalidTitle: "邀请无效",
        invalidMessage: "此邀请无效",
        emailLabel: "邮箱地址",
        fullNameLabel: "姓名",
        passwordLabel: "密码",
        confirmPasswordLabel: "确认密码",
        timezoneLabel: "时区",
        emailPlaceholder: "输入邮箱地址",
        fullNamePlaceholder: "输入姓名",
        passwordPlaceholder: "输入密码",
        confirmPasswordPlaceholder: "再次输入密码",
        createAccount: "创建账户",
        goToLogin: "前往登录",
        timezoneDetected: "已识别时区：{timezone}",
        loginLink: "已有账户？去登录",
        validation: {
            emailInvalid: "邮箱地址格式无效",
            passwordTooShort: "密码至少需要 3 个字符"
        },
        toasts: {
            created: {
                title: "欢迎使用 GeoPulse！",
                detail: "你的账户已创建成功"
            }
        },
        status: {
            used: "此邀请已被使用",
            expired: "此邀请已过期",
            revoked: "此邀请已被撤销"
        },
        errors: {
            validateFailed: "无法验证邀请",
            notFound: "此邀请链接无效，请向管理员索取新链接。"
        }
    }
}
