import Foundation
import Combine
import SwiftUI

// MARK: - 虚拟磁盘视图模型

@MainActor
final class VirtualDiskViewModel: ObservableObject {

    @Published var status: VirtualDiskStatus = .disconnected
    @Published var config: VirtualDiskConfig = .default
    @Published var isMounted = false
    @Published var syncEvents: [SyncEvent] = []
    @Published var cacheSize: Int64 = 0
    @Published var quota: QuotaInfo?
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let manager = VirtualDiskManager.shared
    private var cancellables = Set<AnyCancellable>()

    init() {
        setupBindings()
    }

    private func setupBindings() {
        manager.$status
            .receive(on: DispatchQueue.main)
            .assign(to: &$status)

        manager.$isMounted
            .receive(on: DispatchQueue.main)
            .assign(to: &$isMounted)

        manager.$config
            .receive(on: DispatchQueue.main)
            .assign(to: &$config)

        manager.$syncEvents
            .receive(on: DispatchQueue.main)
            .assign(to: &$syncEvents)
    }

    // MARK: - 操作

    func mount() async {
        isLoading = true
        errorMessage = nil
        do {
            try await manager.mount()
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    func unmount() {
        manager.unmount()
    }

    func clearCache() async {
        isLoading = true
        await manager.clearCache()
        cacheSize = manager.getCacheSize()
        isLoading = false
    }

    func refreshCacheSize() {
        cacheSize = manager.getCacheSize()
    }

    func updateConfig(mountPoint: String? = nil, autoSync: Bool? = nil, syncInterval: TimeInterval? = nil) {
        var newConfig = config
        if let mp = mountPoint { newConfig.mountPoint = mp }
        if let auto = autoSync { newConfig.autoSync = auto }
        if let interval = syncInterval { newConfig.syncInterval = interval }
        manager.updateConfig(newConfig)
    }
}