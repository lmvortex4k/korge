package korlibs.korge.ui

import korlibs.datastructure.iterators.*
import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.io.async.*
import korlibs.korge.component.*
import korlibs.korge.input.*
import korlibs.korge.internal.*
import korlibs.korge.render.*
import korlibs.korge.view.*
import korlibs.korge.view.property.*
import korlibs.math.geom.*
import korlibs.math.interpolation.*
import korlibs.memory.*
import korlibs.time.*
import kotlin.math.*

inline fun Container.uiScrollable(
    size: Size = Size(256, 256),
    config: UIScrollable.() -> Unit = {},
    cache: Boolean = true,
    block: @ViewDslMarker Container.(UIScrollable) -> Unit = {},
): UIScrollable = UIScrollable(size, cache)
    .addTo(this).apply(config).also { block(it.container, it) }

open class UIScrollable(size: Size, cache: Boolean = true) : UIView(size, cache = cache) {
    @PublishedApi
    internal var overflowEnabled: Boolean = true

    class MyScrollbarInfo(val scrollable: UIScrollable, val direction: UIDirection, val view: SolidRect) {
        val isHorizontal get() = direction.isHorizontal
        val isVertical get() = direction.isVertical
        val container get() = scrollable.container

        var scrollBarPos: Float by if (isHorizontal) view::x else view::y
        var viewPos: Float by if (isHorizontal) view::x else view::y
            //get() = if (isHorizontal) view.x else view.y
            //set(value) = if (isHorizontal) view.x = value else view.y = value
        var viewScaledSize: Float by if (isHorizontal) view::scaledWidth else view::scaledHeight
            //get() = if (isHorizontal) view.scaledWidth else view.scaledHeight
            //set(value: Double) = if (isHorizontal) view.scaledWidth = value else view.scaledHeight = value

        val scrollRatio: Float get() = size / totalSize
        val scrollbarSize: Float get() = size * scrollRatio

        val scaledSize: Float get() = if (isHorizontal) view.scaledWidth  else view.scaledHeight
        var containerPos: Float by if (isHorizontal) container::x else container::y
            //get() = if (isHorizontal) container.x else container.y
            //set(value) { if (isHorizontal) container.x = value else container.y = value }

        val overflowPixelsBegin: Float get() = if (isHorizontal) scrollable.overflowPixelsLeft else scrollable.overflowPixelsTop
        val overflowPixelsEnd: Float get() = if (isHorizontal) scrollable.overflowPixelsRight else scrollable.overflowPixelsBottom
        val onScrollPosChange = Signal<UIScrollable>()
        val size: Float get() = if (isHorizontal) scrollable.width else scrollable.height
        val shouldBeVisible get() = (size < totalSize)
        val totalSize: Float get() = (container.getLocalBounds().let { if (isHorizontal) max(scrollable.width, it.right) else max(scrollable.height, it.bottom) })
            //.also { println("totalSize=$it") }
        val scrollArea: Float get() = totalSize - size
        val positionEnd: Float get() = position + size
        var position: Float
            get() = -containerPos
            set(value) {
                val oldValue = -containerPos
                val newValue = when {
                    scrollable.overflowEnabled -> -(value.clamp(-overflowPixelsBegin, scrollArea + overflowPixelsEnd))
                    else -> -(value.clamp(0f, scrollArea))
                }
                if (newValue != oldValue) {
                    containerPos = newValue
                    onScrollPosChange(scrollable)
                }
            }

        @KorgeInternal fun scrollBarPositionToScrollTopLeft(pos: Float): Float {
            val d = size - scaledSize
            if (d == 0f) return 0f
            return (pos / d) * scrollArea
        }
        @KorgeInternal fun scrollTopLeftToScrollBarPosition(pos: Float): Float {
            val d = scrollArea
            if (d == 0f) return 0f
            return (pos / d) * (size - scaledSize)
        }

        fun ensurePositionIsVisible(position: Float, anchor: Float = 0.5f) {
            ensureRangeIsVisible(position, position, anchor)
        }
        fun ensureRangeIsVisible(start: Float, end: Float, anchor: Float = 0.5f) {
            if (start !in this.position..this.positionEnd || end !in this.position..this.positionEnd) {
                this.position = (start - size * anchor).clamp(0f, scrollArea)
            }
        }

        var positionRatio: Float
            get() = position / scrollArea
            set(value) {
                position = scrollArea * value
            }

        var pixelSpeed: Float = 0f

        var startScrollPos: Float = 0f
    }

