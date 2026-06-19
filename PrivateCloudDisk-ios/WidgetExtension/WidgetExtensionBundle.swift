//
//  WidgetExtensionBundle.swift
//  WidgetExtension
//
//  Created by 莫伟钊 on 2026/6/19.
//

import WidgetKit
import SwiftUI

@main
struct WidgetExtensionBundle: WidgetBundle {
    var body: some Widget {
        WidgetExtension()
        WidgetExtensionControl()
        WidgetExtensionLiveActivity()
    }
}
