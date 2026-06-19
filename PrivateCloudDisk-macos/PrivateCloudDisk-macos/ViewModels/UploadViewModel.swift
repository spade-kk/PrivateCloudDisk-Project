import Foundation
import Combine
import SwiftUI

// MARK: - 上传进度视图模型

@MainActor
final class UploadViewModel: ObservableObject {

    @Published var activeTasks: [UploadTask] = []
    @Published var overallProgress: Double = 0
    @Published var isUploading = false

    private let uploadManager = UploadManager.shared
    private var cancellables = Set<AnyCancellable>()

    init() {
        setupBindings()
    }

    private func setupBindings() {
        uploadManager.$activeTasks
            .receive(on: DispatchQueue.main)
            .assign(to: &$activeTasks)

        uploadManager.$overallProgress
            .receive(on: DispatchQueue.main)
            .assign(to: &$overallProgress)

        $activeTasks
            .map { !$0.isEmpty }
            .assign(to: &$isUploading)
    }

    // MARK: - 操作

    func uploadFiles(urls: [URL], parentId: String? = nil) {
        for url in urls {
            Task {
                try? await uploadManager.uploadFile(
                    localURL: url,
                    parentId: parentId
                )
            }
        }
    }

    func pauseUpload(_ uploadId: String) {
        uploadManager.pauseUpload(uploadId)
    }

    func cancelUpload(_ uploadId: String) {
        uploadManager.cancelUpload(uploadId)
    }
}