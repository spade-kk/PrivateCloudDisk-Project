//
//  SharesViewModel.swift
//  PrivateCloudDisk-ios
//
//  分享管理 ViewModel
//

import Foundation
import SwiftUI
import Combine

@MainActor
class SharesViewModel: ObservableObject {
    @Published var shares: [ShareLinkItem] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    // 创建分享
    @Published var showCreateSheet = false
    @Published var createPassword = ""
    @Published var createExpireDays: Int = 7
    @Published var createMaxAccess: Int = 0
    @Published var selectedTarget: FileNode?

    // 分享访问
    @Published var shareContent: ShareContent?
    @Published var shareChildren: [FileNode] = []
    @Published var shareAccessToken: String?
    @Published var shareNodeStack: [(String, String)] = [] // (nodeId, name)

    private let shareService = ShareService.shared

    // MARK: - 我的分享

    func loadMyShares() async {
        isLoading = true
        defer { isLoading = false }

        do {
            shares = try await shareService.getMyShares()
        } catch {
            errorMessage = "加载分享列表失败"
        }
    }

    func createShare(target: FileNode) async {
        do {
            let request = CreateShareRequest(
                targetType: target.isFolder ? "FOLDER" : "FILE",
                fileId: target.isFolder ? nil : target.fileId,
                nodeId: target.isFolder ? target.nodeId : nil,
                password: createPassword.isEmpty ? nil : PasswordHasher.hashSharePassword(createPassword),
                expireDays: createExpireDays > 0 ? createExpireDays : nil,
                maxAccessCount: createMaxAccess > 0 ? createMaxAccess : nil
            )
            let _ = try await shareService.createShare(request: request)
            await loadMyShares()
            showCreateSheet = false
        } catch {
            errorMessage = "创建分享失败"
        }
    }

    func revokeShare(shareToken: String) async {
        do {
            try await shareService.revokeShare(shareToken: shareToken)
            await loadMyShares()
        } catch {
            errorMessage = "撤销分享失败"
        }
    }

    // MARK: - 访问分享

    func loadShareContent(shareToken: String) async {
        isLoading = true
        defer { isLoading = false }

        do {
            shareContent = try await shareService.getShareContent(shareToken: shareToken)
        } catch {
            errorMessage = "加载分享内容失败"
        }
    }

    func verifyPassword(shareToken: String, password: String) async {
        do {
            let result = try await shareService.verifySharePassword(shareToken: shareToken, password: password)
            shareAccessToken = result.accessToken
            await loadShareContent(shareToken: shareToken)
        } catch {
            errorMessage = "密码验证失败"
        }
    }

    func navigateShareFolder(nodeId: String, nodeName: String) async {
        guard let shareToken = shareContent?.shareToken,
              let accessToken = shareAccessToken else { return }

        shareNodeStack.append((nodeId, nodeName))
        do {
            shareChildren = try await shareService.getShareChildren(
                shareToken: shareToken, nodeId: nodeId, accessToken: accessToken
            )
        } catch {
            errorMessage = "加载文件夹内容失败"
        }
    }

    func navigateShareBack() {
        guard !shareNodeStack.isEmpty else { return }
        shareNodeStack.removeLast()
        if let (nodeId, _) = shareNodeStack.last {
            Task { await navigateShareFolder(nodeId: nodeId, nodeName: "") }
        } else {
            shareChildren = shareContent?.children ?? []
        }
    }
}