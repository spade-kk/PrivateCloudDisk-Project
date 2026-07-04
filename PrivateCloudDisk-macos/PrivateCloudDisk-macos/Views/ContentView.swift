import SwiftUI
import AppKit

// MARK: - ContentView（企业级品牌启动体验）

/// 应用根视图
///
/// 启动流程：
/// 1. 品牌启动页（粒子+光晕+品牌动画）
/// 2. 异步初始化服务
/// 3. 根据认证状态显示 LoginView 或 MainView
struct ContentView: View {
    @EnvironmentObject var authService: AuthService
    @EnvironmentObject var virtualDiskManager: VirtualDiskManager

    @State private var splashPhase: SplashPhase = .entering
    @State private var isReady = false
    @State private var logoScale: CGFloat = 0.5
    @State private var logoOpacity: Double = 0
    @State private var taglineOpacity: Double = 0
    @State private var progressOpacity: Double = 0
    @State private var exitOpacity: Double = 1
    @State private var initProgress: Double = 0
    @State private var bgOffset: CGSize = .zero

    @State private var particles: [SplashParticle] = []

    enum SplashPhase { case entering, displaying, exiting, complete }

    private let brandBlue = AppColors.primary

    var body: some View {
        ZStack {
            // ── 主内容区 ──
            if isReady {
                if authService.isAuthenticated {
                    MainView()
                        .transition(.opacity.animation(.easeInOut(duration: 0.5)))
                        .onAppear {
                            transitionToMainWindow()
                        }
                } else {
                    LoginView()
                        .transition(.opacity.animation(.easeInOut(duration: 0.5)))
                        .onAppear {
                            // 登录窗口已在 App 入口配置好，无需额外操作
                        }
                }
            }

            // ── 品牌启动页 ──
            if splashPhase != .complete {
                splashScreenView
                    .opacity(exitOpacity)
                    .zIndex(100)
                    .onAppear {
                        generateParticles()
                        startSplashAnimation()
                    }
            }
        }
        .animation(.easeInOut(duration: 0.4), value: authService.isAuthenticated)
        .onChange(of: authService.isAuthenticated) { authenticated in
            if authenticated && isReady {
                transitionToMainWindow()
                // 登录成功后自动挂载虚拟磁盘（在 Finder 侧边栏与 iCloud 同级显示）
                Task {
                    do {
                        // 更新配置：使用登录用户的真实信息
                        if let user = authService.currentUser {
                            virtualDiskManager.config.userId = user.id
                            virtualDiskManager.config.displayName = "PrivateCloudDisk"
                        }
                        if let token = KeychainManager.shared.readAuthToken() {
                            virtualDiskManager.config.token = token
                        }
                        virtualDiskManager.config.apiBaseUrl = UserDefaults.standard.string(forKey: "api_base_url") ?? "http://localhost:8000"
                        try await virtualDiskManager.mount()
                    } catch {
                        print("虚拟磁盘自动挂载失败: \(error.localizedDescription)")
                    }
                }
            }
        }
    }

    // MARK: - 窗口模式切换

    private func transitionToMainWindow() {
        DispatchQueue.main.async {
            guard let window = NSApp.keyWindow ?? NSApp.mainWindow else { return }
            window.transitionToMainWindow()
        }
    }

    // MARK: - 品牌启动页

