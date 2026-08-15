declare module "crypto-js" {
  const CryptoJS: any;
  export default CryptoJS;
}

declare module "@neteasecloudmusicapienhanced/api/module/*.js" {
  const route: import("@/platform/netease-mobile/routes").MobileNeteaseRoute;
  export default route;
}

declare module "qrcode" {
  const QRCode: {
    toDataURL: (text: string) => Promise<string>;
  };
  export default QRCode;
}
