//
//  QRCodeScanView.swift
//  PrivateCloudDisk-ios
//
//  二维码扫码页面 — 企业级简约风格
//  使用 AVFoundation 原生扫码，支持手电筒、相册识别
//  设计风格与全局 DesignSystem 保持一致
//  完整适配横竖屏，支持预览模拟扫码验证交互
//  智能路由：设备授权 → 确认页 / 好友 → 资料页 / 分享 → 详情页 / URL → 弹窗 / 文本 → 弹窗
//

import SwiftUI
import AVFoundation
import PhotosUI
import Combine

// MARK: - 扫码结果回调（外部使用）

enum QRScanResult {
    case success(String)
    case cancelled
    case error(String)
}

// MARK: - 扫码视图

struct QRCodeScanView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var scanner = QRScannerViewModel()
    @State private var showPhotoPicker = false
    @State private var selectedPhoto: PhotosPickerItem?

    /// 智能路由后的结果类型
    @State private var scannedResultType: QRScanResultType?
    @State private var navigationPath = NavigationPath()

    // ── 弹窗模式（仅 URL / 文本类型使用） ──
    @State private var showAlert = false
    @State private var alertMessage = ""
    @State private var alertIsError = false

    /// 预览模拟模式：非 nil 时跳过真实相机，用模拟数据触发扫码结果
    var debugSimulatedCode: String? = nil

    var onResult: ((QRScanResult) -> Void)?

    var body: some View {
        NavigationStack(path: $navigationPath) {
            ZStack {
                // 全屏相机预览
                scannerView
                    .ignoresSafeArea()

                // 半透明遮罩 + 扫描框
                scanOverlay

                // 底部操作区
                VStack {
                    Spacer()
                    bottomBar
                }

                // 顶部状态文字
                VStack {
                    statusBar
                    Spacer()
                }

                // 预览模式：模拟暗色占位背景
                if debugSimulatedCode != nil {
                    previewDimBackground
                }
            }
            .navigationTitle("扫一扫")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(.ultraThinMaterial, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(action: {
                        scanner.stop()
                        onResult?(.cancelled)
                        dismiss()
                    }) {
                        Image(systemName: "xmark")
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(AppColors.textPrimary)
                            .frame(width: 36, height: 36)
                            .background(.ultraThinMaterial)
                            .clipShape(Circle())
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: { showPhotoPicker = true }) {
                        Image(systemName: "photo.on.rectangle")
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(AppColors.textPrimary)
                            .frame(width: 36, height: 36)
                            .background(.ultraThinMaterial)
                            .clipShape(Circle())
                    }
                }
            }
            .navigationDestination(for: QRScanResultType.self) { resultType in
                destinationView(for: resultType)
            }
            .onAppear {
                if let code = debugSimulatedCode {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                        scanner.isScanning = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                            handleScannedCode(code)
                        }
                    }
                } else {
                    scanner.start()
                }
            }
            .onDisappear { scanner.stop() }
            .onChange(of: scanner.scannedCode) { _, code in
                guard let code = code else { return }
                scanner.stop()
                handleScannedCode(code)
            }
            .onChange(of: scanner.errorMessage) { _, msg in
                guard let msg = msg else { return }
                alertMessage = msg
                alertIsError = true
                showAlert = true
            }
            .alert(alertIsError ? "扫描失败" : "扫描结果", isPresented: $showAlert) {
                if alertIsError {
                    Button("重试") {
                        if debugSimulatedCode != nil {
                            handleScannedCode(debugSimulatedCode!)
                        } else {
                            scanner.start()
                        }
                    }
                    Button("取消", role: .cancel) {
                        onResult?(.error(alertMessage))
                        dismiss()
                    }
                } else {
                    Button("复制") {
                        UIPasteboard.general.string = alertMessage
                        onResult?(.success(alertMessage))
                        dismiss()
                    }
                    Button("打开链接") {
                        if let url = URL(string: alertMessage), UIApplication.shared.canOpenURL(url) {
                            UIApplication.shared.open(url)
                        }
                        onResult?(.success(alertMessage))
                        dismiss()
                    }
                    Button("取消", role: .cancel) {
                        if debugSimulatedCode != nil {
                            handleScannedCode(debugSimulatedCode!)
                        } else {
                            scanner.start()
                        }
                    }
                }
            } message: {
                Text(alertMessage)
            }
            .photosPicker(isPresented: $showPhotoPicker, selection: $selectedPhoto, matching: .images)
            .onChange(of: selectedPhoto) { _, item in
                guard let item = item else { return }
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self),
                       let image = UIImage(data: data),
                       let ciImage = CIImage(image: image) {
                        let result = scanner.detectQRInImage(ciImage)
                        await MainActor.run {
                            if let code = result {
                                scanner.stop()
                                handleScannedCode(code)
                            } else {
                                alertMessage = "未识别到二维码"
                                alertIsError = true
                                showAlert = true
                            }
                        }
                    }
                    selectedPhoto = nil
                }
            }
        }
    }

    // MARK: - 智能路由处理

    private func handleScannedCode(_ code: String) {
        let resultType = QRScanResultRouter.parse(code)

        switch resultType {
        case .deviceAuth:
            // 设备授权 → 直接导航到确认页，不弹窗
            scannedResultType = resultType
            navigationPath.append(resultType)

        case .shareLink:
            // 分享链接 → 直接导航到详情页
            scannedResultType = resultType
            navigationPath.append(resultType)

        case .friendProfile:
            // 好友二维码 → 直接导航到资料页
            scannedResultType = resultType
            navigationPath.append(resultType)

        case .url(let urlString):
            // 普通 URL → 弹窗
            alertMessage = urlString
            alertIsError = false
            showAlert = true

        case .text(let text):
            // 纯文本 → 弹窗
            alertMessage = text
            alertIsError = false
            showAlert = true
        }
    }

    // MARK: - 导航目标视图

    @ViewBuilder
    private func destinationView(for resultType: QRScanResultType) -> some View {
        switch resultType {
        case .deviceAuth(let userCode, let deviceToken, let url):
            DeviceAuthConfirmationView(
                userCode: userCode,
                deviceToken: deviceToken,
                authURL: url,
                onAuthorized: {
                    onResult?(.success(url))
                    dismiss()
                }
            )

        case .shareLink(let shareId, let token, let url):
            ShareLinkDetailView(
                shareId: shareId,
                token: token,
                shareURL: url,
                onOpen: {
                    onResult?(.success(url))
                }
            )

        case .friendProfile(let userId, let nickname, let url):
            FriendProfileView(
                userId: userId,
                nickname: nickname,
                profileURL: url,
                onAddFriend: {
                    onResult?(.success(url))
                }
            )

        case .url, .text:
            // 不应该走到这里，URL 和 text 类型走弹窗
            EmptyView()
        }
    }

    // MARK: - 预览模式暗色背景

    private var previewDimBackground: some View {
        ZStack {
            Color.black.opacity(0.85).ignoresSafeArea()
            VStack(spacing: AppSpacing.lg) {
                Image(systemName: "qrcode")
                    .font(.system(size: 48))
                    .foregroundColor(AppColors.primary.opacity(0.6))
                Text("预览模式 - 模拟扫码")
                    .font(AppTypography.headline)
                    .foregroundColor(.white.opacity(0.7))
                Text("即将模拟识别二维码...")
                    .font(AppTypography.subheadline)
                    .foregroundColor(.white.opacity(0.5))
            }
        }
    }

    // MARK: - 相机预览

    private var scannerView: some View {
        Group {
            if debugSimulatedCode != nil {
                Color.black
            } else if scanner.isAuthorized {
                QRScannerPreview(session: scanner.session)
                    .background(Color.black)
            } else if scanner.permissionChecked {
                VStack(spacing: AppSpacing.xl) {
                    Spacer()
                    Image(systemName: "camera.fill")
                        .font(.system(size: 48))
                        .foregroundColor(AppColors.textTertiary)
                    Text("需要相机权限")
                        .font(AppTypography.title3)
                        .foregroundColor(AppColors.textPrimary)
                    Text("请在「设置 > 隐私 > 相机」中\n允许 CloudDrive 访问相机")
                        .font(AppTypography.subheadline)
                        .foregroundColor(AppColors.textSecondary)
                        .multilineTextAlignment(.center)
                    AppPrimaryButton("前往设置") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
                    .padding(.horizontal, 60)
                    Spacer()
                }
                .background(AppColors.background)
            } else {
                Color.black
            }
        }
    }

    // MARK: - 扫描框遮罩（横竖屏自适应）

    private var scanOverlay: some View {
        GeometryReader { geo in
            let minDim = min(geo.size.width, geo.size.height)
            let scanSize = min(minDim * 0.68, 260)
            let scanCenterX = geo.size.width / 2
            let scanCenterY = geo.size.height / 2 - 30
            let scanRect = CGRect(
                x: scanCenterX - scanSize / 2,
                y: scanCenterY - scanSize / 2,
                width: scanSize,
                height: scanSize
            )

            ZStack {
                scanMask(size: geo.size, scanRect: scanRect)

                RoundedRectangle(cornerRadius: AppRadius.lg)
                    .stroke(Color.white.opacity(0.85), lineWidth: 2)
                    .frame(width: scanSize, height: scanSize)
                    .position(x: scanRect.midX, y: scanRect.midY)

                cornerHighlights(scanRect: scanRect)

                if scanner.isScanning || debugSimulatedCode != nil {
                    scanLine(scanRect: scanRect)
                }

                Text(debugSimulatedCode != nil ? "预览模式 — 模拟扫码中..." : "将二维码放入框内，即可自动扫描")
                    .font(AppTypography.footnote)
                    .foregroundColor(.white.opacity(0.8))
                    .position(x: geo.size.width / 2, y: scanRect.maxY + 26)
            }
            .compositingGroup()
        }
    }

    private func scanMask(size: CGSize, scanRect: CGRect) -> some View {
        Color.black.opacity(0.45)
            .mask(
                Rectangle()
                    .fill(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: AppRadius.lg)
                            .frame(width: scanRect.width, height: scanRect.height)
                            .position(x: scanRect.midX, y: scanRect.midY)
                            .blendMode(.destinationOut)
                    )
            )
    }

    // MARK: - 四角高亮

    private func cornerHighlights(scanRect: CGRect) -> some View {
        let cornerLen: CGFloat = 24
        let lw: CGFloat = 3.5

        return ZStack {
            HCornerMark(x: scanRect.minX, y: scanRect.minY, length: cornerLen, lineWidth: lw)
            VCornerMark(x: scanRect.minX, y: scanRect.minY, length: cornerLen, lineWidth: lw)
            HCornerMark(x: scanRect.maxX, y: scanRect.minY, length: -cornerLen, lineWidth: lw)
            VCornerMark(x: scanRect.maxX, y: scanRect.minY, length: cornerLen, lineWidth: lw)
            HCornerMark(x: scanRect.minX, y: scanRect.maxY, length: cornerLen, lineWidth: lw)
            VCornerMark(x: scanRect.minX, y: scanRect.maxY, length: -cornerLen, lineWidth: lw)
            HCornerMark(x: scanRect.maxX, y: scanRect.maxY, length: -cornerLen, lineWidth: lw)
            VCornerMark(x: scanRect.maxX, y: scanRect.maxY, length: -cornerLen, lineWidth: lw)
        }
    }

    private func HCornerMark(x: CGFloat, y: CGFloat, length: CGFloat, lineWidth: CGFloat) -> some View {
        Rectangle()
            .fill(AppColors.primary)
            .frame(width: abs(length), height: lineWidth)
            .position(x: x + length / 2, y: y)
    }

    private func VCornerMark(x: CGFloat, y: CGFloat, length: CGFloat, lineWidth: CGFloat) -> some View {
        Rectangle()
            .fill(AppColors.primary)
            .frame(width: lineWidth, height: abs(length))
            .position(x: x, y: y + length / 2)
    }

    // MARK: - 扫描线动画

    private func scanLine(scanRect: CGRect) -> some View {
        Rectangle()
            .fill(
                LinearGradient(
                    colors: [
                        AppColors.primary.opacity(0),
                        AppColors.primary.opacity(0.55),
                        AppColors.primary.opacity(0),
                    ],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .frame(width: scanRect.width - 8, height: 2)
            .position(x: scanRect.midX, y: scanRect.minY + 8)
            .modifier(ScanLineAnimation(scanHeight: scanRect.height - 16))
    }

    // MARK: - 顶部状态栏（横竖屏自适应）

    private var statusBar: some View {
        GeometryReader { geo in
            HStack {
                if let error = scanner.errorMessage {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .font(AppTypography.footnote)
                        .foregroundColor(AppColors.danger)
                        .padding(.horizontal, AppSpacing.lg)
                        .padding(.vertical, AppSpacing.sm)
                        .background(.ultraThinMaterial)
                        .clipShape(Capsule())
                } else if scanner.isScanning || debugSimulatedCode != nil {
                    Label(
                        debugSimulatedCode != nil ? "模拟扫描中..." : "正在扫描...",
                        systemImage: "qrcode.viewfinder"
                    )
                    .font(AppTypography.footnote)
                    .foregroundColor(.white.opacity(0.9))
                    .padding(.horizontal, AppSpacing.lg)
                    .padding(.vertical, AppSpacing.sm)
                    .background(.ultraThinMaterial)
                    .clipShape(Capsule())
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, geo.safeAreaInsets.top + 52)
        }
    }

    // MARK: - 底部操作栏（横竖屏自适应）

    private var bottomBar: some View {
        GeometryReader { geo in
            VStack {
                Spacer()
                HStack(spacing: 48) {
                    Button(action: { showPhotoPicker = true }) {
                        VStack(spacing: 6) {
                            Image(systemName: "photo.on.rectangle")
                                .font(.system(size: 22))
                            Text("相册")
                                .font(AppTypography.caption2)
                        }
                        .foregroundColor(.white)
                    }

                    Button(action: { scanner.toggleTorch() }) {
                        VStack(spacing: 6) {
                            Image(systemName: scanner.isTorchOn ? "flashlight.on.fill" : "flashlight.off.fill")
                                .font(.system(size: 22))
                            Text(scanner.isTorchOn ? "关灯" : "开灯")
                                .font(AppTypography.caption2)
                        }
                        .foregroundColor(scanner.isTorchOn ? AppColors.warning : .white)
                    }
                }
                .padding(.vertical, AppSpacing.lg)
                .padding(.horizontal, AppSpacing.xxl)
                .background(.ultraThinMaterial)
                .clipShape(Capsule())
                .padding(.bottom, max(geo.safeAreaInsets.bottom + 20, 30))
            }
        }
    }
}

// MARK: - 扫描线动画

struct ScanLineAnimation: ViewModifier {
    let scanHeight: CGFloat
    @State private var offset: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .offset(y: offset)
            .onAppear {
                withAnimation(
                    .linear(duration: 2.5)
                    .repeatForever(autoreverses: true)
                ) {
                    offset = scanHeight
                }
            }
    }
}

// MARK: - 相机预览 UIViewRepresentable（横竖屏自适应）

struct QRScannerPreview: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .black

        let previewLayer = AVCaptureVideoPreviewLayer(session: session)
        previewLayer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(previewLayer)

        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        guard let layer = uiView.layer.sublayers?.first as? AVCaptureVideoPreviewLayer else { return }
        layer.frame = uiView.bounds
    }
}

