import SwiftUI
import WebKit

// MARK: - Cloudflare Turnstile WebView

/// Turnstile 人机验证 WebView 组件
///
/// 通过 WKWebView 渲染 Cloudflare Turnstile 无感验证组件，
/// 与 Web 前端 LoginView.vue 的 Turnstile 集成完全对齐。
///
/// 配置参数：
/// - sitekey: 与 Web 前端 VITE_TURNSTILE_SITE_KEY 一致
/// - action: 'login'
/// - theme: 'light'
/// - size: 'flexible'
/// - execution: 'execute'（主动调用 execute 触发验证）
/// - appearance: 'interaction-only'（无感验证，用户无感知）
///
/// 触发机制：
/// - 外部通过 `triggerExecute` 绑定通知组件执行 Turnstile.execute()
/// - 组件检测到 `triggerExecute` 变为 true 时调用 JS 的 triggerTurnstileExecute()
/// - 执行后通过 `onExecuteCompleted` 回调通知外部
///
/// 交互流程：
/// 1. 用户输入合法用户名 → 密码输入框启用
/// 2. 用户聚焦密码输入框 → 设置 triggerExecute = true
/// 3. Turnstile 回调 → 通过 WebKit messageHandler 传递 token 给 Swift
/// 4. 获取 token 后 → 登录按钮启用
/// 5. token 过期或错误 → 自动通知，登录按钮禁用
struct TurnstileWebView: NSViewRepresentable {

    // MARK: - 绑定

    /// 外部触发 execute 的信号（设为 true 触发，组件执行后设为 false）
    @Binding var triggerExecute: Bool
    /// 外部触发 reset 的信号
    @Binding var triggerReset: Bool

    // MARK: - 回调

    /// 验证成功回调，返回 token
    var onTokenReceived: ((String) -> Void)?
    /// 验证过期回调
    var onTokenExpired: (() -> Void)?
    /// 验证错误回调
    var onError: ((String) -> Void)?
    /// execute 执行完成回调
    var onExecuteCompleted: (() -> Void)?

    // MARK: - NSViewRepresentable

    func makeCoordinator() -> Coordinator {
        Coordinator(
            onTokenReceived: onTokenReceived,
            onTokenExpired: onTokenExpired,
            onError: onError
        )
    }

    func makeNSView(context: Context) -> WKWebView {
        let contentController = WKUserContentController()

        // 注册 JavaScript 消息处理器
        contentController.add(context.coordinator, name: TurnstileMessageHandler.callback.rawValue)
        contentController.add(context.coordinator, name: TurnstileMessageHandler.expired.rawValue)
        contentController.add(context.coordinator, name: TurnstileMessageHandler.error.rawValue)

        let config = WKWebViewConfiguration()
        config.userContentController = contentController
        config.websiteDataStore = .nonPersistent() // 不持久化数据，避免缓存状态

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.setValue(false, forKey: "drawsBackground") // 透明背景
        webView.allowsMagnification = false
        webView.allowsBackForwardNavigationGestures = false

        if #available(macOS 13.3, *) {
            webView.isInspectable = true
        } else {
            // 对于 macOS 13.0 ~ 13.2，使用私有方法（仅用于调试）
            webView.setValue(true, forKey: "inspectable")
        }

        // 加载 HTML
        webView.loadHTMLString(TurnstileHTMLBuilder.build(), baseURL: URL(string: "http://localhost")!)
        //webView.load(URLRequest(url: URL(string: "http://localhost:5500/test.html")!))

        context.coordinator.webView = webView
        return webView
    }

    func updateNSView(_ nsView: WKWebView, context: Context) {
        // 更新回调引用
        context.coordinator.onTokenReceived = onTokenReceived
        context.coordinator.onTokenExpired = onTokenExpired
        context.coordinator.onError = onError

        // 处理 execute 触发
        if triggerExecute {
            DispatchQueue.main.async {
                self.triggerExecute = false
            }
            nsView.evaluateJavaScript("triggerTurnstileExecute()") { _, error in
                if let error = error {
                    print("[Turnstile] execute 失败: \(error.localizedDescription)")
                }
                onExecuteCompleted?()
            }
        }

        // 处理 reset 触发
        if triggerReset {
            DispatchQueue.main.async {
                self.triggerReset = false
            }
            nsView.evaluateJavaScript("resetTurnstile()", completionHandler: nil)
        }
    }

    // MARK: - Coordinator

    final class Coordinator: NSObject, WKScriptMessageHandler {

        var onTokenReceived: ((String) -> Void)?
        var onTokenExpired: (() -> Void)?
        var onError: ((String) -> Void)?
        weak var webView: WKWebView?

        init(
            onTokenReceived: ((String) -> Void)?,
            onTokenExpired: (() -> Void)?,
            onError: ((String) -> Void)?
        ) {
            self.onTokenReceived = onTokenReceived
            self.onTokenExpired = onTokenExpired
            self.onError = onError
        }

        func userContentController(
            _ userContentController: WKUserContentController,
            didReceive message: WKScriptMessage
        ) {
            guard let handler = TurnstileMessageHandler(rawValue: message.name) else { return }

            DispatchQueue.main.async { [weak self] in
                switch handler {
                case .callback:
                    if let token = message.body as? String, !token.isEmpty {
                        self?.onTokenReceived?(token)
                    }
                case .expired:
                    self?.onTokenExpired?()
                case .error:
                    if let errorMsg = message.body as? String {
                        self?.onError?(errorMsg)
                    } else {
                        self?.onError?("验证组件加载失败，请刷新或稍后重试")
                    }
                }
            }
        }
    }
}

