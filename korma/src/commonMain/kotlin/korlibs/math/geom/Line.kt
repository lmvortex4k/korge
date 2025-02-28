package korlibs.math.geom

import korlibs.math.almostEquals
import korlibs.memory.clamp
import korlibs.memory.pack.*
import korlibs.math.annotations.*
import korlibs.math.geom.bezier.*
import korlibs.math.geom.shape.*
import korlibs.math.geom.vector.*
import korlibs.math.isAlmostZero
import kotlin.math.*

//@KormaValueApi
data class Line(val a: Vector2, val b: Vector2) : Shape2D {
    override val area: Float get() = 0f
    override val perimeter: Float get() = length

    override fun normalVectorAt(p: Point): Vector2 {
        val projected = projectedPoint(p)
        return (b - a).toNormal().normalized * Point.crossProduct(projected, p).sign
    }

    override val center: Point get() = (a + b) * 0.5f
    fun toRay(): Ray = Ray(a, (b - a).normalized)

    val xmin: Float get() = kotlin.math.min(x0, x1)
    val xmax: Float get() = kotlin.math.max(x0, x1)
    val ymin: Float get() = kotlin.math.min(y0, y1)
    val ymax: Float get() = kotlin.math.max(y0, y1)

    override fun projectedPoint(p: Point): Point {
        return projectedPointOutsideSegment(p).clamp(Point(xmin, ymin), Point(xmax, ymax))
    }

    fun projectedPointOutsideSegment(p: Point): Point {
        val v1x = x0
        val v2x = x1
        val v1y = y0
        val v2y = y1
        val px = p.x
        val py = p.y

        // return this.getIntersectionPoint(Line(point, Point.fromPolar(point, this.angle + 90.degrees)))!!
        // get dot product of e1, e2
        val e1x = v2x - v1x
        val e1y = v2y - v1y
        val e2x = px - v1x
        val e2y = py - v1y
        val valDp = Point.dot(e1x, e1y, e2x, e2y)
        // get length of vectors

        val lenLineE1 = kotlin.math.hypot(e1x, e1y)
        val lenLineE2 = kotlin.math.hypot(e2x, e2y)

        // What happens if lenLineE1 or lenLineE2 are zero?, it would be a division by zero.
        // Does that mean that the point is on the line, and we should use it?
        if (lenLineE1 == 0f || lenLineE2 == 0f) {
            return Point(px, py)
        }

        val cos = valDp / (lenLineE1 * lenLineE2)

        // length of v1P'
        val projLenOfLine = cos * lenLineE2

        return Point((v1x + (projLenOfLine * e1x) / lenLineE1), (v1y + (projLenOfLine * e1y) / lenLineE1))
    }

    override fun toVectorPath(): VectorPath = buildVectorPath { moveTo(a); lineTo(b) }
    override fun containsPoint(p: Point): Boolean = false

    constructor() : this(Point(), Point())
    constructor(x0: Double, y0: Double, x1: Double, y1: Double) : this(Point(x0, y0), Point(x1, y1))
    constructor(x0: Float, y0: Float, x1: Float, y1: Float) : this(Point(x0, y0), Point(x1, y1))
    constructor(x0: Int, y0: Int, x1: Int, y1: Int) : this(Point(x0, y0), Point(x1, y1))

    inline fun flipped(): Line = Line(b, a)
    fun toBezier(): Bezier = Bezier(a, b)

    val x0: Float get() = a.x
    val y0: Float get() = a.y

    val x1: Float get() = b.x
    val y1: Float get() = b.y

    val dx: Float get() = x1 - x0
    val dy: Float get() = y1 - y0

    val min: Point get() = Point(minX, minY)
    val minX: Float get() = kotlin.math.min(a.x, b.x)
    val minY: Float get() = kotlin.math.min(a.y, b.y)

    val max: Point get() = Point(maxX, maxY)
    val maxX: Float get() = kotlin.math.max(a.x, b.x)
    val maxY: Float get() = kotlin.math.max(a.y, b.y)