    //private val background = solidRect(width, height, Colors["#161a1d"])
    private val contentContainer = fixedSizeContainer(size, clip = true)
    val container = contentContainer.container(cull = true)
    //private val verticalScrollBar = solidRect(10.0, height / 2, Colors["#57577a"])
    //private val horizontalScrollBar = solidRect(width / 2, 10.0, Colors["#57577a"])

    private val vertical = MyScrollbarInfo(this, UIDirection.VERTICAL, solidRect(Size(10f, size.height / 2), Colors["#57577a"]))
    private val horizontal = MyScrollbarInfo(this, UIDirection.HORIZONTAL, solidRect(Size(size.width / 2, 10f), Colors["#57577a"]))
    private val infos = arrayOf(horizontal, vertical)

    private val totalHeight: Float get() = vertical.totalSize
    private val totalWidth: Float get() = horizontal.totalSize

    // HORIZONTAL SCROLLBAR
    val onScrollLeftChange: Signal<UIScrollable> get() = horizontal.onScrollPosChange
    val scrollWidth: Float get() = horizontal.totalSize
    var scrollLeft: Float by horizontal::position
    var scrollLeftRatio: Float by horizontal::positionRatio


    // VERTICAL SCROLLBAR
    val onScrollTopChange: Signal<UIScrollable> get() = vertical.onScrollPosChange
    val scrollHeight: Float get() = vertical.totalSize
    var scrollTop: Float by vertical::position
    var scrollTopRatio: Float by vertical::positionRatio

    @ViewProperty
    var frictionRate = 0.75f
    @ViewProperty
    var overflowRate = 0.1f
    val overflowPixelsVertical: Float get() = height * overflowRate
    val overflowPixelsHorizontal: Float get() = width * overflowRate
    val overflowPixelsTop: Float get() = overflowPixelsVertical
    val overflowPixelsBottom: Float get() = overflowPixelsVertical
    val overflowPixelsLeft: Float get() = overflowPixelsHorizontal
    val overflowPixelsRight: Float get() = overflowPixelsHorizontal
    @ViewProperty
    var containerX: Float by container::x
    @ViewProperty
    var containerY: Float by container::y
    @ViewProperty
    var timeScrollBar: TimeSpan = 0.seconds
    @ViewProperty
    var autohideScrollBar = false
    @ViewProperty
    var scrollBarAlpha = 0.75f
    @ViewProperty
    var backgroundColor: RGBA = Colors["#161a1d"]
    @ViewProperty
    var mobileBehaviour = true

    private fun showScrollBar() {
        horizontal.view.alphaF = scrollBarAlpha
        vertical.view.alphaF = scrollBarAlpha
        timeScrollBar = 0.seconds
    }

    override fun renderInternal(ctx: RenderContext) {
        if (backgroundColor != Colors.TRANSPARENT) {
            ctx.useBatcher { batch ->
                batch.drawQuad(ctx.getTex(Bitmaps.white), 0f, 0f, widthD.toFloat(), heightD.toFloat(), globalMatrix, colorMul = backgroundColor * renderColorMul)
            }
        }
        super.renderInternal(ctx)
    }

    fun ensurePointIsVisible(x: Float, y: Float, anchor: Anchor = Anchor.CENTER) {
        horizontal.ensurePositionIsVisible(x, anchor.floatX)
        vertical.ensurePositionIsVisible(y, anchor.floatY)
    }

    fun ensureRectIsVisible(rect: Rectangle, anchor: Anchor = Anchor.CENTER) {
        horizontal.ensureRangeIsVisible(rect.left, rect.right, anchor.floatX)
        vertical.ensureRangeIsVisible(rect.top, rect.bottom, anchor.floatY)
    }

    fun ensureViewIsVisible(view: View, anchor: Anchor = Anchor.CENTER) {
        ensureRectIsVisible(view.getBounds(this), anchor)
        scrollParentsToMakeVisible()
    }

