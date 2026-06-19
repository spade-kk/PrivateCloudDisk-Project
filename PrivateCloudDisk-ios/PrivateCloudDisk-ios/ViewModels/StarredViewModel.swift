//
//  StarredViewModel.swift
//  PrivateCloudDisk-ios
//
//  收藏 ViewModel
//

import Foundation
import SwiftUI
import Combine

@MainActor
class StarredViewModel: ObservableObject {
    @Published var stars: [StarItem] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let starService = StarService.shared

    func loadStars() async {
        isLoading = true
        defer { isLoading = false }

        do {
            stars = try await starService.getStars()
        } catch {
            errorMessage = "加载收藏列表失败"
        }
    }

    func removeStar(starId: String) async {
        do {
            try await starService.removeStar(starId: starId)
            stars.removeAll { $0.starId == starId }
        } catch {
            errorMessage = "取消收藏失败"
        }
    }
}