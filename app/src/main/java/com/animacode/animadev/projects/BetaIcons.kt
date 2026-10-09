package com.animacode.animadev.projects

import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object BetaIcons {
    val Folder=ImageVector.Builder("Projects",24.dp,24.dp,24f,24f).apply { path(fill=SolidColor(Color.Black),pathFillType=PathFillType.EvenOdd) { moveTo(3f,4f);lineTo(10f,4f);lineTo(12f,6f);lineTo(21f,6f);lineTo(21f,20f);lineTo(3f,20f);close() } }.build()
    val Terminal=ImageVector.Builder("Termux",24.dp,24.dp,24f,24f).apply { path(fill=SolidColor(Color.Black),pathFillType=PathFillType.EvenOdd) { moveTo(4f,4f);lineTo(20f,4f);lineTo(20f,20f);lineTo(4f,20f);close();moveTo(6f,7f);lineTo(10f,11f);lineTo(6f,15f);lineTo(7.5f,16.5f);lineTo(13f,11f);lineTo(7.5f,5.5f);close();moveTo(12f,15f);lineTo(18f,15f);lineTo(18f,17f);lineTo(12f,17f);close() } }.build()
    val Git=ImageVector.Builder("Git",24.dp,24.dp,24f,24f).apply { path(fill=SolidColor(Color.Black),pathFillType=PathFillType.EvenOdd) { moveTo(11f,2f);lineTo(22f,13f);lineTo(13f,22f);lineTo(2f,11f);close() } }.build()
    val Tests=ImageVector.Builder("Tests",24.dp,24.dp,24f,24f).apply { path(fill=SolidColor(Color.Black),pathFillType=PathFillType.EvenOdd) { moveTo(9f,3f);lineTo(15f,3f);lineTo(15f,5f);lineTo(14f,5f);lineTo(14f,10f);lineTo(20f,20f);lineTo(4f,20f);lineTo(10f,10f);lineTo(10f,5f);lineTo(9f,5f);close() } }.build()
    val Build=ImageVector.Builder("Build",24.dp,24.dp,24f,24f).apply { path(fill=SolidColor(Color.Black),pathFillType=PathFillType.EvenOdd) { moveTo(6f,3f);lineTo(18f,3f);lineTo(18f,21f);lineTo(6f,21f);close() } }.build()
    val History=ImageVector.Builder("History",24.dp,24.dp,24f,24f).apply { path(fill=SolidColor(Color.Black),pathFillType=PathFillType.EvenOdd) { moveTo(4f,4f);lineTo(20f,4f);lineTo(20f,20f);lineTo(4f,20f);close();moveTo(7f,7f);lineTo(7f,9f);lineTo(17f,9f);lineTo(17f,7f);close();moveTo(7f,11f);lineTo(7f,13f);lineTo(17f,13f);lineTo(17f,11f);close();moveTo(7f,15f);lineTo(7f,17f);lineTo(14f,17f);lineTo(14f,15f);close() } }.build()
}
