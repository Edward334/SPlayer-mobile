import { AudioElementPlayer } from "./AudioElementPlayer";
import { AUDIO_EVENTS, AudioErrorCode, type AudioEventType } from "./BaseAudioPlayer";
import type {
  EngineCapabilities,
  FadeCurve,
  IPlaybackEngine,
  PauseOptions,
  PlayOptions,
} from "./IPlaybackEngine";
import { getNativeAudioPlugin, type NativeAudioPlugin } from "@/platform/capacitor";
import { useMusicStore } from "@/stores";

/** 移动端原生音频适配器，原生插件不可用时回退到 Web 音频。 */
export class NativeAudioPlayer implements IPlaybackEngine {
  private readonly fallback = new AudioElementPlayer();
  private readonly listeners = new EventTarget();
  private readonly plugin: NativeAudioPlugin | null;
  private pluginListeners: { remove: () => Promise<void> }[] = [];
  private state = {
    src: "",
    duration: 0,
    currentTime: 0,
    paused: true,
    volume: 1,
    rate: 1,
  };
  private errorCode = 0;

  public readonly capabilities: EngineCapabilities = {
    supportsRate: true,
    supportsSinkId: false,
    supportsEqualizer: false,
    supportsSpectrum: false,
  };

  public constructor() {
    this.plugin = getNativeAudioPlugin();
    if (!this.plugin) this.bindFallbackEvents();
  }

  public init(): void {
    if (this.plugin) {
      void this.plugin.initialize?.();
      void this.bindPluginEvents();
      return;
    }
    this.fallback.init();
  }

  public destroy(): void {
    this.pluginListeners.forEach((listener) => void listener.remove());
    this.pluginListeners = [];
    if (this.plugin) void this.plugin.stop();
    else this.fallback.destroy();
  }

  public async play(url?: string, options?: PlayOptions): Promise<void> {
    if (!this.plugin) return this.fallback.play(url, options);
    if (url && url !== this.state.src) {
      this.state.src = url;
      const song = useMusicStore().playSong;
      const artist = Array.isArray(song.artists)
        ? song.artists.map((item) => item.name).join(" / ")
        : song.artists;
      await this.plugin.load({
        url,
        title: song.name,
        artist,
        artwork: song.coverSize?.m || song.cover,
      });
      if (options?.seek != null) await this.plugin.seek({ time: options.seek });
    }
    await this.plugin.play();
    this.state.paused = false;
  }

  public async resume(options?: { fadeIn?: boolean; fadeDuration?: number }): Promise<void> {
    if (!this.plugin) return this.fallback.resume(options);
    await this.plugin.play();
    this.state.paused = false;
  }

  public pause(_options?: PauseOptions): void {
    if (!this.plugin) {
      this.fallback.pause(_options);
      return;
    }
    void this.plugin.pause();
    this.state.paused = true;
  }

  public stop(): void {
    if (!this.plugin) {
      this.fallback.stop();
      return;
    }
    void this.plugin.stop();
    this.state.currentTime = 0;
    this.state.paused = true;
  }

  public seek(time: number): void {
    if (!this.plugin) {
      void this.fallback.seek(time);
      return;
    }
    this.state.currentTime = time;
    void this.plugin.seek({ time });
  }

  public setVolume(value: number): void {
    this.state.volume = Math.max(0, Math.min(1, value));
    if (this.plugin) void this.plugin.setVolume({ volume: this.state.volume });
    else this.fallback.setVolume(this.state.volume);
  }

  public rampVolumeTo(value: number, duration: number, _curve?: FadeCurve): void {
    if (!this.plugin) {
      this.fallback.rampVolumeTo?.(value, duration);
      return;
    }
    const start = this.state.volume;
    const startedAt = performance.now();
    const tick = () => {
      const progress = Math.min(1, (performance.now() - startedAt) / (duration * 1000));
      this.setVolume(start + (value - start) * progress);
      if (progress < 1) requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  }

  public getVolume(): number {
    return this.plugin ? this.state.volume : this.fallback.getVolume();
  }

  public setRate(value: number): void {
    this.state.rate = value;
    if (this.plugin) void this.plugin.setRate({ rate: value });
    else this.fallback.setRate(value);
  }

  public getRate(): number {
    return this.plugin ? this.state.rate : this.fallback.getRate();
  }

  public setAudioDelayCompensation(offset: number): void {
    if (!this.plugin) this.fallback.setAudioDelayCompensation(offset);
  }

  public async setSinkId(deviceId: string): Promise<void> {
    if (!this.plugin) await this.fallback.setSinkId(deviceId);
  }

  public setReplayGain(gain: number): void {
    if (!this.plugin) this.fallback.setReplayGain?.(gain);
    else this.setVolume(this.state.volume * gain);
  }

  public get src(): string {
    return this.plugin ? this.state.src : this.fallback.src;
  }

  public get duration(): number {
    return this.plugin ? this.state.duration : this.fallback.duration;
  }

  public get currentTime(): number {
    return this.plugin ? this.state.currentTime : this.fallback.currentTime;
  }

  public get paused(): boolean {
    return this.plugin ? this.state.paused : this.fallback.paused;
  }

  public getErrorCode(): number {
    return this.plugin ? this.errorCode : this.fallback.getErrorCode();
  }

  public addEventListener(type: string, listener: EventListenerOrEventListenerObject): void {
    this.listeners.addEventListener(type, listener);
  }

  public removeEventListener(type: string, listener: EventListenerOrEventListenerObject): void {
    this.listeners.removeEventListener(type, listener);
  }

  private bindFallbackEvents(): void {
    Object.values(AUDIO_EVENTS).forEach((eventType) => {
      this.fallback.addEventListener(eventType, (event) => this.dispatch(eventType, event));
    });
  }

  private async bindPluginEvents(): Promise<void> {
    if (!this.plugin) return;
    const events: ["timeUpdate" | "play" | "pause" | "ended" | "error", AudioEventType][] = [
      ["timeUpdate", AUDIO_EVENTS.TIME_UPDATE],
      ["play", AUDIO_EVENTS.PLAY],
      ["pause", AUDIO_EVENTS.PAUSE],
      ["ended", AUDIO_EVENTS.ENDED],
      ["error", AUDIO_EVENTS.ERROR],
    ];
    for (const [nativeEvent, eventType] of events) {
      const handle = await this.plugin.addListener(nativeEvent, (payload) => {
        if (nativeEvent === "timeUpdate") {
          this.state.currentTime = Number(payload.currentTime ?? this.state.currentTime);
          this.state.duration = Number(payload.duration ?? this.state.duration);
        } else if (nativeEvent === "error") {
          this.errorCode = Number(payload.errorCode ?? AudioErrorCode.NETWORK);
        } else if (nativeEvent === "play") {
          this.state.paused = false;
        } else if (nativeEvent === "pause" || nativeEvent === "ended") {
          this.state.paused = true;
        }
        this.dispatch(eventType, payload);
      });
      this.pluginListeners.push(handle);
    }
  }

  private dispatch(type: string, detail?: unknown): void {
    this.listeners.dispatchEvent(new CustomEvent(type, { detail }));
  }
}
