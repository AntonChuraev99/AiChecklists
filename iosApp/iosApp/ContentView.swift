import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    @StateObject private var volumeObserver = VolumeButtonObserver()

    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            .onAppear {
                volumeObserver.onDebugMenuTriggered = {
                    MainViewControllerKt.navigateToDebugMenu()
                }
            }
    }
}