    fun round(): Line = Line(a.round(), b.round())
    fun directionVector(): Point = Point(dx, dy)

    fun getMinimumDistance(p: Point): Float {
        val v = a
        val w = b
        val l2 = Point.distanceSquared(v, w)
        if (l2 == 0f) return Point.distanceSquared(p, a)
        val t = (Point.dot(p - v, w - v) / l2).clamp(0f, 1f)
        return Point.distance(p, v + (w - v) * t)
    }

    @KormaExperimental
    fun scaledPoints(scale: Double): Line {
        val dx = this.dx
        val dy = this.dy
        return Line(x0 - dx * scale, y0 - dy * scale, x1 + dx * scale, y1 + dy * scale)
    }

    fun containsX(x: Float): Boolean = (x in x0..x1) || (x in x1..x0) || (almostEquals(x, x0)) || (almostEquals(x, x1))
    fun containsY(y: Float): Boolean = (y in y0..y1) || (y in y1..y0) || (almostEquals(y, y0)) || (almostEquals(y, y1))
    fun containsBoundsXY(x: Float, y: Float): Boolean = containsX(x) && containsY(y)

    val angle: Angle get() = Angle.between(a, b)
    val length: Float get() = Point.distance(a, b)
    val lengthSquared: Float get() = Point.distanceSquared(a, b)

    fun getLineIntersectionPoint(line: Line): Point? =
        getIntersectXY(x0, y0, x1, y1, line.x0, line.y0, line.x1, line.y1)

    fun getIntersectionPoint(line: Line): Point? = getSegmentIntersectionPoint(line)
    fun getSegmentIntersectionPoint(line: Line): Point? {
        val out = getIntersectXY(x0, y0, x1, y1, line.x0, line.y0, line.x1, line.y1)
        if (out != null && this.containsBoundsXY(out.x, out.y) && line.containsBoundsXY(out.x, out.y)) return out
        return null
    }

    fun intersectsLine(line: Line): Boolean = getLineIntersectionPoint(line) != null
    fun intersects(line: Line): Boolean = intersectsSegment(line)
    fun intersectsSegment(line: Line): Boolean = getSegmentIntersectionPoint(line) != null

    override fun toString(): String = "Line($a, $b)"

    val isNIL get() = a.x.isNaN()
    fun isNaN(): Boolean = a.y.isNaN()

    companion object {
        val ZERO = Line(Point.ZERO, Point.ZERO)
        val NaN = Line(Point.NaN, Point.NaN)
        val NIL: Line get() = NaN

        fun fromPointAndDirection(point: Point, direction: Point, scale: Float = 1f): Line =
            Line(point, point + direction * scale)
        fun fromPointAngle(point: Point, angle: Angle, length: Float = 1f): Line =
            Line(point, Point.polar(angle, length))

        fun length(Ax: Double, Ay: Double, Bx: Double, By: Double): Double = kotlin.math.hypot(Bx - Ax, By - Ay)

        inline fun getIntersectXY(Ax: Float, Ay: Float, Bx: Float, By: Float, Cx: Float, Cy: Float, Dx: Float, Dy: Float): Point? {
            val a1 = By - Ay
            val b1 = Ax - Bx
            val c1 = a1 * (Ax) + b1 * (Ay)
            val a2 = Dy - Cy
            val b2 = Cx - Dx
            val c2 = a2 * (Cx) + b2 * (Cy)
            val determinant = a1 * b2 - a2 * b1
            if (determinant.isAlmostZero()) return null
            val x = (b2 * c1 - b1 * c2) / determinant
            val y = (a1 * c2 - a2 * c1) / determinant
            //if (!x.isFinite() || !y.isFinite()) TODO()
            return Point(x, y)
        }

        fun getIntersectXY(a: Point, b: Point, c: Point, d: Point): Point? =
            getIntersectXY(a.x, a.y, b.x, b.y, c.x, c.y, d.x, d.y)
    }

}
