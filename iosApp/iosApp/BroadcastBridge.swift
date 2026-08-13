import SwiftUI
import ReplayKit
import ExternalAccessory
import ComposeApp

/// Connects the shared Compose UI to iOS screen broadcasting:
/// - the Pillion "Start mirroring" button → triggers the system broadcast picker,
/// - the extension's broadcast start/stop/status Darwin notifications → the shared `MirrorState`,
/// - a live MFi accessory preflight so Connection status is visible *before* Start mirroring.
final class BroadcastBridge: ObservableObject {
    let controller: BroadcastMirrorController        // NaviLite (Bluetooth / MFi via ReplayKit)
    let sdlController: SdlBroadcastController         // SDL (USB / iAP2)
    private let sdlSession: SdlSession
    private weak var picker: RPSystemBroadcastPickerView?
    /// Poll while the extension is live — Darwin can miss a beat across process boundaries.
    private var statusPoll: Timer?
    /// Always-on accessory scan for the Idle Connection status panel.
    private var preflightPoll: Timer?

    init() {
        controller = BroadcastMirrorController()
        sdlController = SdlBroadcastController()
        // Build the SDL session first, then connect the Kotlin controller's state in (the closure has to
        // be assigned after sdlController exists), and route start/stop from the UI down to the session.
        let sdlController = self.sdlController
        sdlSession = SdlSession(onState: { state in
            DispatchQueue.main.async {
                switch state {
                case .idle: sdlController.setIdle()
                case .connecting: sdlController.setConnecting()
                case .streaming: sdlController.setStreaming()
                case .error(let message): sdlController.setError(message: message)
                }
            }
        })
        controller.onToggle = { [weak self] in self?.triggerPicker() }
        self.sdlController.onStart = { [weak self] in self?.sdlSession.start() }
        self.sdlController.onStop = { [weak self] in self?.sdlSession.stop() }
        observeBroadcastState()
        startPreflightPoll()
    }

    func makeViewController() -> UIViewController {
        MainViewControllerKt.MainViewController(naviliteController: controller, sdlController: sdlController)
    }

    /// Called by `BroadcastPickerHost` once the (hidden) picker view exists.
    func register(_ picker: RPSystemBroadcastPickerView) { self.picker = picker }

    private func triggerPicker() {
        // RPSystemBroadcastPickerView has no programmatic trigger, so tap its embedded button.
        // Its view tree differs across iOS versions, so search recursively.
        guard let picker = picker, let button = Self.firstButton(in: picker) else { return }
        button.sendActions(for: .touchUpInside)
    }

    private static func firstButton(in view: UIView) -> UIButton? {
        if let button = view as? UIButton { return button }
        for sub in view.subviews { if let b = firstButton(in: sub) { return b } }
        return nil
    }

    private func observeBroadcastState() {
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        let me = Unmanaged.passUnretained(self).toOpaque()
        let callback: CFNotificationCallback = { _, observer, name, _, _ in
            guard let observer = observer, let name = name else { return }
            let bridge = Unmanaged<BroadcastBridge>.fromOpaque(observer).takeUnretainedValue()
            let raw = name.rawValue as String
            DispatchQueue.main.async {
                switch raw {
                case "app.pillion.broadcast.started":
                    bridge.controller.setActive(active: true)
                    bridge.startStatusPoll()
                    bridge.pullStatus()
                case "app.pillion.broadcast.stopped":
                    bridge.stopStatusPoll()
                    bridge.controller.setActive(active: false)
                    bridge.publishPreflight()
                case BroadcastStatus.updated:
                    bridge.pullStatus()
                default: break
                }
            }
        }
        for name in [
            "app.pillion.broadcast.started",
            "app.pillion.broadcast.stopped",
            BroadcastStatus.updated,
        ] {
            CFNotificationCenterAddObserver(center, me, callback, name as CFString, nil, .deliverImmediately)
        }
    }

    private func pullStatus() {
        guard let snap = BroadcastStatus.read() else { return }
        controller.applyStatus(
            phase: snap.phase,
            transport: snap.transport,
            bikeFound: snap.bikeFound,
            accessories: snap.accessories,
            message: snap.message,
            fps: snap.fps,
            kbPerFrame: Int32(snap.kbPerFrame)
        )
    }

    private func startStatusPoll() {
        stopStatusPoll()
        statusPoll = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            self?.pullStatus()
        }
    }

    private func stopStatusPoll() {
        statusPoll?.invalidate()
        statusPoll = nil
    }

    private func startPreflightPoll() {
        publishPreflight()
        preflightPoll = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { [weak self] _ in
            self?.publishPreflight()
        }
    }

    /// Enumerate MFi accessories in the *app* process (no App Group). Shows on Idle immediately.
    private func publishPreflight() {
        let protocolId = BroadcastConfig.dashProtocol
        let accs = EAAccessoryManager.shared().connectedAccessories
        let bikeFound = accs.contains { $0.protocolStrings.contains(protocolId) }
        let lines: String
        if accs.isEmpty {
            lines = "MFi accessories: none\nCCU (\(protocolId)): NOT found\n\nPair the bike in Bluetooth settings, close StreetCross, put the dash in Navigation mode."
        } else {
            let list = accs.map { "• \($0.name): \($0.protocolStrings.joined(separator: ", "))" }
                .joined(separator: "\n")
            lines = "MFi accessories:\n\(list)\n\nCCU (\(protocolId)): \(bikeFound ? "found ✓" : "NOT found")"
        }
        controller.setPreflight(hint: lines)
    }
}
