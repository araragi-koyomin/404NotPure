export function parseRole(role: string | null) {
    if (role === "ADMIN") {
        return "管理员"
    } else if (role === "USER") {
        return "用户"
    }
}

export function parseTime(time: string) {
    let times = time.split(/T|\./)
    return times[0] + " " + times[1]
}

export function runWithTimeout(
    task: () => Promise<void>,
    timeout: number,
    timeoutError = new Error(`Task timed out after ${timeout}ms`)
): Promise<void> {
    let timeoutId: ReturnType<typeof setTimeout>;

    const timeoutPromise = new Promise<never>((_, reject) => {
        timeoutId = setTimeout(() => reject(timeoutError), timeout);
    });

    return Promise.race([
        task().finally(() => clearTimeout(timeoutId)),
        timeoutPromise
    ]);
}