package com.youmo.core.validation;

import java.util.Set;
import java.util.regex.Pattern;

public final class UsernameValidator {

    private static final Pattern FORMAT = Pattern.compile(
        "^[\\u4e00-\\u9fff\\u3400-\\u4dbfa-zA-Z0-9_\\-]{2,20}$");
    private static final Pattern PURE_DIGITS = Pattern.compile("^\\d+$");
    private static final Pattern EMAIL_LIKE = Pattern.compile(".*@.*");
    private static final Set<String> RESERVED = Set.of(
        "admin", "root", "system", "superuser", "moderator",
        "support", "service", "test", "null", "undefined",
        "administrator", "owner", "staff", "manager"
    );

    private UsernameValidator() {}

    public static String validate(String raw) {
        if (raw == null) return "用户名不能为空";
        String name = raw.strip();
        if (name.length() < 2)  return "用户名至少 2 个字符";
        if (name.length() > 20) return "用户名最多 20 个字符";
        if (!FORMAT.matcher(name).matches()) return "用户名只能包含中文、英文、数字、下划线和连字符";
        if (PURE_DIGITS.matcher(name).matches()) return "用户名不能为纯数字";
        if (EMAIL_LIKE.matcher(name).matches()) return "用户名不能包含 @ 符号";
        if (RESERVED.contains(name.toLowerCase())) return "该用户名为系统保留词";
        return null;
    }

    public static String normalize(String raw) {
        return raw == null ? null : raw.strip();
    }
}
