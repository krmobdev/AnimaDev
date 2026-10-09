package com.animacode.animadev.projects
import org.junit.Assert.*
import org.junit.Test
class SettingsPolicyTest {
    @Test fun searchActualPreferences() {assertEquals("theme",SettingsPolicy.search("тЁмная").single().id);assertEquals("folder",SettingsPolicy.search("артефакты").single().id);assertTrue(SettingsPolicy.search("несуществующий").isEmpty())}
    @Test fun targetKeepsCategoryAndExactRow() {assertEquals("appearance",SettingsPolicy.section("appearance/dynamic"));assertEquals(1,SettingsPolicy.index("appearance/dynamic"));assertEquals(0,SettingsPolicy.index("appearance"))}
    @Test fun indexIsCompleteAndUnique() {assertEquals(8,SettingsPolicy.entries.map {it.section}.distinct().size);assertEquals(SettingsPolicy.entries.size,SettingsPolicy.entries.map {it.section+"/"+it.id}.distinct().size)}
    @Test fun toolbarKeepsTheSameTargetWithCompactTitle() {assertEquals("Профили",SettingsPolicy.toolbarTitle("profiles/test"));assertEquals("Настройки",SettingsPolicy.toolbarTitle(""))}
    @Test fun emptyQueryDoesNotProduceFakeOptions() {assertTrue(SettingsPolicy.search("  ").isEmpty())}
}
