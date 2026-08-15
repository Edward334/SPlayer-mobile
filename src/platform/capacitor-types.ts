export interface PluginListenerHandle {
  remove: () => Promise<void>;
}

export interface CapacitorRuntime {
  Plugins?: Record<string, unknown>;
  isNativePlatform?: () => boolean;
  getPlatform?: () => "android" | "ios" | "web";
}

declare global {
  interface Window {
    Capacitor?: CapacitorRuntime;
  }
}
