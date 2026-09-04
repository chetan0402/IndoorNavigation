package me.chetan.indoornavigation

import me.chetan.indoornavigation.data.GeoLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class PathFindTest {
    @Test
    fun `Route test`(){
        val point1=GeoLocation(0.0,0.0,0.0,"1")
        val point2=GeoLocation(10.0,0.0,0.0,"2")
        val point3=GeoLocation(0.0,10.0,0.0,"3")

        val finder = PathFind(mutableMapOf(
            point1 to mutableListOf(point2,point3),
            point2 to mutableListOf(point1),
            point3 to mutableListOf(point1)
        ))

        val myLoc=GeoLocation(2.0,1.0,0.0,"MyLoc")

        val route=finder.route(myLoc,point3)

        assertEquals(
            listOf(GeoLocation(2.0,0.0,0.0),point1,point3),
            route
        )
    }
    @Test
    fun `Trilaterate test`(){
        val point1=GeoLocation(0.0,0.0,0.0,"1")
        val point2=GeoLocation(10.0,0.0,0.0,"2")
        val point3= GeoLocation(0.0,10.0,10.0,"3")

        val finder = PathFind(mutableMapOf(
            point1 to mutableListOf(point2,point3),
            point2 to mutableListOf(point1),
            point3 to mutableListOf(point1)
        ))

        assertEquals(
            GeoLocation(5.0,5.0,5.0),
            finder.trilaterate(listOf(point1,point2,point3),listOf(5.0,5.0,5.0))
        )
    }
}