// MARK: - 消息处理器标识

private enum TurnstileMessageHandler: String {
    case callback = "turnstileCallback"
    case expired  = "turnstileExpired"
    case error    = "turnstileError"
}

// MARK: - HTML 构建器

/// 构建 Turnstile Widget 的 HTML 页面
private enum TurnstileHTMLBuilder {

    static func build() -> String {
        """
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Turnstile</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                html, body {
                    width: 100%; height: 100%;
                    background: transparent;
                    overflow: hidden;
                }
                body {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                #turnstile-container {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                iframe { border: none !important; }
            </style>
        </head>
        <body>
            <div id="turnstile-container"></div>

            <script>
            // ── Turnstile 状态 ──
            var turnstileWidgetId = null;
            var turnstileToken = null;

            // ── 配置 ──
            var TURNSTILE_CONFIG = {
                sitekey: '\(TurnstileConfig.siteKey)',
                action: '\(TurnstileConfig.action)',
                theme: '\(TurnstileConfig.theme)',
                size: '\(TurnstileConfig.size)',
                execution: '\(TurnstileConfig.execution)',
                appearance: '\(TurnstileConfig.appearance)'
            };

            // ── 加载 Turnstile API 脚本 ──
            function loadTurnstileScript() {
                return new Promise(function(resolve, reject) {
                    if (window.turnstile) {
                        resolve();
                        return;
                    }
                    var script = document.createElement('script');
                    script.src = '\(TurnstileConfig.scriptURL)';
                    script.async = true;
                    script.defer = true;
                    script.onload = function() { resolve(); };
                    script.onerror = function() {
                        reject(new Error('Turnstile script load failed'));
                    };
                    document.head.appendChild(script);
                });
            }

            // ── 渲染 Turnstile Widget ──
            async function renderTurnstile() {
                try {
                    await loadTurnstileScript();

                    if (!window.turnstile) {
                        sendError('Turnstile API 不可用');
                        return;
                    }

                    var container = document.getElementById('turnstile-container');
                    if (!container) {
                        sendError('Turnstile 容器未找到');
                        return;
                    }

                    turnstileWidgetId = window.turnstile.render('#turnstile-container', {
                        sitekey: TURNSTILE_CONFIG.sitekey,
                        action: TURNSTILE_CONFIG.action,
                        theme: TURNSTILE_CONFIG.theme,
                        size: TURNSTILE_CONFIG.size,
                        execution: TURNSTILE_CONFIG.execution,
                        appearance: TURNSTILE_CONFIG.appearance,
                        callback: function(token) {
                            turnstileToken = token;
                            window.webkit.messageHandlers.turnstileCallback.postMessage(token);
                        },
                        'expired-callback': function() {
                            turnstileToken = null;
                            window.webkit.messageHandlers.turnstileExpired.postMessage('expired');
                        },
                        'error-callback': function() {
                            turnstileToken = null;
                            window.webkit.messageHandlers.turnstileError.postMessage('验证组件加载失败');
                        }
                    });
                } catch (e) {
                    sendError(e.message || 'Turnstile 初始化失败');
                }
                return null;   // 函数末尾统一返回 null
            }

            // ── 触发 Turnstile 验证（主动调用 execute） ──
            function triggerTurnstileExecute() {
                if (turnstileToken) {
                    // 已有有效 token，直接通知 Swift
                    window.webkit.messageHandlers.turnstileCallback.postMessage(turnstileToken);
                    return null;
                }
                if (window.turnstile && turnstileWidgetId !== null) {
                    try {
                        window.turnstile.execute(turnstileWidgetId);
                    } catch (e) {
                        sendError('验证触发失败: ' + e.message);
                    }
                } else {
                    // Widget 尚未渲染完成，稍后重试
                    setTimeout(function() {
                        if (window.turnstile && turnstileWidgetId !== null) {
                            try {
                                window.turnstile.execute(turnstileWidgetId);
                            } catch (e) {
                                sendError('验证触发失败: ' + e.message);
                            }
                        } else {
                            sendError('验证组件尚未就绪，请稍后重试');
                        }
                    }, 500);
                }
                return null;   // 函数末尾统一返回 null
            }

            // ── 重置 Turnstile ──
            function resetTurnstile() {
                turnstileToken = null;
                if (window.turnstile && turnstileWidgetId !== null) {
                    try {
                        window.turnstile.reset(turnstileWidgetId);
                    } catch (e) {
                        // 忽略重置错误
                    }
                }
                return null;   // 函数末尾统一返回 null
            }

            // ── 发送错误到 Swift ──
            function sendError(message) {
                window.webkit.messageHandlers.turnstileError.postMessage(message);
            }

            // ── 页面加载完成后渲染 Turnstile ──
            document.addEventListener('DOMContentLoaded', function() {
                renderTurnstile();
            });
            </script>
        </body>
        </html>
        """
    }
}
