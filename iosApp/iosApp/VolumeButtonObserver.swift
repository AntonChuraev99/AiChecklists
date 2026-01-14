import AVFoundation
import MediaPlayer

class VolumeButtonObserver: ObservableObject {
    private var audioSession: AVAudioSession?
    private var volumeView: MPVolumeView?
    private var observation: NSKeyValueObservation?

    private var lastVolumeChangeTime: Date = Date.distantPast
    private var lastVolumeDirection: VolumeDirection = .none
    private var sequenceCount: Int = 0

    private let sequenceTimeoutSeconds: TimeInterval = 0.5
    private let requiredSequenceCount: Int = 3

    var onDebugMenuTriggered: (() -> Void)?

    private enum VolumeDirection {
        case none
        case up
        case down
    }

    init() {
        setupAudioSession()
        setupVolumeObservation()
        hideSystemVolumeHUD()
    }

    deinit {
        observation?.invalidate()
    }

    private func setupAudioSession() {
        audioSession = AVAudioSession.sharedInstance()
        do {
            try audioSession?.setActive(true)
        } catch {
            print("Failed to activate audio session: \(error)")
        }
    }

    private func setupVolumeObservation() {
        guard let audioSession = audioSession else { return }

        observation = audioSession.observe(\.outputVolume, options: [.old, .new]) { [weak self] _, change in
            guard let self = self,
                  let oldValue = change.oldValue,
                  let newValue = change.newValue,
                  oldValue != newValue else { return }

            let direction: VolumeDirection = newValue > oldValue ? .up : .down
            self.handleVolumeChange(direction: direction)
        }
    }

    private func hideSystemVolumeHUD() {
        // Create an invisible MPVolumeView to hide the system volume HUD
        volumeView = MPVolumeView(frame: CGRect(x: -1000, y: -1000, width: 1, height: 1))
        volumeView?.isHidden = true
    }

    private func handleVolumeChange(direction: VolumeDirection) {
        let now = Date()
        let timeSinceLastChange = now.timeIntervalSince(lastVolumeChangeTime)

        if timeSinceLastChange > sequenceTimeoutSeconds {
            // Timeout - start new sequence
            resetSequence()
            sequenceCount = 1
            lastVolumeDirection = direction
            lastVolumeChangeTime = now
            return
        }

        // Check if direction alternates (up-down or down-up)
        if direction != lastVolumeDirection && lastVolumeDirection != .none {
            sequenceCount += 1
            lastVolumeDirection = direction
            lastVolumeChangeTime = now

            if sequenceCount >= requiredSequenceCount {
                resetSequence()
                DispatchQueue.main.async {
                    self.onDebugMenuTriggered?()
                }
            }
        } else {
            // Same direction twice - reset
            resetSequence()
            sequenceCount = 1
            lastVolumeDirection = direction
            lastVolumeChangeTime = now
        }
    }

    private func resetSequence() {
        sequenceCount = 0
        lastVolumeDirection = .none
        lastVolumeChangeTime = Date.distantPast
    }
}
