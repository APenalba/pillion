import SwiftUI
import ReplayKit

/// Hosts the system broadcast picker. Kept *on-screen* (not off-screen): on recent iOS builds an
/// off-screen `RPSystemBroadcastPickerView` often has no tappable control, so Start mirroring
/// silently does nothing. The Compose button triggers it programmatically; the user can also tap
/// this icon directly as a fallback.
struct BroadcastPickerHost: UIViewRepresentable {
    let bridge: BroadcastBridge

    func makeUIView(context: Context) -> RPSystemBroadcastPickerView {
        let view = RPSystemBroadcastPickerView(frame: CGRect(x: 0, y: 0, width: 44, height: 44))
        // Sideloaders rewrite the bundle id; the appex id is always "<app id>.broadcast".
        let appId = Bundle.main.bundleIdentifier ?? "app.pillion.dev"
        view.preferredExtension = "\(appId).broadcast"
        view.showsMicrophoneButton = false
        bridge.register(view)
        return view
    }

    func updateUIView(_ uiView: RPSystemBroadcastPickerView, context: Context) {}
}
