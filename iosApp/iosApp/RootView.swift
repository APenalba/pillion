import SwiftUI

/// Shared Compose UI + a visible ReplayKit broadcast picker fallback.
/// Start mirroring programmatically taps that picker; if that fails on this iOS build, the user can
/// tap the system icon directly.
struct RootView: View {
    @StateObject private var bridge = BroadcastBridge()

    var body: some View {
        ZStack(alignment: .bottom) {
            ComposeScreen { bridge.makeViewController() }
                .ignoresSafeArea()

            VStack(spacing: 4) {
                Text("Broadcast (tap if Start does nothing)")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                BroadcastPickerHost(bridge: bridge)
                    .frame(width: 44, height: 44)
            }
            // Sit above the Compose primary button (~56pt) + padding.
            .padding(.bottom, 88)
        }
    }
}
