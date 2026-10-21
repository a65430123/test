package com.twstock.ta.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockQueryTest {
    @Test
    fun normalize() {
        assertEquals("2330", StockQuery.normalize(" 2330 "))
        assertEquals("2330", StockQuery.normalize("２３３０")) // 全形數字
        assertEquals("00631L", StockQuery.normalize("００６３１ｌ")) // 全形＋小寫
        assertEquals("台積電", StockQuery.normalize("台積\u3000電")) // 全形空白
        assertEquals("", StockQuery.normalize("   "))
    }

    @Test
    fun looksLikeCode() {
        listOf("2330", "0050", "00878", "006208", "00631L", "00400A").forEach { assertTrue(it, StockQuery.looksLikeCode(it)) }
        listOf("233", "台積電", "2330AB", "1234567").forEach { assertFalse(it, StockQuery.looksLikeCode(it)) }
    }
}
