// src/utils/storage.ts
import { ref, watchEffect, type Ref } from 'vue';

type UseReactiveSessionStorageReturn = {
    value: Ref<string>;
    set: (newValue: string) => void;
};

export function useReactiveSessionStorage(
    key: string,
    defaultValue: string = ''
): UseReactiveSessionStorageReturn {
    // 安全获取初始值（处理 SSR 场景）
    const initialValue = typeof window !== 'undefined'
        ? sessionStorage.getItem(key)
        : null;

    // 创建响应式引用（明确字符串类型）
    const storedValue = ref<string>(initialValue || defaultValue);

    // 响应当前窗口的修改
    watchEffect(() => {
        const latestValue = sessionStorage.getItem(key);
        if (latestValue !== storedValue.value) {
            storedValue.value = latestValue ?? defaultValue;
        }
    });

    // 监听跨窗口修改（类型安全处理）
    const storageHandler = (e: StorageEvent) => {
        if (e.key === key && e.newValue !== null) {
            storedValue.value = e.newValue;
        }
    };

    window.addEventListener('storage', storageHandler);

    // 返回操作方法（明确字符串参数类型）
    return {
        value: storedValue,
        set: (newValue: string) => {
            sessionStorage.setItem(key, newValue);
            storedValue.value = newValue; // 立即更新当前窗口
        }
    };
}