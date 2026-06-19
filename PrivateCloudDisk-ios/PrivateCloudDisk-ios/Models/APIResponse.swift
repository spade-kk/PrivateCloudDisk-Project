//
//  APIResponse.swift
//  PrivateCloudDisk-ios
//
//  统一 API 响应模型
//

import Foundation

/// 统一 API 响应结构
struct APIResponse<T: Codable>: Codable {
    let code: Int
    let message: String?
    let data: T?
}

/// 空数据响应（仅需要 code + message 的接口）
struct APIEmptyResponse: Codable {
    let code: Int
    let message: String?
}

/// 分页数据
struct PageData<T: Codable>: Codable {
    let items: [T]
    let total: Int
    let page: Int
    let size: Int
}

/// WebSocket 消息类型
struct WSMessage<T: Codable>: Codable {
    let type: String
    let data: T?
    let timestamp: Int64?
    let senderId: String?
}