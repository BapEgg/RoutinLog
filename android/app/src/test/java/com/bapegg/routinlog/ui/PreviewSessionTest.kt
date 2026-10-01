package com.bapegg.routinlog.ui

import org.junit.Assert.*
import org.junit.Test

class PreviewSessionTest {
    @Test fun detailBackReturnsToOriginatingFeature(){
        val ui=PreviewSession();ui.startPreview();ui.go("W01");ui.go("W08");ui.go("W09");ui.back()
        assertEquals("W08",ui.route);ui.back();assertEquals("W01",ui.route)
    }
    @Test fun rootBackExitsTheGuestFlow(){
        val ui=PreviewSession();ui.startPreview();ui.go("F01");ui.back();assertEquals("A01",ui.route)
    }
    @Test fun resetClearsAllTemporaryPersonalValues(){
        val ui=PreviewSession();ui.startPreview();ui.set("body.weight","83.2");ui.go("F03");ui.reset()
        assertTrue(ui.values.isEmpty());assertFalse(ui.previewMode);assertEquals("A01",ui.route)
    }
    @Test fun theCatalogIncludesEveryDesignedRouteAndValidBackTargets(){
        assertEquals(73,ScreenCatalog.size)
        ScreenCatalog.values.forEach{assertTrue("Missing back route for ${it.id}",it.back==null||it.back in ScreenCatalog)}
    }
}
