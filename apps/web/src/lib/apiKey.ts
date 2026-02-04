const KEY_NAME = "CRYPTOLINK_API_KEY";

export function getApiKey(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(KEY_NAME);
}

export function setApiKey(value: string) {
  window.localStorage.setItem(KEY_NAME, value.trim());
}

export function clearApiKey() {
  window.localStorage.removeItem(KEY_NAME);
}
