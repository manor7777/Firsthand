package app.firsthand.data

import org.junit.Assert.*
import org.junit.Test

class BarOrderTest {
    @Test fun `a stored order is repaired`() {
        assertEquals(BarOrder.default, BarOrder.parse(null))
        assertEquals(listOf("mynews", "foryou", "breaking", "search"), BarOrder.parse("mynews,foryou"))
        assertEquals(listOf("search", "foryou", "breaking", "mynews"), BarOrder.parse("search, foryou ,search,bogus"))
    }

    @Test fun `breaking is hidden without BBC but keeps its place`() {
        val order = listOf("breaking", "foryou", "mynews", "search")
        assertEquals(listOf("foryou", "mynews", "search"), BarOrder.visible(order, bbcOn = false))
        assertEquals(order, BarOrder.visible(order, bbcOn = true))
        // Dragging while Breaking is hidden doesn't lose it.
        val merged = BarOrder.merge(listOf("search", "foryou", "mynews"), order)
        assertEquals(listOf("search", "foryou", "mynews", "breaking"), merged)
    }

    @Test fun `home is the first page, skipping search`() {
        assertEquals("foryou", BarOrder.home(listOf("foryou", "breaking", "mynews", "search")))
        assertEquals("mynews", BarOrder.home(listOf("search", "mynews", "foryou", "breaking")))
        assertEquals("breaking", BarOrder.home(listOf("breaking", "search", "foryou", "mynews")))
        assertEquals("foryou", BarOrder.home(listOf("search")))
    }

    @Test fun `encode round trips`() {
        val o = listOf("mynews", "search", "foryou", "breaking")
        assertEquals(o, BarOrder.parse(BarOrder.encode(o)))
    }
}