    init {
        container.yD = 0.0
        showScrollBar()
        //onScrollTopChange.add { println(it.scrollRatio) }
        onSizeChanged()
        mouse {
            scroll {
                overflowEnabled = false
                showScrollBar()
                val axisY = when {
                    !horizontal.shouldBeVisible -> vertical
                    !vertical.shouldBeVisible -> horizontal
                    it.isAltDown -> horizontal
                    else -> vertical
                }
                //val axisX = if (axisY == vertical) horizontal else vertical
                val axisX = if (it.isAltDown) vertical else horizontal

                //println(it.lastEvent.scrollDeltaMode)
                //val infoAlt = if (it.isAltDown) vertical else horizontal
                if (axisX.shouldBeVisible) {
                    axisX.position = (axisX.position + it.scrollDeltaXPixels * (axisY.size / 16f))
                    //infoAlt.position = (info.position + it.scrollDeltaX * (info.size / 16.0))
                    if (it.scrollDeltaXPixels != 0f) axisX.pixelSpeed = 0f
                }
                if (axisY.shouldBeVisible) {
                    axisY.position = (axisY.position + it.scrollDeltaYPixels * (axisY.size / 16f))
                    //infoAlt.position = (info.position + it.scrollDeltaX * (info.size / 16.0))
                    if (it.scrollDeltaYPixels != 0f) axisY.pixelSpeed = 0f
                    //if (it.scrollDeltaX != 0.0) infoAlt.pixelSpeed = 0.0
                }
                it.stopPropagation()
                invalidateRender()
            }
        }

        var dragging = false

        for (info in infos) {
            info.view.decorateOutOverAlpha { if (it) 1.0f else scrollBarAlpha }
        }

        for (info in infos) {
            var startScrollBarPos = 0f
            info.view.onMouseDrag {
                if (!info.shouldBeVisible) return@onMouseDrag
                val dxy = if (info.isHorizontal) it.localDX else it.localDY
                if (it.start) {
                    startScrollBarPos = info.scrollBarPos
                }
                info.position =
                    info.scrollBarPositionToScrollTopLeft(startScrollBarPos + dxy).clamp(0f, info.scrollArea)
            }
        }

        contentContainer.onMouseDrag {
            overflowEnabled = true
            //println("DRAG: $it")
            if (it.start) {
                showScrollBar()
                dragging = true
                for (info in infos) {
                    if (!info.shouldBeVisible || !mobileBehaviour) continue
                    info.startScrollPos = info.position
                    info.pixelSpeed = 0f
                }
            }

            for (info in infos) {
                if (!info.shouldBeVisible || !mobileBehaviour) continue
                if (info.pixelSpeed.absoluteValue < 0.0001f) {
                    info.pixelSpeed = 0f
                }
            }

            for (info in infos) {
                if (!info.shouldBeVisible || !mobileBehaviour) continue
                val localDXY = if (info.isHorizontal) it.localDX else it.localDY
                info.position = info.startScrollPos - localDXY
                if (it.end) {
                    dragging = false
                    info.pixelSpeed = 300f
                    val elapsedTime = it.elapsed
                    info.pixelSpeed = -(localDXY * 1.1f) / elapsedTime.seconds.toFloat()
                }
            }
        }
        addUpdater {
            if (it.milliseconds == 0.0) return@addUpdater
            //println("horizontal.scrollbarSize=${horizontal.scrollBarPos},${horizontal.scrollbarSize}(${horizontal.view.visible},${horizontal.view.alpha}), vertical.scrollbarSize=${vertical.scrollbarSize}")
            infos.fastForEach { info ->
                info.view.visible = info.shouldBeVisible

                info.viewScaledSize = max(info.scrollbarSize, 10f)
                info.viewPos = info.scrollTopLeftToScrollBarPosition(info.position)
                //verticalScrollBar.y = scrollTop
                if (info.pixelSpeed.absoluteValue <= 1f) {
                    info.pixelSpeed = 0f
                }
                if (info.pixelSpeed != 0f) {
                    val oldScrollPos = info.position
                    info.position += info.pixelSpeed * it.seconds.toFloat()
                    if (oldScrollPos == info.position) {
                        info.pixelSpeed = 0f
                    }
                } else {
                    //scrollTop = round(scrollTop)

                    if (!dragging && (info.position < 0f || info.position > info.scrollArea)) {
                        //println("scrollRatio=$scrollRatio, scrollTop=$scrollTop")
                        val destScrollPos = if (info.position < 0f) 0f else info.scrollArea
                        if ((destScrollPos - info.position).absoluteValue < 0.1f) {
                            info.position = destScrollPos
                        } else {
                            info.position =
                                (0.5f * (it.seconds * 10f)).toRatio().interpolate(info.position, destScrollPos)
                        }
                    }

                    if (!dragging && autohideScrollBar) {
                        if (timeScrollBar >= 1.seconds) {
                            info.view.alphaF *= 0.9f
                        } else {
                            timeScrollBar += it
                        }
                    }
                }
            }
        }
        addFixedUpdater(0.1.seconds) {
            //pixelSpeed *= 0.95
            //pixelSpeed *= 0.75
            infos.fastForEach { it.pixelSpeed *= frictionRate }
        }
    }

    override fun onSizeChanged() {
        super.onSizeChanged()
        contentContainer.size(this.widthD, this.heightD)
        vertical.view.position(widthD - 10.0, 0.0)
        horizontal.view.position(0.0, heightD - 10.0)
        //println(vertical.overflowPixelsEnd)
        //background.size(width, height)
        invalidateRender()
    }
}