    private var splashScreenView: some View {
        GeometryReader { geo in
            ZStack {
                AppGradients.splashBackground
                    .ignoresSafeArea()

                // 视差光晕
                ZStack {
                    Circle()
                        .fill(RadialGradient(
                            colors: [brandBlue.opacity(0.12), .clear],
                            center: .center, startRadius: 0, endRadius: 300))
                        .frame(width: 500, height: 500)
                        .position(x: geo.size.width * 0.15 + bgOffset.width * 0.5,
                                  y: geo.size.height * 0.2 + bgOffset.height * 0.3)
                        .blur(radius: 40)

                    Circle()
                        .fill(RadialGradient(
                            colors: [Color.cyan.opacity(0.06), .clear],
                            center: .center, startRadius: 0, endRadius: 200))
                        .frame(width: 300, height: 300)
                        .position(x: geo.size.width * 0.5 + bgOffset.width * 0.7,
                                  y: geo.size.height * 0.5 + bgOffset.height * 0.5)
                        .blur(radius: 30)
                }
                .ignoresSafeArea()
                .onAppear {
                    withAnimation(.easeInOut(duration: 8).repeatForever(autoreverses: true)) {
                        bgOffset = CGSize(width: 30, height: -20)
                    }
                }

                GridPatternView()
                    .opacity(0.06)

                // 粒子
                Canvas { context, size in
                    for particle in particles {
                        let pos = CGPoint(x: particle.x * size.width, y: particle.y * size.height)
                        let circlePath = Path(ellipseIn: CGRect(
                            x: pos.x - particle.size / 2, y: pos.y - particle.size / 2,
                            width: particle.size, height: particle.size))
                        context.fill(circlePath, with: .color(.white.opacity(particle.opacity * 0.3)))
                    }
                }
                .ignoresSafeArea()
                .allowsHitTesting(false)

                // 主内容
                VStack(spacing: 0) {
                    Spacer()

                    VStack(spacing: 28) {
                        // 图标
                        ZStack {
                            Circle()
                                .fill(RadialGradient(
                                    colors: [brandBlue.opacity(0.3), .clear],
                                    center: .center, startRadius: 30, endRadius: 70))
                                .frame(width: 120, height: 120)

                            RoundedRectangle(cornerRadius: 28)
                                .fill(AppGradients.primaryExtended)
                                .frame(width: 88, height: 88)
                                .shadow(color: brandBlue.opacity(0.5), radius: 40, x: 0, y: 10)

                            Image(systemName: "externaldrive.fill.badge.icloud")
                                .font(.system(size: 40, weight: .medium))
                                .foregroundColor(.white)
                        }
                        .scaleEffect(logoScale)
                        .opacity(logoOpacity)

                        // 品牌名
                        HStack(spacing: 0) {
                            Text("Private").font(.system(size: 36, weight: .bold, design: .default))
                                .foregroundColor(.white)
                            Text("Cloud").font(.system(size: 36, weight: .bold, design: .default))
                                .foregroundColor(brandBlue)
                            Text("Disk").font(.system(size: 36, weight: .bold, design: .default))
                                .foregroundColor(.white)
                        }
                        .opacity(logoOpacity)

                        Text("安全 · 高效 · 随时随地")
                            .font(.system(size: 15, weight: .medium, design: .default))
                            .foregroundColor(.white.opacity(0.6))
                            .tracking(4)
                            .opacity(taglineOpacity)
                            .padding(.top, 4)
                    }

                    Spacer()

                    // 底部：进度条 + 版本
                    VStack(spacing: 16) {
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                RoundedRectangle(cornerRadius: 2)
                                    .fill(.white.opacity(0.1))
                                    .frame(height: 3)
                                RoundedRectangle(cornerRadius: 2)
                                    .fill(LinearGradient(
                                        colors: [AppColors.primary, AppColors.primaryLight],
                                        startPoint: .leading, endPoint: .trailing))
                                    .frame(width: geo.size.width * initProgress, height: 3)
                                    .animation(.easeInOut(duration: 0.6), value: initProgress)
                            }
                        }
                        .frame(width: 200, height: 3)

                        Text("正在初始化...")
                            .font(.system(size: 13, design: .default))
                            .foregroundColor(.white.opacity(0.5))
                    }
                    .opacity(progressOpacity)

                    Text("v1.0.0")
                        .font(.system(size: 11, design: .default))
                        .foregroundColor(.white.opacity(0.25))
                        .padding(.top, 8)
                        .padding(.bottom, 40)
                        .opacity(progressOpacity)
                }
            }
        }
    }

    // MARK: - 粒子

    private func generateParticles() {
        particles = (0..<60).map { _ in
            SplashParticle(
                x: CGFloat.random(in: 0...1), y: CGFloat.random(in: 0...1),
                size: CGFloat.random(in: 1...3), opacity: CGFloat.random(in: 0.1...0.5),
                speedX: CGFloat.random(in: (-0.002)...0.002),
                speedY: CGFloat.random(in: (-0.003)...(-0.001)))
        }
        animateParticles()
    }

    private func animateParticles() {
        Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { timer in
            if splashPhase == .complete { timer.invalidate(); return }
            for i in particles.indices {
                particles[i].x += particles[i].speedX
                particles[i].y += particles[i].speedY
                if particles[i].y < -0.05 { particles[i].y = 1.05 }
                if particles[i].x > 1.05 { particles[i].x = -0.05 }
                if particles[i].x < -0.05 { particles[i].x = 1.05 }
            }
        }
    }

    // MARK: - 启动动画

    private func startSplashAnimation() {
        Task {
            withAnimation(.spring(response: 0.8, dampingFraction: 0.65)) {
                logoScale = 1.0; logoOpacity = 1.0
            }
            try? await Task.sleep(nanoseconds: 700_000_000)
            withAnimation(.easeOut(duration: 0.7)) { taglineOpacity = 1.0 }
            try? await Task.sleep(nanoseconds: 500_000_000)
            withAnimation(.easeOut(duration: 0.5)) { progressOpacity = 1.0 }
            try? await Task.sleep(nanoseconds: 300_000_000)

            withAnimation(.easeInOut(duration: 1.5)) { initProgress = 0.7 }
            await initializeServices()
            withAnimation(.easeInOut(duration: 0.3)) { initProgress = 1.0 }
            try? await Task.sleep(nanoseconds: 400_000_000)

            withAnimation(.easeInOut(duration: 0.7)) { exitOpacity = 0 }
            try? await Task.sleep(nanoseconds: 700_000_000)

            splashPhase = .complete
            await MainActor.run {
                withAnimation(.easeOut(duration: 0.4)) { isReady = true }
                DispatchQueue.main.async {
                    NSApp.keyWindow?.showTrafficLightButtons()
                    NSApp.keyWindow?.configureTrafficLightButtons()
                }
            }
        }
    }

    private func initializeServices() async {
        if let token = KeychainManager.shared.readAuthToken(), !token.isEmpty {
            do {
                let user = try await authService.validateToken(token)
                await MainActor.run {
                    authService.currentUser = user
                    authService.isAuthenticated = true
                }
            } catch {
                KeychainManager.shared.clearAll()
            }
        }
        if UserDefaults.standard.bool(forKey: "VirtualDisk.IsMounted") {
            try? await virtualDiskManager.restoreMount()
        }
    }
}

