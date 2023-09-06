package com.esanwoo.eview

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.OverScroller
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnChildAttachStateChangeListener
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import kotlin.math.absoluteValue

class ERecyclerWife(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
    FrameLayout(context, attrs, defStyleAttr) {

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    private val refreshingView = LottieAnimationView(context)
    private val loadingView = LottieAnimationView(context)

//    private val touchSlop by lazy {
//        ViewConfiguration.get(context).scaledTouchSlop
//    }

    private val scroller by lazy { OverScroller(context) }
    private var downX = 0f
    private var downY = 0f
    private var refreshThreshold = 0    //触发刷新的滚动阈值
    private var refreshListener: RefreshListener? = null
    private var state = State.FINISH

    private var preload: Int     //-1表示不自动加载

    //    private val autoLoad:Boolean
    private val recyclerView: RecyclerView?
        get() = if (childCount > 2) getChildAt(2) as RecyclerView else null

    init {
        val typeArray = context.obtainStyledAttributes(attrs, R.styleable.ERecyclerWife)
        preload = typeArray.getInt(R.styleable.ERecyclerWife_autoPreloadBackwardsIndex, -1).let {
            if (it < -1) -1 else it
        }
        typeArray.recycle()

        refreshingView.let {
            addView(
                it,
                ViewGroup.LayoutParams(
                    300,
                    150
                )
            )
            it.setAnimation(R.raw.refresh_lottie)
            it.repeatCount = LottieDrawable.INFINITE
        }
        loadingView.let {
            addView(
                it,
                ViewGroup.LayoutParams(
                    300,
                    150
                )
            )
            it.setAnimation(R.raw.load_more_lottie)
            it.repeatCount = LottieDrawable.INFINITE
        }
    }

    enum class State {
        REFRESHING, FINISH, LOADING, ERROR
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        check(childCount <= 2) { "Only can host one direct child" }
        if (child is RecyclerView) {
            child.addOnChildAttachStateChangeListener(object : OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    if (preload == -1) return
                    if (state != State.LOADING && layoutManager?.getPosition(view) == recyclerView?.adapter?.itemCount?.minus(
                            preload
                        )
                    ) {
                        loadMore()
                    }
                }

                override fun onChildViewDetachedFromWindow(view: View) {
                }

            })
        }
        super.addView(child, index, params)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        check(getChildAt(2) is RecyclerView) { "Child can only be RecyclerView" }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
//        val myWidthSize = MeasureSpec.getSize(widthMeasureSpec)
//        val myHeightSize = MeasureSpec.getSize(heightMeasureSpec)
//        setMeasuredDimension(myWidthSize,myHeightSize)
        refreshThreshold = measuredHeight / 12
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        var view = getChildAt(0)
        var l = (measuredWidth - view.measuredWidth) / 2
        view.layout(l, -view.measuredHeight, l + view.measuredWidth, 0)

        view = getChildAt(1)
        view.layout(l, measuredHeight, l + view.measuredWidth, measuredHeight + view.measuredHeight)
    }

    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, scroller.currY)
        }
        invalidate()
    }

    val layoutManager by lazy { recyclerView?.layoutManager }
//    var lastItemVisible = false

//    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
//        if (ev?.action == MotionEvent.ACTION_MOVE){
//            val dy = ev.y - downY
//            if (dy < 0) {
//                isBottom = recyclerView?.canScrollVertically(1) == false
//                recyclerView?.addOnChildAttachStateChangeListener(object :
//                    OnChildAttachStateChangeListener {
//                    override fun onChildViewAttachedToWindow(view: View) {
//                        val position = layoutManager?.getPosition(view)
//                        if (position == recyclerView?.size && isBottom)
//                    }
//
//                    override fun onChildViewDetachedFromWindow(view: View) {
//                    }
//
//                })
//            }
//        }
//        return super.dispatchTouchEvent(ev)
//    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (dy < 0) {
                    if (preload == -1 && state != State.LOADING && recyclerView?.canScrollVertically(
                            1
                        ) == false
                    ) {
                        parent.requestDisallowInterceptTouchEvent(true)
                        lastY = ev.y
                        return true
                    }
                    return false
                } else {
                    if (dy.absoluteValue > dx.absoluteValue && recyclerView?.canScrollVertically(-1) == false) {
                        //如果recyclerView不能往下滚动（也就是往下拉）了 说明到顶了 再往下拉的事件就要由ERefresher拦截处理刷新逻辑了 所以返回true
                        parent.requestDisallowInterceptTouchEvent(true)     //这句代码为了防止下拉时手指横向移动过大导致触发viewpager2的翻页
                        lastY = ev.y
                        return true
                    }
                    return false
                }
            }
        }

        return super.onInterceptTouchEvent(ev)
    }

    private var lastY = 0f
    override fun onTouchEvent(ev: MotionEvent): Boolean {

        if (ev.action == MotionEvent.ACTION_MOVE) {
            val scrolly = (ev.y - downY) / 2
            if (scrolly > 0) {
                if (scrollY > 0) {
                    scrollY = 0
                } else if (scrollY == 0 && ev.y < lastY) {
                    downY = ev.y
                } else {
                    scrollY = -scrolly.toInt()
                    refreshingView.progress = scrolly / 3 / refreshThreshold % 1
                }
            } else {
                if (scrollY < 0) {
                    scrollY = 0
                } else if (scrollY == 0 && ev.y > lastY) {
                    downY = ev.y
                } else {
                    scrollY = -scrolly.toInt()
                    loadingView.progress = -scrolly / 3 / refreshThreshold % 1
                }
            }
            lastY = ev.y
        }
        if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
            if (scrollY.absoluteValue < refreshThreshold) scrollToInitial()     //下拉没到刷新阈值，就滚回原位
            else {  //否则就刷新
                scroller.startScroll(
                    0,
                    scrollY,
                    0,
                    -scrollY - (if (scrollY < 0) refreshingView.measuredHeight else -refreshingView.measuredHeight),
                    500
                ) //为了让刷新动画处于刷新绘制区域的中心，所以要回滚到距离顶部等于刷新动画高度的位置
                if (scrollY < 0) {
                    refreshingView.resumeAnimation()
                    state = State.REFRESHING
                    refreshListener?.onRefersh()
                } else {
                    loadingView.resumeAnimation()
                    loadMore()
                }
            }
        }
        return true
    }

    private fun scrollToInitial() {
        scroller.startScroll(0, scrollY, 0, -scrollY, 500)
        if (state == State.REFRESHING) refreshingView.cancelAnimation()
        if (state == State.LOADING) loadingView.cancelAnimation()
    }

    private fun loadMore() {
        state = State.LOADING
        refreshListener?.onLoadMore()
    }

    fun setRefreshListener(refreshListener: RefreshListener) {
        this.refreshListener = refreshListener
    }

    fun setAutoPreloadBackwardsIndex(backwardsIndex: Int) {
        preload = if (backwardsIndex < -1) -1 else backwardsIndex
    }

    fun refreshFinish() {
        if (state == State.REFRESHING) {
            scrollToInitial()
            state = State.FINISH
            Toast.makeText(context, "刷新完成", Toast.LENGTH_SHORT).show()
        }
    }

    fun loadFinish() {
        if (state == State.LOADING) {
            scrollToInitial()
            state = State.FINISH
        }
    }

    fun release() {
        refreshingView.cancelAnimation()
    }

    interface RefreshListener {
        fun onRefersh()
        fun onLoadMore()
    }
}