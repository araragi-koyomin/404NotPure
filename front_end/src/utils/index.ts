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
