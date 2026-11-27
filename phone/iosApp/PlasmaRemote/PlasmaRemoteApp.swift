// SPDX-License-Identifier: Apache-2.0
import Shared
import SwiftUI

/// Plasma Remote for iPhone (brief 5.22): the shared Compose app, edge to edge, always dark.
@main
struct PlasmaRemoteApp: App {
    var body: some Scene {
        WindowGroup {
            SharedApp()
                .ignoresSafeArea()
                .preferredColorScheme(.dark)
        }
    }
}

private struct SharedApp: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
