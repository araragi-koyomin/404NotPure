// src/types/global.d.ts
import 'vue';

declare global {
    interface WindowEventMap {
        'sessionstorage-local-update': CustomEvent<{
            key: string;
            value: string;
        }>;
    }
}