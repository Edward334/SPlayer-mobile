import type { PluginListenerHandle } from "./capacitor-types";

export interface NativeAudioState {
  src: string;
  duration: number;
  currentTime: number;
  paused: boolean;
  volume: number;
  rate: number;
}

export interface NativeAudioPlugin {
  initialize?: () => Promise<void>;
  load: (options: {
    url: string;
    title?: string;
    artist?: string;
    artwork?: string;
  }) => Promise<void>;
  play: () => Promise<void>;
  pause: () => Promise<void>;
  stop: () => Promise<void>;
  seek: (options: { time: number }) => Promise<void>;
  setVolume: (options: { volume: number }) => Promise<void>;
  setRate: (options: { rate: number }) => Promise<void>;
  getState: () => Promise<NativeAudioState>;
  addListener: (
    eventName: "timeUpdate" | "play" | "pause" | "ended" | "error",
    listenerFunc: (event: Record<string, unknown>) => void,
  ) => Promise<PluginListenerHandle>;
}

export const getNativeAudioPlugin = (): NativeAudioPlugin | null => {
  const capacitor = window.Capacitor;
  if (!capacitor?.isNativePlatform?.()) return null;
  return (capacitor.Plugins?.SPlayerAudio as NativeAudioPlugin | undefined) ?? null;
};

export const isCapacitor = (): boolean => Boolean(window.Capacitor?.isNativePlatform?.());
