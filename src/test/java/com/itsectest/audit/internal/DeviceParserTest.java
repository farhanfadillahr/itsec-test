package com.itsectest.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DeviceParserTest {

    private final DeviceParser parser = new DeviceParser();

    @ParameterizedTest(name = "{1} on {2} is a {3}")
    @CsvSource(delimiter = '|', value = {
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 | Chrome | macOS 10.15.7 | DESKTOP",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0 | Edge | Windows 10/11 | DESKTOP",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36 OPR/124.0.0.0 | Opera | Windows 10/11 | DESKTOP",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:132.0) Gecko/20100101 Firefox/132.0 | Firefox | macOS 10.15 | DESKTOP",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.2 Mobile/15E148 Safari/604.1 | Safari | iOS 18.2 | MOBILE",
            "Mozilla/5.0 (iPad; CPU OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/604.1 | Safari | iOS 17.6 | TABLET",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36 | Chrome | Android 14 | MOBILE",
            "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/23.0 Chrome/115.0.0.0 Mobile Safari/537.36 | Samsung Internet | Android 13 | MOBILE",
            "Mozilla/5.0 (Windows NT 6.1; Trident/7.0; rv:11.0) like Gecko | Internet Explorer | Windows 7 | DESKTOP",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 | Chrome | Linux | DESKTOP"
    })
    void recognisesRealBrowsers(String userAgent, String browser, String os, String deviceType) {
        DeviceInfo info = parser.parse(userAgent);

        assertThat(info.browser()).isEqualTo(browser);
        assertThat(info.os()).isEqualTo(os);
        assertThat(info.deviceType()).isEqualTo(deviceType);
    }

    @ParameterizedTest(name = "{1} is reported as an API client")
    @CsvSource(delimiter = '|', value = {
            "curl/8.7.1 | curl",
            "PostmanRuntime/7.44.1 | Postman",
            "insomnia/10.1.1 | Insomnia",
            "python-requests/2.32.3 | python-requests",
            "Python-urllib/3.13 | Python urllib",
            "okhttp/4.12.0 | OkHttp",
            "Java/21.0.5 | Java",
            "Go-http-client/2.0 | Go"
    })
    void separatesToolsFromBrowsers(String userAgent, String expected) {
        DeviceInfo info = parser.parse(userAgent);

        assertThat(info.browser()).isEqualTo(expected);
        assertThat(info.deviceType()).isEqualTo("API_CLIENT");
    }

    @Test
    void extractsTheBrowserVersion() {
        DeviceInfo info = parser.parse("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.7390.55 Safari/537.36");

        assertThat(info.browserVersion()).isEqualTo("141.0.7390.55");
    }

    @Test
    void translatesAppleUnderscoreVersions() {
        assertThat(parser.parse("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)").os())
                .isEqualTo("macOS 10.15.7");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
            "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)",
            "Mozilla/5.0 (compatible; YandexBot/3.0)"
    })
    void flagsCrawlers(String userAgent) {
        assertThat(parser.parse(userAgent).deviceType()).isEqualTo("BOT");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   " })
    void survivesAMissingHeader(String userAgent) {
        DeviceInfo info = parser.parse(userAgent);

        assertThat(info.browser()).isEqualTo("Unknown");
        assertThat(info.deviceType()).isEqualTo("UNKNOWN");
    }

    @Test
    void fallsBackGracefullyOnSomethingItHasNeverSeen() {
        DeviceInfo info = parser.parse("TotallyMadeUpClient/1.0");

        assertThat(info.browser()).isEqualTo("Unknown");
        assertThat(info.os()).isEqualTo("Unknown");
        assertThat(info.deviceType()).isEqualTo("DESKTOP");
    }
}
