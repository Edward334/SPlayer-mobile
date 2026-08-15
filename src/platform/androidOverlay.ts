import type { PluginListenerHandle } from "./capacitor-types";

export interface AndroidOverlayPlugin {
  checkPermission: () => Promise<{ granted: boolean }>;
  requestPermission: () => Promise<{ granted: boolean }>;
  show: (options: { title: string; artist: string; lyric: string }) => Promise<void>;
  update: (options: { title: string; artist: string; lyric: string }) => Promise<void>;
  hide: () => Promise<void>;
  addListener?: (
    eventName: "overlayAction",
    listenerFunc: (event: { action: "play" | "pause" | "next" | "previous" }) => void,
  ) => Promise<PluginListenerHandle>;
}

const getPlugin = (): AndroidOverlayPlugin | null => {
  if (window.Capacitor?.getPlatform?.() !== "android") return null;
  return (window.Capacitor.Plugins?.SPlayerOverlay as AndroidOverlayPlugin | undefined) ?? null;
};

export const checkAndroidOverlayPermission = async (): Promise<boolean> => {
  const plugin = getPlugin();
  if (!plugin) return false;
  return (await plugin.checkPermission()).granted;
};

export const requestAndroidOverlayPermission = async (): Promise<boolean> => {
  const plugin = getPlugin();
  if (!plugin) return false;
  return (await plugin.requestPermission()).granted;
};

export const showAndroidOverlayLyric = async (payload: {
  title: string;
  artist: string;
  lyric: string;
}): Promise<boolean> => {
  const plugin = getPlugin();
  if (!plugin || !(await plugin.checkPermission()).granted) return false;
  await plugin.show(payload);
  return true;
};

export const updateAndroidOverlayLyric = async (payload: {
  title: string;
  artist: string;
  lyric: string;
}): Promise<boolean> => {
  const plugin = getPlugin();
  if (!plugin || !(await plugin.checkPermission()).granted) return false;
  await plugin.update(payload);
  return true;
};

export const hideAndroidOverlayLyric = async (): Promise<void> => {
  await getPlugin()?.hide();
};
