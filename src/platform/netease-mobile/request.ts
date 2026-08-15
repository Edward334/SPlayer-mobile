import { CapacitorCookies, CapacitorHttp, type HttpResponse } from "@capacitor/core";
import { setCookies } from "@/utils/cookie";
import { encryptEapi, encryptWeapi } from "./crypto";
import {
  getMobileNeteaseRoute,
  type MobileNeteaseOptions,
  type MobileNeteaseRequest,
  type MobileNeteaseResponse,
} from "./routes";

const WEB_DOMAIN = "https://music.163.com";
const API_DOMAIN = "https://interface.music.163.com";
const WEB_USER_AGENT =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36";
const API_USER_AGENT = "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)";

const randomHex = (length: number): string => {
  const bytes = crypto.getRandomValues(new Uint8Array(Math.ceil(length / 2)));
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0"))
    .join("")
    .slice(0, length);
};

const getDeviceId = (): string => {
  const key = "netease-mobile-device-id";
  const saved = localStorage.getItem(key);
  if (saved) return saved;
  const value = randomHex(32);
  localStorage.setItem(key, value);
  return value;
};

const parseCookie = (cookie?: string | Record<string, string>): Record<string, string> => {
  if (!cookie) return {};
  if (typeof cookie !== "string") return { ...cookie };
  return cookie.split(";").reduce<Record<string, string>>((result, item) => {
    const separator = item.indexOf("=");
    if (separator <= 0) return result;
    result[item.slice(0, separator).trim()] = item.slice(separator + 1).trim();
    return result;
  }, {});
};

const serializeCookie = (cookie: Record<string, string>): string =>
  Object.entries(cookie)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join("; ");

const responseCookies = async (response: HttpResponse): Promise<string[]> => {
  const header = Object.entries(response.headers).find(
    ([key]) => key.toLowerCase() === "set-cookie",
  )?.[1];
  const headerValues = header
    ? (Array.isArray(header) ? header : header.split(/,(?=[^;,]+=)/)).map((value) =>
        value.replace(/\s*Domain=[^;]+;?/i, "").trim(),
      )
    : [];
  const nativeCookies = await CapacitorCookies.getCookies({ url: WEB_DOMAIN });
  const cookieMap = new Map<string, string>();
  [
    ...headerValues,
    ...Object.entries(nativeCookies).map(([key, value]) => `${key}=${value}`),
  ].forEach((cookie) => {
    const separator = cookie.indexOf("=");
    if (separator > 0) cookieMap.set(cookie.slice(0, separator), cookie);
  });
  return [...cookieMap.values()];
};

const normalizeBody = (data: unknown): Record<string, any> => {
  if (typeof data !== "string") return (data ?? {}) as Record<string, any>;
  try {
    return JSON.parse(data) as Record<string, any>;
  } catch {
    return { code: 500, message: data };
  }
};

const nativeRequest: MobileNeteaseRequest = async (uri, sourceData, options = {}) => {
  const requestOptions: MobileNeteaseOptions = options;
  const cookie = parseCookie(requestOptions.cookie);
  const now = Date.now();
  const deviceId = cookie.deviceId || getDeviceId();
  const data: Record<string, any> = { ...sourceData, e_r: false };
  const headers: Record<string, string> = {
    "Content-Type": "application/x-www-form-urlencoded;charset=utf-8",
  };
  let url: string;
  let payload: Record<string, unknown>;
  const cryptoMode = requestOptions.crypto || "eapi";

  if (requestOptions.realIP) {
    headers["X-Real-IP"] = requestOptions.realIP;
    headers["X-Forwarded-For"] = requestOptions.realIP;
  }

  if (cryptoMode === "weapi") {
    const csrfToken = cookie.__csrf || "";
    data.csrf_token = csrfToken;
    headers.Referer = requestOptions.domain || WEB_DOMAIN;
    headers["User-Agent"] = requestOptions.ua || WEB_USER_AGENT;
    headers.Cookie = serializeCookie({ ...cookie, os: cookie.os || "pc" });
    url = `${requestOptions.domain || WEB_DOMAIN}/weapi/${uri.slice(5)}`;
    payload = encryptWeapi(data);
  } else {
    const clientCookie = {
      osver: cookie.osver || "16.2",
      deviceId,
      os: cookie.os || "iPhone OS",
      appver: cookie.appver || "9.0.90",
      versioncode: cookie.versioncode || "140",
      buildver: cookie.buildver || String(now).slice(0, 10),
      resolution: cookie.resolution || "1920x1080",
      __csrf: cookie.__csrf || "",
      channel: cookie.channel || "distribution",
      requestId: `${now}_${Math.floor(Math.random() * 1000)
        .toString()
        .padStart(4, "0")}`,
      ...(cookie.MUSIC_U ? { MUSIC_U: cookie.MUSIC_U } : {}),
      ...(cookie.MUSIC_A ? { MUSIC_A: cookie.MUSIC_A } : {}),
    };
    headers.Cookie = serializeCookie(clientCookie);
    headers["User-Agent"] = requestOptions.ua || API_USER_AGENT;
    if (cryptoMode === "api") {
      url = `${requestOptions.domain || API_DOMAIN}${uri}`;
      payload = data;
    } else {
      data.header = clientCookie;
      url = `${requestOptions.domain || API_DOMAIN}/eapi/${uri.slice(5)}`;
      payload = encryptEapi(uri, data);
    }
  }

  const response = await CapacitorHttp.post({
    url,
    data: new URLSearchParams(
      Object.entries(payload).map(([key, value]) => [key, String(value)]),
    ).toString(),
    headers,
    connectTimeout: 15000,
    readTimeout: 15000,
  });
  const cookies = await responseCookies(response);
  cookies.forEach(setCookies);
  const body = normalizeBody(response.data);
  if (body.code !== undefined) body.code = Number(body.code);
  return { status: Number(body.code || response.status), body, cookie: cookies };
};

export const mobileNeteaseRequest = async (
  path: string,
  query: Record<string, any>,
): Promise<Record<string, any>> => {
  const route = getMobileNeteaseRoute(path);
  if (!route) throw new Error(`Unsupported mobile Netease route: ${path}`);
  const processScope = globalThis as unknown as { process?: { env: Record<string, string> } };
  processScope.process ??= { env: {} };
  const response: MobileNeteaseResponse = await route(query, nativeRequest);
  return response.body;
};
