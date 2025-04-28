export function parseRole(role: string | null) {
    if (role === "ADMIN") {
        return "管理员"
    } else if (role === "USER") {
        return "用户"
    }
}

export function parseBookCategory(category: string | null): string {
    if (category === "literature") {
        return "文学小说";
    } else if (category === "biography" || category === "history") {
        return "历史传记";
    } else if (category === "philosophy" || category === "religion") {
        return "哲学宗教";
    } else if (category === "art" || category === "design") {
        return "艺术设计";
    } else if (category === "science") {
        return "科学技术";
    } else if (category === "computer" || category === "internet") {
        return "计算机与互联网";
    } else if (category === "medical" || category === "health") {
        return "医学与健康";
    } else if (category === "education" || category === "exam") {
        return "教育考试";
    } else if (category === "economics" || category === "management") {
        return "经济管理";
    } else if (category === "politics" || category === "law") {
        return "政治法律";
    } else if (category === "social") {
        return "社会科学";
    } else if (category === "travel" || category === "geography") {
        return "旅行与地理";
    } else if (category === "children") {
        return "儿童读物";
    } else {
        return "未知类别";
    }
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