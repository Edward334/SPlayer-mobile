import type { MetaData } from "@/types/main";

export const isMetaData = (value: unknown): value is MetaData => {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<MetaData>;
  return typeof candidate.id === "number" && typeof candidate.name === "string";
};