// MARK: - 粒子模型

struct SplashParticle {
    var x: CGFloat; var y: CGFloat; let size: CGFloat
    let opacity: CGFloat; let speedX: CGFloat; let speedY: CGFloat
}

// MARK: - 网格装饰

struct GridPatternView: View {
    var body: some View {
        Canvas { context, size in
            let spacing: CGFloat = 40
            var x: CGFloat = spacing
            while x < size.width {
                context.fill(Path(CGRect(x: x, y: 0, width: 0.5, height: size.height)),
                             with: .color(.white.opacity(0.03)))
                x += spacing
            }
            var y: CGFloat = spacing
            while y < size.height {
                context.fill(Path(CGRect(x: 0, y: y, width: size.width, height: 0.5)),
                             with: .color(.white.opacity(0.03)))
                y += spacing
            }
        }
    }
}

// MARK: - NSVisualEffectView 桥接

struct VisualEffectView: NSViewRepresentable {
    let material: NSVisualEffectView.Material
    let blendingMode: NSVisualEffectView.BlendingMode

    func makeNSView(context: Context) -> NSVisualEffectView {
        let view = NSVisualEffectView()
        view.material = material
        view.blendingMode = blendingMode
        view.state = .active
        view.isEmphasized = true
        return view
    }

    func updateNSView(_ nsView: NSVisualEffectView, context: Context) {
        nsView.material = material
        nsView.blendingMode = blendingMode
    }
}