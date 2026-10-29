package tk.glucodata

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GisBridgeContractTests {
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "Common/src/main/AndroidManifest.xml").isFile }

    @Test
    fun providerUsesPackageAllowlistInsteadOfAnUnusableSignatureReadPermission() {
        val manifest = File(root, "Common/src/main/AndroidManifest.xml").readText()
        val provider = manifest.substringAfter("android:name=\"tk.glucodata.GisBridge\"")
            .substringBefore("/>")
        assertFalse(provider.contains("android:readPermission="))

        val source = File(root, "Common/src/main/java/tk/glucodata/GisBridge.java").readText()
        assertTrue(source.contains("SOLMI_APP_PACKAGE = \"ru.solmi.app\""))
        assertTrue(source.contains("getPackagesForUid"))
        assertTrue(source.contains("if (!isAllowedReader()) throw new SecurityException"))
    }

    @Test
    fun publishingIsIndependentFromOptionalXdripBroadcast() {
        val central = File(root, "Common/src/main/java/tk/glucodata/SuperGattCallback.java").readText()
        val xdrip = File(root, "Common/src/main/java/tk/glucodata/SendLikexDrip.java").readText()
        assertTrue(central.contains("GisBridge.shareEnabled(app)"))
        assertTrue(central.contains("GisBridge.publishPayload(exchangePayload, sensorstartmsec)"))
        assertFalse(xdrip.contains("GisBridge.publishPayload"))
    }
}
