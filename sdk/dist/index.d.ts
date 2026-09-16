export interface MistError {
    code: string;
    message: string;
}

export interface MistSDK {
    call(api: string, args?: Record<string, any>): Promise<any>;
    app: {
        info(): Promise<any>;
        id(): Promise<{ id: string }>;
        version(): Promise<{ version: string; runtimeVersion: string }>;
        close(): Promise<any>;
        getLaunchInfo(): Promise<any>;
    };
    ui: {
        toast(text: string): Promise<any>;
        vibrate(duration?: number): Promise<any>;
    };
    storage: {
        get(key: string): Promise<{ key: string; value?: string; exists: boolean }>;
        set(key: string, value: string | number | boolean): Promise<{ key: string; stored: boolean }>;
        remove(key: string): Promise<{ removed: boolean }>;
        clear(): Promise<{ cleared: boolean }>;
        has(key: string): Promise<{ has: boolean }>;
        keys(): Promise<{ keys: string[] }>;
    };
    files: {
        read(path: string): Promise<{ path: string; content: string }>;
        write(path: string, content: string): Promise<{ path: string; bytesWritten: number; written: boolean }>;
        delete(path: string): Promise<{ path: string; deleted: boolean }>;
        exists(path: string): Promise<{ path: string; exists: boolean }>;
        info(path: string): Promise<{ path: string; size: number; isFile: boolean; isDirectory: boolean }>;
    };
    camera: {
        isAvailable(): Promise<{ available: boolean }>;
        takePhoto(): Promise<any>;
        pickPhoto(): Promise<any>;
        getCameras(): Promise<{ cameras: string }>;
    };
    microphone: {
        isAvailable(): Promise<{ available: boolean }>;
        requestPermission(): Promise<{ granted: boolean }>;
    };
    device: {
        info(): Promise<any>;
    };
    network: {
        fetch(url: string, options?: { method?: string; body?: string }): Promise<{ status: number; data: string; ok: boolean }>;
    };
    clipboard: {
        read(): Promise<{ text: string }>;
        write(text: string): Promise<{ copied: boolean }>;
    };
    share: {
        text(text: string, title?: string): Promise<{ shared: boolean }>;
    };
    browser: {
        open(url: string): Promise<{ opened: boolean }>;
    };
    phone: {
        openDialer(number: string): Promise<{ opened: boolean }>;
    };
    sms: {
        openComposer(number: string, body?: string): Promise<{ opened: boolean }>;
    };
    notifications: {
        send(title: string, body: string): Promise<{ sent: boolean }>;
    };
    sensors: {
        available(): Promise<{ accelerometer: boolean; gyroscope: boolean; light: boolean }>;
    };
    location: {
        getCurrent(): Promise<{ latitude: number; longitude: number; accuracy: number }>;
    };
    biometric: {
        isAvailable(): Promise<{ available: boolean }>;
        authenticate(): Promise<{ authenticated: boolean }>;
    };
    bluetooth: {
        isAvailable(): Promise<{ available: boolean }>;
    };
    nfc: {
        isAvailable(): Promise<{ available: boolean }>;
    };
    background: {
        schedule(taskId: string, delay?: number): Promise<{ taskId: string; scheduled: boolean; delay: number }>;
        cancel(taskId: string): Promise<{ taskId: string; cancelled: boolean }>;
    };
}

declare global {
    interface Window {
        Mist: MistSDK;
        MistFoxSDK: MistSDK;
        /** @deprecated Deprecated compatibility alias */
        ZS: MistSDK;
    }
}
