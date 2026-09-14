package com.itsectest.audit.internal;

public record DeviceInfo(String browser, String browserVersion, String os, String deviceType) {

    static final DeviceInfo UNKNOWN = new DeviceInfo("Unknown", null, "Unknown", "UNKNOWN");
}
