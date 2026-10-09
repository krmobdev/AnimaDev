package com.animacode.animadev.projects

import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.PathNode
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class SettingsVectorTest {
    private fun checkVector(name:String) {
        val relative="src/main/res/drawable/ms_$name.xml"
        val file=listOf(File(relative),File("app/$relative")).firstOrNull {it.isFile} ?: error("Missing vector $name")
        val factory=DocumentBuilderFactory.newInstance().apply {isNamespaceAware=true}
        val doc=factory.newDocumentBuilder().parse(file)
        val android="http://schemas.android.com/apk/res/android"
        val root=doc.documentElement
        assertEquals("960",root.getAttributeNS(android,"viewportHeight"))
        val group=doc.getElementsByTagName("group").item(0) as org.w3c.dom.Element
        assertEquals("960",group.getAttributeNS(android,"translateY"))
        val path=doc.getElementsByTagName("path").item(0) as org.w3c.dom.Element
        val nodes=PathParser().parsePathString(path.getAttributeNS(android,"pathData")).toNodes()
        assertTrue("Path must contain actual drawing instructions",nodes.size>10)
        assertTrue(nodes.first() is PathNode.MoveTo)
        assertTrue(nodes.last() is PathNode.Close)
    }
    @Test fun paletteIsRenderable()=checkVector("palette")
    @Test fun notificationIsRenderable()=checkVector("notifications")
    @Test fun waitingIsRenderable()=checkVector("hourglass_empty")
}
