package com.animacode.animadev.projects

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbEndpointTest {
    @Test fun acceptsPrivateNetworkPixelConnectPorts() {
        assertTrue(AdbDeviceGateway.validLanEndpoint("192.168.1.132:37123"))
        assertTrue(AdbDeviceGateway.validLanEndpoint("10.2.0.5:5555"))
        assertTrue(AdbDeviceGateway.validLanEndpoint("172.16.0.18:60000"))
    }
    @Test fun rejectsMissingPortAndUntrustedDestinations() {
        for (value in listOf("192.168.1.132","192.168.1.132:0","192.168.1.132:100000",
            "8.8.8.8:5555","example.com:5555","127.0.0.1:5555",
            "192.168.1.132:4444; echo hacked","192.168.1.132:-1",
            "192.168.1.999:5555","192.168.1.132:xyz"))
            assertFalse(value,AdbDeviceGateway.validLanEndpoint(value))
    }
}
