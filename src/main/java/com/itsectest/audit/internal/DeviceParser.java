package com.itsectest.audit.internal;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class DeviceParser {

    private record Rule(String name, Pattern pattern) {
    }

    private static final List<Rule> BROWSERS = List.of(
            new Rule("Edge", Pattern.compile("Edg(?:e|A|iOS)?/([0-9.]+)")),
            new Rule("Opera", Pattern.compile("OPR/([0-9.]+)")),
            new Rule("Samsung Internet", Pattern.compile("SamsungBrowser/([0-9.]+)")),
            new Rule("Firefox", Pattern.compile("(?:Firefox|FxiOS)/([0-9.]+)")),
            new Rule("Chrome", Pattern.compile("(?:Chrome|CriOS)/([0-9.]+)")),
            new Rule("Safari", Pattern.compile("Version/([0-9.]+).*Safari")),
            new Rule("Internet Explorer", Pattern.compile("Trident/[0-9.]+.*?rv:([0-9.]+)")),
            new Rule("Internet Explorer", Pattern.compile("MSIE ([0-9.]+)")));

    private static final List<Rule> CLIENTS = List.of(
            new Rule("curl", Pattern.compile("curl/([0-9.]+)")),
            new Rule("Postman", Pattern.compile("PostmanRuntime/([0-9.]+)")),
            new Rule("Insomnia", Pattern.compile("insomnia/([0-9.]+)")),
            new Rule("HTTPie", Pattern.compile("HTTPie/([0-9.]+)")),
            new Rule("OkHttp", Pattern.compile("okhttp/([0-9.]+)")),
            new Rule("python-requests", Pattern.compile("python-requests/([0-9.]+)")),
            new Rule("Python urllib", Pattern.compile("Python-urllib/([0-9.]+)")),
            new Rule("Java", Pattern.compile("Java/([0-9._]+)")),
            new Rule("Go", Pattern.compile("Go-http-client/([0-9.]+)")));

    private static final List<Rule> OPERATING_SYSTEMS = List.of(
            new Rule("Windows", Pattern.compile("Windows NT ([0-9.]+)")),
            new Rule("Android", Pattern.compile("Android ([0-9.]+)")),
            new Rule("iOS", Pattern.compile("(?:iPhone|iPad|CPU) OS ([0-9_]+)")),
            new Rule("macOS", Pattern.compile("Mac OS X ([0-9_.]+)")),
            new Rule("ChromeOS", Pattern.compile("CrOS [^ ]+ ([0-9.]+)")),
            new Rule("Linux", Pattern.compile("(Linux)")));

    private static final Pattern BOT = Pattern.compile("(?i)(bot|crawler|spider|slurp|monitoring)");
    private static final Pattern TABLET = Pattern.compile("(?i)(iPad|Tablet|Nexus (?:7|9|10)|Kindle)");
    private static final Pattern MOBILE = Pattern.compile("(?i)(Mobile|iPhone|iPod|Android.*Mobile|Windows Phone)");

    public DeviceInfo parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return DeviceInfo.UNKNOWN;
        }
        if (BOT.matcher(userAgent).find()) {
            return new DeviceInfo("Bot", null, detectOs(userAgent), "BOT");
        }

        DeviceInfo client = firstMatch(CLIENTS, userAgent);
        if (client != null) {
            return new DeviceInfo(client.browser(), client.browserVersion(), detectOs(userAgent), "API_CLIENT");
        }

        DeviceInfo browser = firstMatch(BROWSERS, userAgent);
        String name = browser == null ? "Unknown" : browser.browser();
        String version = browser == null ? null : browser.browserVersion();

        return new DeviceInfo(name, version, detectOs(userAgent), detectDeviceType(userAgent));
    }

    private DeviceInfo firstMatch(List<Rule> rules, String userAgent) {
        for (Rule rule : rules) {
            Matcher matcher = rule.pattern().matcher(userAgent);
            if (matcher.find()) {
                String version = matcher.groupCount() >= 1 ? matcher.group(1) : null;
                return new DeviceInfo(rule.name(), normaliseVersion(version), null, null);
            }
        }
        return null;
    }

    private String detectOs(String userAgent) {
        for (Rule rule : OPERATING_SYSTEMS) {
            Matcher matcher = rule.pattern().matcher(userAgent);
            if (matcher.find()) {
                if ("Linux".equals(rule.name())) {
                    return "Linux";
                }
                String version = normaliseVersion(matcher.group(1));
                return "Windows".equals(rule.name())
                        ? "Windows " + windowsName(version)
                        : rule.name() + " " + version;
            }
        }
        return "Unknown";
    }

    private String detectDeviceType(String userAgent) {
        if (TABLET.matcher(userAgent).find()) {
            return "TABLET";
        }
        return MOBILE.matcher(userAgent).find() ? "MOBILE" : "DESKTOP";
    }

    private String windowsName(String ntVersion) {
        return switch (ntVersion) {
            case "10.0" -> "10/11";
            case "6.3" -> "8.1";
            case "6.2" -> "8";
            case "6.1" -> "7";
            default -> ntVersion;
        };
    }

    private String normaliseVersion(String version) {
        return version == null ? null : version.replace('_', '.');
    }
}
