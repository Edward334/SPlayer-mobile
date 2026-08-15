import AVFoundation
import Capacitor
import MediaPlayer

@objc(SPlayerAudioPlugin)
public class SPlayerAudioPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "SPlayerAudioPlugin"
    public let jsName = "SPlayerAudio"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "initialize", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "load", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "play", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "pause", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stop", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "seek", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setVolume", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setRate", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getState", returnType: CAPPluginReturnPromise)
    ]

    private var player: AVPlayer?
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?

    @objc func initialize(_ call: CAPPluginCall) {
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
            try AVAudioSession.sharedInstance().setActive(true)
            configureRemoteCommands()
            call.resolve()
        } catch {
            call.reject("Unable to initialize audio session", "AUDIO_SESSION_ERROR", error)
        }
    }

    @objc func load(_ call: CAPPluginCall) {
        guard let urlString = call.getString("url"), let url = URL(string: urlString) else {
            call.reject("Audio URL is required")
            return
        }
        if let observer = endObserver {
            NotificationCenter.default.removeObserver(observer)
        }
        player = AVPlayer(url: url)
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: player?.currentItem,
            queue: .main
        ) { [weak self] _ in
            self?.updateNowPlayingState()
            self?.notifyListeners("ended", data: [:])
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: call.getString("title") ?? "SPlayer",
            MPMediaItemPropertyArtist: call.getString("artist") ?? "",
            MPNowPlayingInfoPropertyPlaybackRate: 0.0,
        ]
        installTimeObserver()
        call.resolve()
    }

    @objc func play(_ call: CAPPluginCall) {
        player?.play()
        updateNowPlayingState()
        notifyListeners("play", data: [:])
        call.resolve()
    }

    @objc func pause(_ call: CAPPluginCall) {
        player?.pause()
        updateNowPlayingState()
        notifyListeners("pause", data: [:])
        call.resolve()
    }

    @objc func stop(_ call: CAPPluginCall) {
        player?.pause()
        player?.seek(to: .zero)
        updateNowPlayingState()
        notifyListeners("pause", data: [:])
        call.resolve()
    }

    @objc func seek(_ call: CAPPluginCall) {
        let seconds = call.getDouble("time") ?? 0
        player?.seek(to: CMTime(seconds: seconds, preferredTimescale: 600))
        updateNowPlayingState()
        call.resolve()
    }

    @objc func setVolume(_ call: CAPPluginCall) {
        player?.volume = Float(call.getDouble("volume") ?? 1)
        call.resolve()
    }

    @objc func setRate(_ call: CAPPluginCall) {
        player?.rate = Float(call.getDouble("rate") ?? 1)
        updateNowPlayingState()
        call.resolve()
    }

    @objc func getState(_ call: CAPPluginCall) {
        let current = player?.currentTime().seconds ?? 0
        let duration = player?.currentItem?.duration.seconds ?? 0
        call.resolve([
            "src": "",
            "duration": duration.isFinite ? duration : 0,
            "currentTime": current.isFinite ? current : 0,
            "paused": player?.rate == 0,
            "volume": player?.volume ?? 1,
            "rate": player?.rate ?? 1
        ])
    }

    deinit {
        if let observer = timeObserver { player?.removeTimeObserver(observer) }
        if let observer = endObserver { NotificationCenter.default.removeObserver(observer) }
    }

    private func installTimeObserver() {
        guard let player else { return }
        if let observer = timeObserver { player.removeTimeObserver(observer) }
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.25, preferredTimescale: 600),
            queue: .main
        ) { [weak self] time in
            let duration = player.currentItem?.duration.seconds ?? 0
            self?.updateNowPlayingState(duration: duration, currentTime: time.seconds)
            self?.notifyListeners("timeUpdate", data: [
                "currentTime": time.seconds,
                "duration": duration.isFinite ? duration : 0
            ])
        }
    }

    private func configureRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()
        center.playCommand.addTarget { [weak self] _ in
            self?.player?.play()
            self?.notifyListeners("play", data: [:])
            return .success
        }
        center.pauseCommand.addTarget { [weak self] _ in
            self?.player?.pause()
            self?.notifyListeners("pause", data: [:])
            return .success
        }
        center.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let positionEvent = event as? MPChangePlaybackPositionCommandEvent else {
                return .commandFailed
            }
            self?.player?.seek(to: CMTime(seconds: positionEvent.positionTime, preferredTimescale: 600))
            return .success
        }
    }

    private func updateNowPlayingState(duration: Double? = nil, currentTime: Double? = nil) {
        var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
        let resolvedDuration = duration ?? player?.currentItem?.duration.seconds ?? 0
        let resolvedTime = currentTime ?? player?.currentTime().seconds ?? 0
        if resolvedDuration.isFinite { info[MPMediaItemPropertyPlaybackDuration] = resolvedDuration }
        if resolvedTime.isFinite { info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = resolvedTime }
        info[MPNowPlayingInfoPropertyPlaybackRate] = player?.rate ?? 0
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }
}