// MARK: - 扫码 ViewModel

@MainActor
class QRScannerViewModel: ObservableObject {
    @Published var scannedCode: String?
    @Published var errorMessage: String?
    @Published var isTorchOn = false
    @Published var isScanning = false
    @Published var isAuthorized = false
    @Published var permissionChecked = false

    let session = AVCaptureSession()
    private var output = AVCaptureMetadataOutput()
    private var device: AVCaptureDevice?

    func start() {
        scannedCode = nil
        errorMessage = nil
        checkPermission()
    }

    func stop() {
        isScanning = false
        if session.isRunning {
            session.stopRunning()
        }
    }

    private func checkPermission() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            isAuthorized = true
            permissionChecked = true
            setupSession()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                Task { @MainActor in
                    self?.isAuthorized = granted
                    self?.permissionChecked = true
                    if granted { self?.setupSession() }
                }
            }
        default:
            isAuthorized = false
            permissionChecked = true
        }
    }

    private func setupSession() {
        guard let device = AVCaptureDevice.default(for: .video) else {
            errorMessage = "无法访问摄像头"
            return
        }
        self.device = device

        session.beginConfiguration()
        session.sessionPreset = .high

        do {
            let input = try AVCaptureDeviceInput(device: device)
            if session.canAddInput(input) { session.addInput(input) }
        } catch {
            errorMessage = "摄像头初始化失败"
            session.commitConfiguration()
            return
        }

        if session.canAddOutput(output) {
            session.addOutput(output)
            let delegate = ScannerDelegate { [weak self] code in
                Task { @MainActor [weak self] in
                    self?.scannedCode = code
                }
            }
            output.setMetadataObjectsDelegate(delegate, queue: DispatchQueue.main)
            output.metadataObjectTypes = [
                .qr, .ean8, .ean13, .code128,
                .code39, .code93, .aztec, .pdf417
            ]
        }

        session.commitConfiguration()

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.session.startRunning()
            Task { @MainActor in
                self?.isScanning = true
            }
        }
    }

    func toggleTorch() {
        guard let device = device, device.hasTorch, device.isTorchAvailable else { return }
        try? device.lockForConfiguration()
        if device.torchMode == .on {
            device.torchMode = .off
            isTorchOn = false
        } else {
            try? device.setTorchModeOn(level: 1.0)
            isTorchOn = true
        }
        device.unlockForConfiguration()
    }

    func detectQRInImage(_ ciImage: CIImage) -> String? {
        let detector = CIDetector(
            ofType: CIDetectorTypeQRCode,
            context: nil,
            options: [CIDetectorAccuracy: CIDetectorAccuracyHigh]
        )
        guard let features = detector?.features(in: ciImage) as? [CIQRCodeFeature] else { return nil }
        return features.first?.messageString
    }
}

