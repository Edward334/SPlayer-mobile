import CryptoJS from "crypto-js";

const IV = "0102030405060708";
const PRESET_KEY = "0CoJUm6Qyw8W8jud";
const EAPI_KEY = "e82ckenh8dichen8";
const BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
const RSA_EXPONENT = 0x10001n;
const RSA_MODULUS = BigInt(
  "0x00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7" +
    "b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280" +
    "104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932" +
    "575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b" +
    "3ece0462db0a22b8e7",
);

interface EncryptedPayload {
  [key: string]: string | undefined;
  params: string;
  encSecKey?: string;
}

const aesEncrypt = (
  value: string,
  key: string,
  mode: "CBC" | "ECB",
  format: "base64" | "hex",
): string => {
  const encrypted = CryptoJS.AES.encrypt(
    CryptoJS.enc.Utf8.parse(value),
    CryptoJS.enc.Utf8.parse(key),
    {
      iv: mode === "CBC" ? CryptoJS.enc.Utf8.parse(IV) : undefined,
      mode: CryptoJS.mode[mode],
      padding: CryptoJS.pad.Pkcs7,
    },
  );
  return format === "hex" ? encrypted.ciphertext.toString().toUpperCase() : encrypted.toString();
};

const modPow = (base: bigint, exponent: bigint, modulus: bigint): bigint => {
  let result = 1n;
  let factor = base % modulus;
  let power = exponent;
  while (power > 0n) {
    if (power & 1n) result = (result * factor) % modulus;
    factor = (factor * factor) % modulus;
    power >>= 1n;
  }
  return result;
};

const textToBigInt = (value: string): bigint => {
  const bytes = new TextEncoder().encode(value);
  let hex = "";
  bytes.forEach((byte) => {
    hex += byte.toString(16).padStart(2, "0");
  });
  return BigInt(`0x${hex}`);
};

const randomSecret = (): string => {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, (byte) => BASE62[byte % BASE62.length]).join("");
};

export const encryptWeapi = (data: Record<string, unknown>): EncryptedPayload => {
  const secret = randomSecret();
  const firstPass = aesEncrypt(JSON.stringify(data), PRESET_KEY, "CBC", "base64");
  const reversedSecret = secret.split("").reverse().join("");
  const encSecKey = modPow(textToBigInt(reversedSecret), RSA_EXPONENT, RSA_MODULUS)
    .toString(16)
    .padStart(256, "0");
  return {
    params: aesEncrypt(firstPass, secret, "CBC", "base64"),
    encSecKey,
  };
};

export const encryptEapi = (url: string, data: Record<string, unknown>): EncryptedPayload => {
  const text = JSON.stringify(data);
  const digest = CryptoJS.MD5(`nobody${url}use${text}md5forencrypt`).toString();
  const message = `${url}-36cd479b6b5-${text}-36cd479b6b5-${digest}`;
  return { params: aesEncrypt(message, EAPI_KEY, "ECB", "hex") };
};
