package tk.glucodata.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import tk.glucodata.ui.components.SensorType
import tk.glucodata.ui.components.sensorCatalogFeaturedTypes
import tk.glucodata.ui.components.sensorCatalogOrder
import tk.glucodata.SensorTypeName

class SensorScreenPolicyTests {
    @Test
    fun `active sensor is first and stable order is retained within each group`() {
        data class Row(val id: String, val active: Boolean, val streaming: Boolean)
        val rows = listOf(
            Row("past", false, false),
            Row("secondary", false, true),
            Row("active", true, true),
            Row("past-2", false, false),
        )

        assertEquals(
            listOf("active", "secondary", "past", "past-2"),
            activeSensorsFirst(rows, Row::active, Row::streaming).map(Row::id),
        )
    }

    @Test
    fun `recognized model precedes popular models and catalog remains complete`() {
        val available = listOf(
            SensorType.LIBRE,
            SensorType.DEXCOM,
            SensorType.SIBIONICS,
            SensorType.MEDTRUM,
            SensorType.MQ,
        )
        val ordered = sensorCatalogOrder(available, setOf(SensorType.MEDTRUM))

        assertEquals(SensorType.MEDTRUM, ordered.first())
        assertEquals(available.toSet(), ordered.toSet())
        assertEquals(
            listOf(SensorType.MEDTRUM, SensorType.LIBRE, SensorType.DEXCOM, SensorType.SIBIONICS),
            sensorCatalogFeaturedTypes(ordered, setOf(SensorType.MEDTRUM)),
        )
    }

    @Test
    fun `known sensor family maps to its connection catalog and remote source does not`() {
        assertEquals(SensorType.LIBRE, pickerTypeForSensor(SensorTypeName.LIBRE_3))
        assertEquals(SensorType.SIBIONICS, pickerTypeForSensor(SensorTypeName.SIBIONICS_GS3))
        assertEquals(null, pickerTypeForSensor(SensorTypeName.NIGHTSCOUT))
    }
}