// MARK: - 扫码代理

private class ScannerDelegate: NSObject, AVCaptureMetadataOutputObjectsDelegate, @unchecked Sendable {
    let handler: (String) -> Void

    init(handler: @escaping (String) -> Void) {
        self.handler = handler
    }

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard let obj = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let code = obj.stringValue else { return }
        handler(code)
    }
}

// MARK: - 预览（含模拟扫码 → 智能路由）

#Preview("扫码页面 - 正常状态") {
    QRCodeScanView()
}

#Preview("扫码页面 - 暗色模式") {
    QRCodeScanView()
        .preferredColorScheme(.dark)
}

#Preview("模拟设备授权 → 跳转确认页") {
    QRCodeScanView(
        debugSimulatedCode: "https://clouddrive.example.com/device/authorize?user_code=KD8X-2P9A&device_token=eyJhbGciOiJSUzI1NiJ9.xxx"
    )
}

#Preview("模拟好友二维码 → 跳转资料页") {
    QRCodeScanView(
        debugSimulatedCode: "https://clouddrive.example.com/user/u_abc123?nickname=张晓明"
    )
}

#Preview("模拟分享链接 → 跳转详情页") {
    QRCodeScanView(
        debugSimulatedCode: "https://clouddrive.example.com/share/s/7f3a8b2c1d?token=abc123def456&type=file"
    )
}

#Preview("模拟普通 URL → 弹窗") {
    QRCodeScanView(
        debugSimulatedCode: "https://www.example.com/article/12345"
    )
}

#Preview("模拟纯文本 → 弹窗") {
    QRCodeScanView(
        debugSimulatedCode: "这是一段纯文本内容，不是 URL"
    )
}

#Preview("横屏 - 设备授权", traits: .landscapeRight) {
    QRCodeScanView(
        debugSimulatedCode: "https://clouddrive.example.com/device/authorize?user_code=KD8X-2P9A"
    )
}